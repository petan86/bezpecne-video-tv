package cz.petr.bezpecnevideo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

final class AppUpdater {
    private static final String LATEST_RELEASE = "https://api.github.com/repos/petan86/bezpecne-video-tv/releases/latest";
    private final Activity activity;
    private long downloadId = -1;
    private BroadcastReceiver receiver;

    AppUpdater(Activity activity) {
        this.activity = activity;
    }

    void checkAndInstall() {
        Toast.makeText(activity, "Kontroluji aktualizace…", Toast.LENGTH_SHORT).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Release release = loadLatestRelease();
                activity.runOnUiThread(() -> showResult(release));
            } catch (Exception exception) {
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Aktualizaci se nepodařilo ověřit. Zkontrolujte internet.", Toast.LENGTH_LONG).show());
            }
        });
    }

    private Release loadLatestRelease() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "BezpecneVideo-TV");
        if (connection.getResponseCode() != 200) throw new IllegalStateException("GitHub " + connection.getResponseCode());
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
        }
        JSONObject root = new JSONObject(json.toString());
        String tag = root.getString("tag_name");
        JSONArray assets = root.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.getString("name");
            if (name.endsWith(".apk")) return new Release(tag, asset.getString("browser_download_url"), name);
        }
        throw new IllegalStateException("Release neobsahuje APK");
    }

    private void showResult(Release release) {
        String current = currentVersion();
        String latest = release.tag.replaceFirst("^v", "");
        if (compareVersions(latest, current) <= 0) {
            new AlertDialog.Builder(activity)
                    .setTitle("Aplikace je aktuální")
                    .setMessage("Používáte nejnovější verzi " + current + ".")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Je dostupná verze " + latest)
                .setMessage("Chcete ji stáhnout a nainstalovat?")
                .setPositiveButton("Aktualizovat", (dialog, which) -> prepareDownload(release))
                .setNegativeButton("Teď ne", null)
                .show();
    }

    private void prepareDownload(Release release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Povolit aktualizace")
                    .setMessage("Google TV nyní otevře nastavení. Povolte instalaci aplikací pro Bezpečné video a potom stiskněte tlačítko aktualizace znovu.")
                    .setPositiveButton("Otevřít nastavení", (dialog, which) -> {
                        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(settings);
                    })
                    .setNegativeButton("Zrušit", null)
                    .show();
            return;
        }
        startDownload(release);
    }

    private void startDownload(Release release) {
        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.url));
        request.setTitle("Bezpečné video " + release.tag);
        request.setDescription("Stahuji aktualizaci…");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setMimeType("application/vnd.android.package-archive");
        request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, release.fileName);
        downloadId = manager.enqueue(request);
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                long completed = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (completed != downloadId) return;
                try { activity.unregisterReceiver(this); } catch (Exception ignored) { }
                Uri apk = manager.getUriForDownloadedFile(downloadId);
                if (apk == null) {
                    Toast.makeText(activity, "Stažení aktualizace se nezdařilo.", Toast.LENGTH_LONG).show();
                    return;
                }
                Intent install = new Intent(Intent.ACTION_VIEW);
                install.setDataAndType(apk, "application/vnd.android.package-archive");
                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(install);
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(receiver, filter);
        Toast.makeText(activity, "Aktualizace se stahuje…", Toast.LENGTH_LONG).show();
    }

    private String currentVersion() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "0" : info.versionName;
        } catch (Exception ignored) { return "0"; }
    }

    private int compareVersions(String first, String second) {
        String[] a = first.split("\\.");
        String[] b = second.split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? number(a[i]) : 0;
            int bv = i < b.length ? number(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private int number(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9].*$", "")); }
        catch (Exception ignored) { return 0; }
    }

    private static final class Release {
        final String tag;
        final String url;
        final String fileName;
        Release(String tag, String url, String fileName) {
            this.tag = tag; this.url = url; this.fileName = fileName;
        }
    }
}

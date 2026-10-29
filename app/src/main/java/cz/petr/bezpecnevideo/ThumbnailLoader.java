package cz.petr.bezpecnevideo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.util.LruCache;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class ThumbnailLoader {
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(3, 3, 0,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object DISK_LOCK = new Object();
    private static final long DISK_LIMIT = 32L * 1024 * 1024;
    private static final long MAX_AGE = TimeUnit.DAYS.toMillis(7);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    static void clear(ImageView view) {
        Object tag = view.getTag();
        if (tag instanceof Request) {
            Future<?> future = ((Request) tag).future;
            if (future != null) {
                future.cancel(true);
                if (future instanceof Runnable) EXECUTOR.remove((Runnable) future);
            }
        }
        view.setTag(null);
        view.setImageDrawable(null);
    }

    static void load(ImageView view, String id) {
        clear(view);
        if (!id.matches("[A-Za-z0-9_-]{11}")) return;
        Bitmap cached = CACHE.get(id);
        if (cached != null) { view.setImageBitmap(cached); return; }
        Request request = new Request();
        view.setTag(request);
        WeakReference<ImageView> target = new WeakReference<>(view);
        File directory = new File(view.getContext().getCacheDir(), "video_thumbnails");
        request.future = EXECUTOR.submit(() -> {
            if (Thread.currentThread().isInterrupted()) return;
            Bitmap bitmap = read(directory, id);
            if (bitmap != null) CACHE.put(id, bitmap);
            MAIN.post(() -> {
                ImageView current = target.get();
                if (current != null && current.getTag() == request && bitmap != null) current.setImageBitmap(bitmap);
            });
        });
    }

    private static Bitmap read(File directory, String id) {
        Bitmap memory = CACHE.get(id);
        if (memory != null) return memory;
        File file = new File(directory, id + ".jpg");
        synchronized (DISK_LOCK) {
            if (file.isFile() && System.currentTimeMillis() - file.lastModified() < MAX_AGE) {
                Bitmap cached = BitmapFactory.decodeFile(file.getPath());
                if (cached != null) return cached;
            }
        }
        if (Thread.currentThread().isInterrupted()) return null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://i.ytimg.com/vi/" + id + "/mqdefault.jpg").openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            Bitmap bitmap;
            try (InputStream stream = connection.getInputStream()) { bitmap = BitmapFactory.decodeStream(stream); }
            if (bitmap != null && !Thread.currentThread().isInterrupted()) {
                try { synchronized (DISK_LOCK) {
                    if (directory.isDirectory() || directory.mkdirs()) {
                        File temp = File.createTempFile("thumb-", ".tmp", directory);
                        try {
                            try (FileOutputStream output = new FileOutputStream(temp)) {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output);
                            }
                            if (file.exists()) file.delete();
                            temp.renameTo(file);
                        } finally { temp.delete(); }
                        trim(directory);
                    }
                } } catch (java.io.IOException ignored) { /* A full cache must not hide a downloaded thumbnail. */ }
            }
            return bitmap;
        } catch (Exception ignored) { return null; }
        finally { if (connection != null) connection.disconnect(); }
    }

    private static void trim(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long size = 0;
        for (File file : files) size += file.length();
        for (File file : files) {
            if (size <= DISK_LIMIT && System.currentTimeMillis() - file.lastModified() < MAX_AGE) continue;
            long length = file.length();
            if (file.delete()) size -= length;
        }
    }
    private static final class Request { Future<?> future; }
}

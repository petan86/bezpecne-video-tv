package cz.petr.bezpecnevideo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;
import android.util.LruCache;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

@SuppressWarnings("deprecation")
final class ThumbnailLoader extends AsyncTask<String, Void, Bitmap> {
    static final java.util.concurrent.Executor EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(4);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    private final WeakReference<ImageView> target;
    ThumbnailLoader(ImageView target) { this.target = new WeakReference<>(target); }

    @Override protected Bitmap doInBackground(String... values) {
        if (target.get() == null) return null;
        Bitmap cached = CACHE.get(values[0]);
        if (cached != null) return cached;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(values[0]).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            try (InputStream stream = connection.getInputStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                if (bitmap != null) CACHE.put(values[0], bitmap);
                return bitmap;
            }
        } catch (Exception ignored) { return null; }
        finally { if (connection != null) connection.disconnect(); }
    }

    @Override protected void onPostExecute(Bitmap bitmap) {
        ImageView view = target.get();
        if (view != null && bitmap != null) view.setImageBitmap(bitmap);
    }
}

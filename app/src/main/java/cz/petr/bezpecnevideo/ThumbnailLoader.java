package cz.petr.bezpecnevideo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

@SuppressWarnings("deprecation")
final class ThumbnailLoader extends AsyncTask<String, Void, Bitmap> {
    private final WeakReference<ImageView> target;
    ThumbnailLoader(ImageView target) { this.target = new WeakReference<>(target); }

    @Override protected Bitmap doInBackground(String... values) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(values[0]).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            try (InputStream stream = connection.getInputStream()) { return BitmapFactory.decodeStream(stream); }
        } catch (Exception ignored) { return null; }
    }

    @Override protected void onPostExecute(Bitmap bitmap) {
        ImageView view = target.get();
        if (view != null && bitmap != null) view.setImageBitmap(bitmap);
    }
}

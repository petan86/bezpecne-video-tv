package cz.petr.bezpecnevideo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.net.Inet4Address;
import java.net.InetAddress;

final class PhonePairing {
    static InetAddress address(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return null;
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null || !(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue;
            LinkProperties properties = manager.getLinkProperties(network);
            if (properties == null) continue;
            for (LinkAddress link : properties.getLinkAddresses()) {
                InetAddress address = link.getAddress();
                if (address instanceof Inet4Address && address.isSiteLocalAddress()) return address;
            }
        }
        return null;
    }

    static Bitmap qr(String link) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 512, 512);
        int[] pixels = new int[512 * 512];
        for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++)
            pixels[y * 512 + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
        return Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888);
    }
}

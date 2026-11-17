package org.jebol.adapter.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;

import org.jebol.domain.host.ImagePort;
import org.jebol.domain.render.DrawingCommand;

import java.util.List;

public final class AndroidPixelSurface {

    private static final int CHANNELS = 4;

    private AndroidPixelSurface() {
    }

    public static ImagePort.Pixels paintOnWhite(List<DrawingCommand> calls, int wide, int high, Typeface face) {
        if (wide <= 0 || high <= 0) {
            return new ImagePort.Pixels(Math.max(0, wide), Math.max(0, high), new byte[0]);
        }
        Bitmap picture = Bitmap.createBitmap(wide, high, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(picture);
        canvas.drawColor(Color.WHITE);
        AndroidSurface surface = new AndroidSurface(canvas, face);
        calls.forEach(surface::execute);
        int[] argb = new int[wide * high];
        picture.getPixels(argb, 0, wide, 0, 0, wide, high);
        picture.recycle();
        return new ImagePort.Pixels(wide, high, convertToRgba(argb));
    }

    private static byte[] convertToRgba(int[] argb) {
        byte[] rgba = new byte[argb.length * CHANNELS];
        for (int at = 0; at < argb.length; at++) {
            int pixel = argb[at];
            rgba[at * CHANNELS] = (byte) (pixel >>> 16);
            rgba[at * CHANNELS + 1] = (byte) (pixel >>> 8);
            rgba[at * CHANNELS + 2] = (byte) pixel;
            rgba[at * CHANNELS + 3] = (byte) (pixel >>> 24);
        }
        return rgba;
    }
}

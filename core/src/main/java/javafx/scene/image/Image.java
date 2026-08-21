package javafx.scene.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Android 兼容实现：用 Bitmap 承载图像数据。
 */
public class Image {

    private final Bitmap bitmap;
    private Exception exception;

    public Image(String url) {
        this.bitmap = null;
    }

    public Image(InputStream is) throws IOException {
        this(is, 0, 0, false, false);
    }

    public Image(InputStream is, int requestedWidth, int requestedHeight,
                 boolean preserveRatio, boolean smooth) throws IOException {
        Bitmap bmp;
        try {
            bmp = BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            this.exception = e;
            bmp = null;
        }
        if (bmp == null) {
            this.exception = new IOException("Failed to decode image");
            this.bitmap = null;
            return;
        }
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (requestedWidth > 0 && requestedHeight > 0) {
            if (preserveRatio) {
                double rw = (double) requestedWidth / w;
                double rh = (double) requestedHeight / h;
                double scale = Math.min(rw, rh);
                int nw = Math.max(1, (int) (w * scale));
                int nh = Math.max(1, (int) (h * scale));
                this.bitmap = Bitmap.createScaledBitmap(bmp, nw, nh, smooth);
            } else {
                this.bitmap = Bitmap.createScaledBitmap(bmp, requestedWidth, requestedHeight, smooth);
            }
        } else {
            this.bitmap = bmp;
        }
    }

    public Image(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public float getWidth() {
        return bitmap != null ? bitmap.getWidth() : 0;
    }

    public float getHeight() {
        return bitmap != null ? bitmap.getHeight() : 0;
    }

    public PixelReader getPixelReader() {
        return new PixelReader(this);
    }

    public boolean isError() {
        return exception != null || bitmap == null;
    }

    public Exception getException() {
        return exception;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }
}

package javafx.scene.image;

import android.graphics.Bitmap;

public class WritableImage extends Image {

    public WritableImage(int width, int height) {
        super(Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888));
    }

    public WritableImage(PixelWriter pixelWriter, int width, int height) {
        this(width, height);
    }

    public PixelWriter getPixelWriter() {
        return new PixelWriter(this);
    }
}

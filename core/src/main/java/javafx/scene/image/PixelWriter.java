package javafx.scene.image;

public class PixelWriter {

    private final WritableImage image;

    public PixelWriter(WritableImage image) {
        this.image = image;
    }

    public void setArgb(int x, int y, int argb) {
        if (image.getBitmap() != null) {
            image.getBitmap().setPixel(x, y, argb);
        }
    }
}

package javafx.scene.image;

/**
 * 兼容实现：通过像素数组读取图像数据。
 */
public class PixelReader {

    private final Image image;

    public PixelReader(Image image) {
        this.image = image;
    }

    public int getArgb(int x, int y) {
        if (image.getBitmap() == null) return 0;
        return image.getBitmap().getPixel(x, y);
    }
}

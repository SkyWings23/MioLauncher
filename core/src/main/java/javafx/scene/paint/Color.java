package javafx.scene.paint;

public class Color extends Paint {

    private final double red;
    private final double green;
    private final double blue;
    private final double opacity;

    public Color(double red, double green, double blue, double opacity) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.opacity = opacity;
    }

    public double getRed() {
        return red;
    }

    public double getGreen() {
        return green;
    }

    public double getBlue() {
        return blue;
    }

    public double getOpacity() {
        return opacity;
    }

    @Override
    public String toString() {
        int r = (int) Math.round(red * 255);
        int g = (int) Math.round(green * 255);
        int b = (int) Math.round(blue * 255);
        if (opacity >= 1.0) {
            return String.format("#%02x%02x%02x", r, g, b);
        }
        return String.format("#%02x%02x%02x%02x", r, g, b, (int) Math.round(opacity * 255));
    }
}

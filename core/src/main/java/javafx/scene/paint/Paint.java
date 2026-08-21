package javafx.scene.paint;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Paint {

    private static final Pattern HEX = Pattern.compile("#([0-9a-fA-F]{6})([0-9a-fA-F]{2})?");
    private static final Pattern RGB = Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,\\s*([0-9.]+)\\s*)?\\)");

    public static Paint valueOf(String value) {
        if (value == null) return null;
        Matcher hex = HEX.matcher(value.trim());
        if (hex.matches()) {
            int r = Integer.parseInt(hex.group(1).substring(0, 2), 16);
            int g = Integer.parseInt(hex.group(1).substring(2, 4), 16);
            int b = Integer.parseInt(hex.group(1).substring(4, 6), 16);
            double opacity = 1.0;
            if (hex.group(2) != null) {
                opacity = Integer.parseInt(hex.group(2), 16) / 255.0;
            }
            return new Color(r / 255.0, g / 255.0, b / 255.0, opacity);
        }
        Matcher rgb = RGB.matcher(value.trim());
        if (rgb.matches()) {
            double r = Double.parseDouble(rgb.group(1)) / 255.0;
            double g = Double.parseDouble(rgb.group(2)) / 255.0;
            double b = Double.parseDouble(rgb.group(3)) / 255.0;
            double opacity = rgb.group(4) != null ? Double.parseDouble(rgb.group(4)) : 1.0;
            return new Color(r, g, b, opacity);
        }
        return new Color(0, 0, 0, 1.0);
    }
}

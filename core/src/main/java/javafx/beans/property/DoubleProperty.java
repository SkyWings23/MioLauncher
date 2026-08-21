package javafx.beans.property;

public abstract class DoubleProperty extends ReadOnlyDoubleProperty {

    public abstract void set(double value);

    public void setValue(Number v) {
        if (v == null) {
            set(0.0);
        } else {
            set(v.doubleValue());
        }
    }
}

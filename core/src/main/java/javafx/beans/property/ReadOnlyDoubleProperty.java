package javafx.beans.property;

/**
 * 只读 Double 属性：DoubleProperty 继承它。
 */
public abstract class ReadOnlyDoubleProperty implements Property<Number> {

    public abstract double get();

    @Override
    public Number getValue() {
        return get();
    }
}

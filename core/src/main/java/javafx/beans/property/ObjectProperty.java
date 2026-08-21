package javafx.beans.property;

public abstract class ObjectProperty<T> implements Property<T> {

    @Override
    public void setValue(T v) {
        set(v);
    }

    public abstract T get();

    public abstract void set(T value);

    @Override
    public T getValue() {
        return get();
    }
}

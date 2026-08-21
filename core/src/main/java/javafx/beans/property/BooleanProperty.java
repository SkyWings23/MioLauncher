package javafx.beans.property;

public abstract class BooleanProperty implements Property<Boolean> {

    @Override
    public void setValue(Boolean v) {
        if (v == null) v = false;
        set(v);
    }

    public abstract void set(boolean value);

    public abstract boolean get();

    @Override
    public Boolean getValue() {
        return get();
    }
}

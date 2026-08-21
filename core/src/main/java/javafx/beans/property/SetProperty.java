package javafx.beans.property;

import javafx.collections.ObservableSet;

public abstract class SetProperty<E> implements Property<ObservableSet<E>> {

    @Override
    public void setValue(ObservableSet<E> v) {
        set(v);
    }

    public abstract void set(ObservableSet<E> value);

    public abstract ObservableSet<E> get();

    @Override
    public ObservableSet<E> getValue() {
        return get();
    }
}

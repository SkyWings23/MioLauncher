package javafx.beans.property;

import javafx.collections.ObservableList;

public abstract class ListProperty<E> implements Property<ObservableList<E>> {

    @Override
    public void setValue(ObservableList<E> v) {
        set(v);
    }

    public abstract void set(ObservableList<E> value);

    public abstract ObservableList<E> get();

    @Override
    public ObservableList<E> getValue() {
        return get();
    }
}

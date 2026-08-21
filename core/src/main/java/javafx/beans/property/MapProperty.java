package javafx.beans.property;

import javafx.collections.ObservableMap;

public abstract class MapProperty<K, V> implements Property<ObservableMap<K, V>> {

    @Override
    public void setValue(ObservableMap<K, V> v) {
        set(v);
    }

    public abstract void set(ObservableMap<K, V> value);

    public abstract ObservableMap<K, V> get();

    @Override
    public ObservableMap<K, V> getValue() {
        return get();
    }
}

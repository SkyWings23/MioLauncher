package javafx.collections;

import javafx.beans.InvalidationListener;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class ObservableMapWrapper<K, V> implements ObservableMap<K, V> {

    private final Map<K, V> backingMap;
    private final CopyOnWriteArrayList<InvalidationListener> listeners = new CopyOnWriteArrayList<>();

    public ObservableMapWrapper(Map<K, V> backingMap) {
        this.backingMap = backingMap;
    }

    private void fire() {
        for (InvalidationListener l : listeners) l.invalidated(this);
    }

    @Override
    public void addListener(InvalidationListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        listeners.remove(listener);
    }

    @Override
    public int size() {
        return backingMap.size();
    }

    @Override
    public boolean isEmpty() {
        return backingMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return backingMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return backingMap.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return backingMap.get(key);
    }

    @Override
    public V put(K key, V value) {
        V r = backingMap.put(key, value);
        fire();
        return r;
    }

    @Override
    public V remove(Object key) {
        V r = backingMap.remove(key);
        if (r != null) fire();
        return r;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        backingMap.putAll(m);
        fire();
    }

    @Override
    public void clear() {
        if (!backingMap.isEmpty()) {
            backingMap.clear();
            fire();
        }
    }

    @Override
    public Set<K> keySet() {
        return backingMap.keySet();
    }

    @Override
    public Collection<V> values() {
        return backingMap.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return backingMap.entrySet();
    }
}

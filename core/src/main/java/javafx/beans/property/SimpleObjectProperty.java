package javafx.beans.property;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 一个通用可观察属性实现，绑定到另一个 ObservableValue。
 */
public class SimpleObjectProperty<T> implements Property<T> {

    private final Object bean;
    private final String name;
    private T value;
    private ObservableValue<? extends T> boundSource;
    private final CopyOnWriteArrayList<InvalidationListener> invalidationListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ChangeListener<? super T>> changeListeners = new CopyOnWriteArrayList<>();

    public SimpleObjectProperty() {
        this(null, null, null);
    }

    public SimpleObjectProperty(T initialValue) {
        this(null, null, initialValue);
    }

    public SimpleObjectProperty(Object bean, String name) {
        this(bean, name, null);
    }

    public SimpleObjectProperty(Object bean, String name, T initialValue) {
        this.bean = bean;
        this.name = name;
        this.value = initialValue;
    }

    @Override
    public void addListener(InvalidationListener listener) {
        invalidationListeners.add(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        invalidationListeners.remove(listener);
    }

    @Override
    public void addListener(ChangeListener<? super T> listener) {
        changeListeners.add(listener);
    }

    public void removeListener(ChangeListener<? super T> listener) {
        changeListeners.remove(listener);
    }

    @Override
    public T getValue() {
        return get();
    }

    @Override
    public void setValue(T value) {
        set(value);
    }

    public T get() {
        if (boundSource != null) return boundSource.getValue();
        return value;
    }

    public void set(T newValue) {
        if (boundSource != null) {
            throw new RuntimeException("A bound value cannot be set.");
        }
        T old = value;
        if (!Objects.equals(old, newValue)) {
            value = newValue;
            invalidated();
            for (ChangeListener<? super T> l : changeListeners) l.changed(this, old, newValue);
            for (InvalidationListener l : invalidationListeners) l.invalidated(this);
        }
    }

    protected void invalidated() {
    }

    public Object getBean() {
        return bean;
    }

    public String getName() {
        return name;
    }

    public void bind(ObservableValue<? extends T> source) {
        Objects.requireNonNull(source);
        if (this.boundSource == source) return;
        this.boundSource = source;
        for (InvalidationListener l : invalidationListeners) l.invalidated(this);
    }

    public void unbind() {
        this.boundSource = null;
    }

    public boolean isBound() {
        return boundSource != null;
    }

    @Override
    public String toString() {
        Object obj = get();
        return obj == null ? "null" : obj.toString();
    }
}

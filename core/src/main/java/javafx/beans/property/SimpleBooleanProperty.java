package javafx.beans.property;

import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.util.concurrent.CopyOnWriteArrayList;

public class SimpleBooleanProperty extends BooleanProperty {
    private final Object bean;
    private final String name;
    private boolean value;
    private ObservableValue<? extends Boolean> boundSource;
    private final CopyOnWriteArrayList<InvalidationListener> invalidationListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ChangeListener<? super Boolean>> changeListeners = new CopyOnWriteArrayList<>();

    public SimpleBooleanProperty() {
        this(null, null, false);
    }

    public SimpleBooleanProperty(boolean initialValue) {
        this(null, null, initialValue);
    }

    public SimpleBooleanProperty(Object bean, String name, boolean initialValue) {
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

    public void addListener(ChangeListener<? super Boolean> listener) {
        changeListeners.add(listener);
    }

    public void removeListener(ChangeListener<? super Boolean> listener) {
        changeListeners.remove(listener);
    }

    @Override
    public void set(boolean newValue) {
        if (boundSource != null) {
            throw new RuntimeException("A bound value cannot be set.");
        }
        boolean old = value;
        if (old != newValue) {
            value = newValue;
            invalidated();
            for (ChangeListener<? super Boolean> l : changeListeners) l.changed(this, old, newValue);
            for (InvalidationListener l : invalidationListeners) l.invalidated(this);
        }
    }

    protected void invalidated() {
    }

    @Override
    public boolean get() {
        if (boundSource != null) return boundSource.getValue();
        return value;
    }

    public Object getBean() {
        return bean;
    }

    public String getName() {
        return name;
    }

    public void bind(ObservableValue<? extends Boolean> source) {
        if (source == null) throw new NullPointerException("Source cannot be null");
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
        return get() ? "true" : "false";
    }
}

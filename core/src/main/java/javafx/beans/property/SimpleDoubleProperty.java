package javafx.beans.property;

import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.util.concurrent.CopyOnWriteArrayList;

public class SimpleDoubleProperty extends DoubleProperty {

    private final Object bean;
    private final String name;
    private double value;
    private ObservableValue<? extends Number> boundSource;
    private final CopyOnWriteArrayList<InvalidationListener> invalidationListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ChangeListener<? super Number>> changeListeners = new CopyOnWriteArrayList<>();

    public SimpleDoubleProperty() {
        this(null, null, 0.0);
    }

    public SimpleDoubleProperty(double initialValue) {
        this(null, null, initialValue);
    }

    public SimpleDoubleProperty(Object bean, String name, double initialValue) {
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

    public void addListener(ChangeListener<? super Number> listener) {
        changeListeners.add(listener);
    }

    public void removeListener(ChangeListener<? super Number> listener) {
        changeListeners.remove(listener);
    }

    @Override
    public void set(double newValue) {
        if (boundSource != null) {
            throw new RuntimeException("A bound value cannot be set.");
        }
        double old = value;
        if (old != newValue) {
            value = newValue;
            for (ChangeListener<? super Number> l : changeListeners) l.changed(this, old, newValue);
            for (InvalidationListener l : invalidationListeners) l.invalidated(this);
        }
    }

    @Override
    public double get() {
        if (boundSource != null) return boundSource.getValue().doubleValue();
        return value;
    }

    public Object getBean() {
        return bean;
    }

    public String getName() {
        return name;
    }

    public void bind(ObservableValue<? extends Number> source) {
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
        return Double.toString(get());
    }
}

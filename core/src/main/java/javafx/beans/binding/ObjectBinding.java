package javafx.beans.binding;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 惰性绑定：由绑定源重新计算产生值。
 */
public abstract class ObjectBinding<T> implements ObservableValue<T> {

    private final CopyOnWriteArrayList<InvalidationListener> invalidationListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ChangeListener<? super T>> changeListeners = new CopyOnWriteArrayList<>();
    private boolean valid = false;
    private T value;
    private boolean computing = false;
    private final CopyOnWriteArrayList<Observable> boundSources = new CopyOnWriteArrayList<>();
    private final InvalidationListener sourceListener = observable -> invalidate();

    protected abstract T computeValue();

    public final T get() {
        if (!valid) {
            if (computing) throw new IllegalStateException("Recursive computation");
            computing = true;
            try {
                value = computeValue();
                valid = true;
            } finally {
                computing = false;
            }
        }
        return value;
    }

    protected void onInvalidating() {
    }

    public void invalidate() {
        if (valid) {
            valid = false;
            onInvalidating();
            for (InvalidationListener l : invalidationListeners) l.invalidated(this);
        }
    }

    public void bind(ObservableValue<? extends Object> source) {
        if (source == null) throw new NullPointerException("Source cannot be null");
        if (boundSources.contains(source)) return;
        boundSources.add(source);
        source.addListener(sourceListener);
        invalidate();
    }

    public void unbind(ObservableValue<? extends Object> source) {
        if (source == null) return;
        boundSources.remove(source);
        source.removeListener(sourceListener);
    }

    @Override
    public T getValue() {
        return get();
    }

    @Override
    public void addListener(InvalidationListener listener) {
        invalidationListeners.add(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        invalidationListeners.remove(listener);
    }

    public void addListener(ChangeListener<? super T> listener) {
        changeListeners.add(listener);
    }

    public void removeListener(ChangeListener<? super T> listener) {
        changeListeners.remove(listener);
    }
}

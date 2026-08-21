package javafx.beans.binding;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BooleanBinding implements ObservableValue<Boolean> {

    private final CopyOnWriteArrayList<InvalidationListener> invalidationListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ChangeListener<? super Boolean>> changeListeners = new CopyOnWriteArrayList<>();
    private boolean valid = false;
    private boolean value;
    private boolean computing = false;

    protected abstract boolean computeBoolean();

    public final boolean get() {
        if (!valid) {
            if (computing) throw new IllegalStateException("Recursive computation");
            computing = true;
            try {
                value = computeBoolean();
                valid = true;
            } finally {
                computing = false;
            }
        }
        return value;
    }

    public void invalidate() {
        if (valid) {
            valid = false;
            for (InvalidationListener l : invalidationListeners) l.invalidated(this);
        }
    }

    @Override
    public Boolean getValue() {
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

    public void addListener(ChangeListener<? super Boolean> listener) {
        changeListeners.add(listener);
    }

    public void removeListener(ChangeListener<? super Boolean> listener) {
        changeListeners.remove(listener);
    }
}

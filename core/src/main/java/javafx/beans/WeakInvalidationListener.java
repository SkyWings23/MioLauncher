package javafx.beans;

import java.lang.ref.WeakReference;
import java.util.Objects;

public class WeakInvalidationListener implements InvalidationListener, WeakListener {

    private final WeakReference<InvalidationListener> ref;

    public WeakInvalidationListener(InvalidationListener listener) {
        this.ref = new WeakReference<>(Objects.requireNonNull(listener));
    }

    @Override
    public void invalidated(Observable observable) {
        InvalidationListener listener = ref.get();
        if (listener != null) {
            listener.invalidated(observable);
        }
    }

    @Override
    public boolean wasGarbageCollected() {
        return ref.get() == null;
    }
}

package javafx.beans.value;

import javafx.beans.WeakListener;

import java.lang.ref.WeakReference;
import java.util.Objects;

public class WeakChangeListener<T> implements ChangeListener<T>, WeakListener {

    private final WeakReference<ChangeListener<T>> ref;

    public WeakChangeListener(ChangeListener<T> listener) {
        this.ref = new WeakReference<>(Objects.requireNonNull(listener));
    }

    @Override
    public void changed(ObservableValue<? extends T> observable, T oldValue, T newValue) {
        ChangeListener<T> listener = ref.get();
        if (listener != null) {
            listener.changed(observable, oldValue, newValue);
        }
    }

    @Override
    public boolean wasGarbageCollected() {
        return ref.get() == null;
    }
}

package javafx.beans.value;

import javafx.beans.Observable;

@FunctionalInterface
public interface ChangeListener<T> {
    void changed(ObservableValue<? extends T> observable, T oldValue, T newValue);
}

package javafx.beans.property;

import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;

public interface Property<T> extends ObservableValue<T>, WritableValue<T> {

    void bind(ObservableValue<? extends T> source);

    void unbind();

    boolean isBound();
}

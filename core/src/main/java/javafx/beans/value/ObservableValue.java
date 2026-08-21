package javafx.beans.value;

import javafx.beans.Observable;

public interface ObservableValue<T> extends Observable {

    T getValue();

    void addListener(ChangeListener<? super T> listener);

    void removeListener(ChangeListener<? super T> listener);
}

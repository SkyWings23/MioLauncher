package javafx.collections;

import javafx.beans.Observable;

import java.util.List;

public interface ObservableList<E> extends List<E>, Observable {

    boolean setAll(E... elements);

    boolean setAll(java.util.Collection<? extends E> col);

    void addListener(ListChangeListener<? super E> listener);

    void removeListener(ListChangeListener<? super E> listener);
}

package javafx.collections;

import javafx.beans.InvalidationListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 支持变更通知的 List 实现。
 */
public class ObservableListWrapper<E> implements ObservableList<E> {

    private final List<E> backingList;
    private final CopyOnWriteArrayList<InvalidationListener> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ListChangeListener<? super E>> changeListeners = new CopyOnWriteArrayList<>();

    public ObservableListWrapper(List<E> backingList) {
        this.backingList = backingList;
    }

    public ObservableListWrapper() {
        this(new ArrayList<>());
    }

    private void fireInvalidation() {
        for (InvalidationListener l : listeners) l.invalidated(this);
    }

    private void fireChange() {
        for (ListChangeListener<? super E> l : changeListeners) {
            l.onChanged(new SimpleChange());
        }
    }

    private final class SimpleChange extends ListChangeListener.Change<E> {
        private SimpleChange() {
            super(ObservableListWrapper.this);
        }

        @Override
        public boolean next() {
            return false;
        }

        @Override
        public void reset() {
        }
    }

    @Override
    public boolean setAll(E... elements) {
        backingList.clear();
        java.util.Collections.addAll(backingList, elements);
        fireInvalidation();
        fireChange();
        return true;
    }

    @Override
    public boolean setAll(Collection<? extends E> col) {
        backingList.clear();
        backingList.addAll(col);
        fireInvalidation();
        fireChange();
        return true;
    }

    @Override
    public void addListener(InvalidationListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        listeners.remove(listener);
    }

    public void addListener(ListChangeListener<? super E> listener) {
        changeListeners.add(listener);
    }

    public void removeListener(ListChangeListener<? super E> listener) {
        changeListeners.remove(listener);
    }

    @Override
    public int size() {
        return backingList.size();
    }

    @Override
    public boolean isEmpty() {
        return backingList.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return backingList.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return backingList.iterator();
    }

    @Override
    public Object[] toArray() {
        return backingList.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return backingList.toArray(a);
    }

    @Override
    public boolean add(E e) {
        boolean r = backingList.add(e);
        if (r) {
            fireInvalidation();
            fireChange();
        }
        return r;
    }

    @Override
    public boolean remove(Object o) {
        boolean r = backingList.remove(o);
        if (r) {
            fireInvalidation();
            fireChange();
        }
        return r;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return backingList.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean r = backingList.addAll(c);
        if (r) {
            fireInvalidation();
            fireChange();
        }
        return r;
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        boolean r = backingList.addAll(index, c);
        if (r) {
            fireInvalidation();
            fireChange();
        }
        return r;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean r = backingList.removeAll(c);
        if (r) {
            fireInvalidation();
            fireChange();
        }
        return r;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean r = backingList.retainAll(c);
        if (r) {
            fireInvalidation();
            fireChange();
        }
        return r;
    }

    @Override
    public void clear() {
        if (!backingList.isEmpty()) {
            backingList.clear();
            fireInvalidation();
            fireChange();
        }
    }

    @Override
    public E get(int index) {
        return backingList.get(index);
    }

    @Override
    public E set(int index, E element) {
        E r = backingList.set(index, element);
        fireInvalidation();
        fireChange();
        return r;
    }

    @Override
    public void add(int index, E element) {
        backingList.add(index, element);
        fireInvalidation();
        fireChange();
    }

    @Override
    public E remove(int index) {
        E r = backingList.remove(index);
        fireInvalidation();
        fireChange();
        return r;
    }

    @Override
    public int indexOf(Object o) {
        return backingList.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return backingList.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return backingList.listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return backingList.listIterator(index);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return backingList.subList(fromIndex, toIndex);
    }
}

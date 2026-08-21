package javafx.collections.transformation;

import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 简单实现：把源 ObservableList 的变更同步到一个派生视图。
 * 用于 HMCLCore 中对列表的只读变换。
 */
public abstract class TransformationList<E, F> implements ObservableList<E> {

    protected final ObservableList<? extends F> source;
    private final CopyOnWriteArrayList<InvalidationListener> invalidationListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ListChangeListener<? super E>> changeListeners = new CopyOnWriteArrayList<>();

    @SuppressWarnings("unchecked")
    public TransformationList(ObservableList<? extends F> source) {
        this.source = source;
        source.addListener((InvalidationListener) observable -> fireInvalidation());
        source.addListener((ListChangeListener<F>) change -> fireChange());
    }

    private void fireInvalidation() {
        for (InvalidationListener l : invalidationListeners) l.invalidated(this);
    }

    private void fireChange() {
        for (ListChangeListener<? super E> l : changeListeners) l.onChanged(null);
    }

    public ObservableList<? extends F> getSource() {
        return source;
    }

    @Override
    public void addListener(InvalidationListener listener) {
        invalidationListeners.add(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        invalidationListeners.remove(listener);
    }

    public void addListener(ListChangeListener<? super E> listener) {
        changeListeners.add(listener);
    }

    public void removeListener(ListChangeListener<? super E> listener) {
        changeListeners.remove(listener);
    }

    @Override
    public boolean setAll(E... elements) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean setAll(Collection<? extends E> col) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return source.size();
    }

    @Override
    public boolean isEmpty() {
        return source.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return source.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return new ArrayList<>(this).iterator();
    }

    @Override
    public Object[] toArray() {
        return new ArrayList<>(this).toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return new ArrayList<>(this).toArray(a);
    }

    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return source.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get(int index) {
        return (E) source.get(index);
    }

    @Override
    public E set(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public E remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        return source.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return source.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return new ArrayList<>(this).listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new ArrayList<>(this).listIterator(index);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return new ArrayList<>(this).subList(fromIndex, toIndex);
    }
}

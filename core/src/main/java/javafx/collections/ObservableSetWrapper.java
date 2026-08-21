package javafx.collections;

import javafx.beans.InvalidationListener;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class ObservableSetWrapper<E> implements ObservableSet<E> {

    private final Set<E> backingSet;
    private final CopyOnWriteArrayList<InvalidationListener> listeners = new CopyOnWriteArrayList<>();

    public ObservableSetWrapper(Set<E> backingSet) {
        this.backingSet = backingSet;
    }

    private void fire() {
        for (InvalidationListener l : listeners) l.invalidated(this);
    }

    @Override
    public void addListener(InvalidationListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        listeners.remove(listener);
    }

    @Override
    public int size() {
        return backingSet.size();
    }

    @Override
    public boolean isEmpty() {
        return backingSet.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return backingSet.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return backingSet.iterator();
    }

    @Override
    public Object[] toArray() {
        return backingSet.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return backingSet.toArray(a);
    }

    @Override
    public boolean add(E e) {
        boolean r = backingSet.add(e);
        if (r) fire();
        return r;
    }

    @Override
    public boolean remove(Object o) {
        boolean r = backingSet.remove(o);
        if (r) fire();
        return r;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return backingSet.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean r = backingSet.addAll(c);
        if (r) fire();
        return r;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean r = backingSet.retainAll(c);
        if (r) fire();
        return r;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean r = backingSet.removeAll(c);
        if (r) fire();
        return r;
    }

    @Override
    public void clear() {
        if (!backingSet.isEmpty()) {
            backingSet.clear();
            fire();
        }
    }
}

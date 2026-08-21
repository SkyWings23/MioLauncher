package javafx.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FXCollections {

    private FXCollections() {
    }

    public static <E> ObservableList<E> observableArrayList() {
        return new ObservableListWrapper<>(new ArrayList<>());
    }

    public static <E> ObservableList<E> observableArrayList(Collection<? extends E> col) {
        return new ObservableListWrapper<>(new ArrayList<>(col));
    }

    public static <E> ObservableList<E> observableList(List<E> list) {
        return new ObservableListWrapper<>(list);
    }

    public static <E> ObservableSet<E> observableSet() {
        return new ObservableSetWrapper<>(new LinkedHashSet<>());
    }

    public static <E> ObservableSet<E> observableSet(Set<E> set) {
        return new ObservableSetWrapper<>(set);
    }

    public static <E> ObservableSet<E> observableSet(E... elements) {
        return new ObservableSetWrapper<>(new LinkedHashSet<>(java.util.Arrays.asList(elements)));
    }

    public static <K, V> ObservableMap<K, V> observableMap(Map<K, V> map) {
        return new ObservableMapWrapper<>(map);
    }
}

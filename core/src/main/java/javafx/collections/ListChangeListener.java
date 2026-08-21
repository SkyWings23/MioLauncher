package javafx.collections;

public interface ListChangeListener<E> {

    void onChanged(Change<? extends E> c);

    abstract class Change<E> {
        private final ObservableList<E> list;

        public Change(ObservableList<E> list) {
            this.list = list;
        }

        public ObservableList<E> getList() {
            return list;
        }

        public abstract boolean next();

        public abstract void reset();

        public boolean wasAdded() {
            return false;
        }

        public boolean wasRemoved() {
            return false;
        }

        public java.util.List<? extends E> getAddedSubList() {
            return java.util.Collections.emptyList();
        }

        public java.util.List<? extends E> getRemoved() {
            return java.util.Collections.emptyList();
        }

        public int getFrom() {
            return -1;
        }

        public int getTo() {
            return -1;
        }
    }
}

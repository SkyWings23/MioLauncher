package javafx.beans;

@FunctionalInterface
public interface InvalidationListener {
    void invalidated(Observable observable);
}

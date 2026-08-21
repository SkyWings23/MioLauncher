package javafx.beans.binding;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class Bindings {

    private Bindings() {
    }

    public static <T> ObjectBinding<T> createObjectBinding(Supplier<T> func, Observable... dependencies) {
        Objects.requireNonNull(func);
        return new ObjectBinding<T>() {
            {
                for (Observable dep : dependencies) {
                    if (dep != null) {
                        dep.addListener(new InvalidationListener() {
                            @Override
                            public void invalidated(Observable observable) {
                                invalidate();
                            }
                        });
                    }
                }
            }

            @Override
            protected T computeValue() {
                return func.get();
            }
        };
    }

    public static BooleanBinding createBooleanBinding(java.util.function.BooleanSupplier func, Observable... dependencies) {
        Objects.requireNonNull(func);
        return new BooleanBinding() {
            {
                for (Observable dep : dependencies) {
                    if (dep != null) {
                        dep.addListener(new InvalidationListener() {
                            @Override
                            public void invalidated(Observable observable) {
                                invalidate();
                            }
                        });
                    }
                }
            }

            @Override
            protected boolean computeBoolean() {
                return func.getAsBoolean();
            }
        };
    }
}

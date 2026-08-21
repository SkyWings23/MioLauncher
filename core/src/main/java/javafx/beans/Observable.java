package javafx.beans;

/**
 * 兼容包：与 JavaFX 的 Observable 接口等价的最小实现。
 * 用于让 HMCLCore 在 Android 平台无需 JavaFX 也能编译。
 */
public interface Observable {
    void addListener(InvalidationListener listener);

    void removeListener(InvalidationListener listener);
}

package org.fxmisc.easybind.select;

import java.util.function.Function;

import javafx.beans.InvalidationListener;
import javafx.beans.value.ObservableValue;

class LeafSelectionElement<T, U> implements NestedSelectionElement<T, U> {
    private final InvalidationListener observableInvalidationListener = obs -> observableInvalidated();
    private final Runnable onInvalidation;
    private final Function<? super T, ObservableValue<U>> selector;

    private ObservableValue<U> observable = null;

    public LeafSelectionElement(Runnable onInvalidation, Function<? super T, ObservableValue<U>> selector) {
        this.onInvalidation = onInvalidation;
        this.selector = selector;
    }

    @Override
    public void connect(T baseVal) {
        if(isConnected()) {
            throw new IllegalStateException("Already connected");
        }

        observable = selector.apply(baseVal);
        observable.addListener(observableInvalidationListener);
    }

    @Override
    public void disconnect() {
        if(isConnected()) {
            observable.removeListener(observableInvalidationListener);
            observable = null;
        }
    }

    @Override
    public final boolean isConnected() {
        return observable != null;
    }

    @Override
    public U getValue() {
        if(!isConnected()) {
            throw new IllegalStateException("Not connected");
        }

        return observable.getValue();
    }

    private void observableInvalidated() {
        onInvalidation.run();
    }
}
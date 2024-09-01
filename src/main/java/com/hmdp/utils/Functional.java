package com.hmdp.utils;

import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public interface Functional<T extends Functional<T>> {
    default T also(Consumer<T> consumer) {
        consumer.accept((T) this);
        return (T) this;
    }

    default <R> R let(Function<T,R> function) {
        return function.apply((T) this);
    }
}

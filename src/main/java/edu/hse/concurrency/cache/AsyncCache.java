package edu.hse.concurrency.cache;

import java.util.concurrent.CompletableFuture;

public interface AsyncCache<K, V> extends AutoCloseable {

    CompletableFuture<V> get(K key);

    @Override
    void close();
}

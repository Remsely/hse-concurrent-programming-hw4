package edu.hse.concurrency.cache;

import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

public class AsyncCacheImpl<K, V> implements AsyncCache<K, V> {

    public AsyncCacheImpl(CacheConfig config, CacheLoader<K, V> loader) {
        this(config, loader, System::nanoTime);
    }

    public AsyncCacheImpl(CacheConfig config, CacheLoader<K, V> loader, LongSupplier nanoTime) {
        // TODO
    }

    @Override
    public CompletableFuture<V> get(K key) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("TODO");
    }
}

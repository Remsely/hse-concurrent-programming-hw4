package edu.hse.concurrency.cache;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface CacheLoader<K, V> {

    CompletionStage<V> load(K key, AsyncCache<K, V> cache) throws Exception;
}

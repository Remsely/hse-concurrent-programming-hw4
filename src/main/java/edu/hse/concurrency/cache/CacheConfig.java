package edu.hse.concurrency.cache;

import java.time.Duration;
import java.util.Objects;

public record CacheConfig(Duration freshTtl, Duration staleTtl, Duration loadTimeout,
                          int workers, int queueCapacity) {

    private static final Duration MAX_DURATION = Duration.ofDays(1);

    public CacheConfig {
        Objects.requireNonNull(freshTtl, "freshTtl");
        Objects.requireNonNull(staleTtl, "staleTtl");
        Objects.requireNonNull(loadTimeout, "loadTimeout");
        if (freshTtl.isZero() || freshTtl.isNegative() || freshTtl.compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("freshTtl must be positive and at most one day");
        }
        if (staleTtl.isNegative() || staleTtl.compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("staleTtl must be between zero and one day");
        }
        if (loadTimeout.isZero() || loadTimeout.isNegative() || loadTimeout.compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("loadTimeout must be positive and at most one day");
        }
        if (workers < 1 || workers > 64) {
            throw new IllegalArgumentException("workers must be between 1 and 64");
        }
        if (queueCapacity < 1 || queueCapacity > 10_000) {
            throw new IllegalArgumentException("queueCapacity must be between 1 and 10000");
        }
    }
}

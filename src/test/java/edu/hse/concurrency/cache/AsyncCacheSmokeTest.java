package edu.hse.concurrency.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class AsyncCacheSmokeTest {

    private static final Duration FRESH = Duration.ofSeconds(1);
    private static final Duration STALE = Duration.ofSeconds(2);
    private static final CacheConfig CONFIG = new CacheConfig(FRESH, STALE, Duration.ofSeconds(5), 1, 8);

    @Test
    void twoMissesShareOneLoadAndCancellationIsLocal() throws Exception {
        var calls = new AtomicInteger();
        var value = new CompletableFuture<String>();
        try (var cache = new AsyncCacheImpl<String, String>(CONFIG, (key, ignored) -> {
            calls.incrementAndGet();
            return value;
        })) {
            var first = cache.get("orders");
            var second = cache.get("orders");
            assertTrue(first.cancel(false));
            value.complete("open");

            assertEquals("open", second.get(2, SECONDS));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void aFailedLoadCanBeRetried() throws Exception {
        var calls = new AtomicInteger();
        var failure = new IOException("source unavailable");
        try (var cache = new AsyncCacheImpl<String, String>(CONFIG, (key, ignored) ->
                calls.incrementAndGet() == 1
                        ? CompletableFuture.failedFuture(failure)
                        : CompletableFuture.completedFuture("ready"))) {
            var bad = cache.get("key");
            assertSame(failure, assertThrows(ExecutionException.class, () -> bad.get(2, SECONDS)).getCause());
            assertEquals("ready", cache.get("key").get(2, SECONDS));
        }
    }

    @Test
    void staleValueIsImmediateAndExpiredValueWaitsForTheRefresh() throws Exception {
        var clock = new AtomicLong();
        var calls = new AtomicInteger();
        var refresh = new CompletableFuture<String>();
        try (var cache = new AsyncCacheImpl<String, String>(CONFIG, (key, ignored) ->
                calls.incrementAndGet() == 1 ? CompletableFuture.completedFuture("v1") : refresh, clock::get)) {
            assertEquals("v1", cache.get("key").get(2, SECONDS));

            clock.set(FRESH.toNanos());
            var stale = cache.get("key");
            assertTrue(stale.isDone());
            assertEquals("v1", stale.get(2, SECONDS));

            clock.addAndGet(STALE.toNanos());
            var expired = cache.get("key");
            assertFalse(expired.isDone());
            refresh.complete("v2");
            assertEquals("v2", expired.get(2, SECONDS));
            assertEquals(2, calls.get());
        }
    }

    @Test
    void loaderCanComposeWithAnotherKeyOnOneWorker() throws Exception {
        try (var cache = new AsyncCacheImpl<String, String>(CONFIG, (key, owner) -> {
            if (key.equals("Aa")) {
                return owner.get("BB").thenApply(value -> "root:" + value);
            }
            return CompletableFuture.completedFuture("leaf");
        })) {
            assertEquals("root:leaf", cache.get("Aa").get(2, SECONDS));
        }
    }

    @Test
    void timeoutReachesAllCallersWhileTheLoadIsStillRunning() throws Exception {
        var config = new CacheConfig(FRESH, STALE, Duration.ofMillis(750), 1, 8);
        var calls = new AtomicInteger();
        var started = new CountDownLatch(1);
        var value = new CompletableFuture<String>();
        try (var cache = new AsyncCacheImpl<String, String>(config, (key, ignored) -> {
            calls.incrementAndGet();
            started.countDown();
            return value;
        })) {
            var first = cache.get("key");
            assertTrue(started.await(2, SECONDS));
            var second = cache.get("key");
            assertInstanceOf(TimeoutException.class,
                    assertThrows(ExecutionException.class, () -> first.get(2, SECONDS)).getCause());
            assertInstanceOf(TimeoutException.class,
                    assertThrows(ExecutionException.class, () -> second.get(2, SECONDS)).getCause());
            assertEquals(1, calls.get());
        } finally {
            value.complete("late");
        }
    }

    @Test
    void fullQueueRejectsTheNextMiss() throws Exception {
        var config = new CacheConfig(FRESH, STALE, Duration.ofSeconds(5), 1, 1);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var cache = new AsyncCacheImpl<String, String>(config, (key, ignored) -> {
            if (key.equals("first")) {
                started.countDown();
                release.await();
            }
            return CompletableFuture.completedFuture(key);
        })) {
            var first = cache.get("first");
            assertTrue(started.await(2, SECONDS));
            var queued = cache.get("queued");
            var rejected = cache.get("rejected");
            assertInstanceOf(RejectedExecutionException.class,
                    assertThrows(ExecutionException.class, () -> rejected.get(2, SECONDS)).getCause());

            release.countDown();
            assertEquals("first", first.get(2, SECONDS));
            assertEquals("queued", queued.get(2, SECONDS));
        } finally {
            release.countDown();
        }
    }

    @Test
    void closeCompletesPendingAndNewCallsExceptionally() {
        var value = new CompletableFuture<String>();
        try (var cache = new AsyncCacheImpl<String, String>(CONFIG, (key, ignored) -> value)) {
            var pending = cache.get("key");
            cache.close();

            assertInstanceOf(IllegalStateException.class,
                    assertThrows(ExecutionException.class, () -> pending.get(2, SECONDS)).getCause());
            var afterClose = cache.get("key");
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(ExecutionException.class, () -> afterClose.get(2, SECONDS)).getCause());
        } finally {
            value.complete("late");
        }
    }
}

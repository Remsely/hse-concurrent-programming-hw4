package edu.hse.concurrency.cache;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CacheLoadProbe {

    private static final int REQUESTS = 256;
    private static final byte[] RESPONSE = "value".getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        int workers = Integer.parseInt(args[0]);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), REQUESTS);
        try (var serverThreads = Executors.newVirtualThreadPerTaskExecutor();
             var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            server.setExecutor(serverThreads);
            server.createContext("/", exchange -> {
                try {
                    Thread.sleep(40);
                    exchange.sendResponseHeaders(200, RESPONSE.length);
                    exchange.getResponseBody().write(RESPONSE);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            try {
                var config = new CacheConfig(Duration.ofMinutes(1), Duration.ZERO, Duration.ofSeconds(30),
                        workers, REQUESTS);
                try (var cache = new AsyncCacheImpl<String, String>(config, (key, ignored) -> {
                    var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + key);
                    var request = HttpRequest.newBuilder(uri).GET().build();
                    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    return CompletableFuture.completedFuture(response.body());
                })) {
                    var warmup = new CompletableFuture<?>[32];
                    for (int i = 0; i < warmup.length; i++) {
                        warmup[i] = cache.get("warmup-" + i);
                    }
                    CompletableFuture.allOf(warmup).join();
                    measure(cache, workers);
                }
            } finally {
                server.stop(0);
            }
        }
    }

    private static void measure(AsyncCache<String, String> cache, int workers) {
        var tasks = new ArrayList<CompletableFuture<Void>>();
        var errors = new AtomicInteger();
        var latencies = new long[REQUESTS];
        long started = System.nanoTime();
        for (int i = 0; i < REQUESTS; i++) {
            int index = i;
            long sent = System.nanoTime();
            tasks.add(cache.get("probe-" + i).handle((value, error) -> {
                latencies[index] = System.nanoTime() - sent;
                if (error != null) {
                    errors.incrementAndGet();
                }
                return null;
            }));
        }
        CompletableFuture.allOf(tasks.toArray(CompletableFuture<?>[]::new)).join();
        double elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
        Arrays.sort(latencies);
        double p95Millis = latencies[(int) Math.ceil(REQUESTS * 0.95) - 1] / 1_000_000.0;
        double throughput = (REQUESTS - errors.get()) / elapsedSeconds;
        System.out.printf(Locale.ROOT, "workers=%d requests=%d throughput=%.1f/s p95=%.1fms errors=%d%n",
                workers, REQUESTS, throughput, p95Millis, errors.get());
    }
}

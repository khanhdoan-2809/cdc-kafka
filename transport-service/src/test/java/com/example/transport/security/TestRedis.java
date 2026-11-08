package com.example.transport.security;

import org.springframework.test.context.DynamicPropertyRegistry;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Isolated real Redis, launched from PATH or REDIS_TEST_EXECUTABLE. Never connects to a shared DB. */
final class TestRedis implements AutoCloseable {
    private static class Shared { static final TestRedis INSTANCE = new TestRedis(); }
    static TestRedis shared() { return Shared.INSTANCE; }
    final int port;
    final String password = UUID.randomUUID().toString();
    final Path directory;
    private Process process;

    TestRedis() {
        try {
            Files.createDirectories(Path.of("target"));
            directory = Files.createTempDirectory(Path.of("target"), "test-redis-").toAbsolutePath();
            try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
                port = socket.getLocalPort();
            }
            start();
            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        } catch (Exception failure) {
            throw new IllegalStateException("Real Redis required: install redis-server or set REDIS_TEST_EXECUTABLE", failure);
        }
    }

    static void properties(DynamicPropertyRegistry registry) {
        var instance = Shared.INSTANCE;
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> instance.port);
        registry.add("spring.data.redis.password", () -> instance.password);
    }

    synchronized void restart() throws Exception {
        close();
        start();
    }

    private void start() throws Exception {
        String executable = System.getenv().getOrDefault("REDIS_TEST_EXECUTABLE", "redis-server");
        process = new ProcessBuilder(executable, "--bind", "127.0.0.1", "--port", "" + port,
                "--requirepass", password, "--save", "", "--appendonly", "no",
                "--maxmemory", "128mb", "--maxmemory-policy", "noeviction")
                .directory(directory.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(directory.resolve("redis.log").toFile())).start();
        for (int i = 0; i < 100; i++) {
            if (!process.isAlive()) throw new IllegalStateException("Redis exited; see " + directory);
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
                socket.setSoTimeout(500);
                var out = socket.getOutputStream();
                out.write(("*2\r\n$4\r\nAUTH\r\n$" + password.length() + "\r\n" + password + "\r\n").getBytes(StandardCharsets.US_ASCII));
                var in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                if ("+OK".equals(in.readLine())) return;
            } catch (java.io.IOException starting) {
                Thread.sleep(50);
            }
        }
        close();
        throw new IllegalStateException("Redis startup timed out; see " + directory);
    }

    @Override
    public synchronized void close() {
        if (process == null) return;
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}

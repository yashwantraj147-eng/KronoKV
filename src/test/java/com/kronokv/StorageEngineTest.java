package com.kronokv;

import com.kronokv.engine.StorageEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StorageEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void testSetAndGet() {
        Path walPath = tempDir.resolve("test_crud.wal");
        try (StorageEngine engine = new StorageEngine(walPath)) {
            engine.set("key1", "val1", -1);
            assertEquals("val1", engine.get("key1"));
        }
    }

    @Test
    void testGetNonExistent() {
        Path walPath = tempDir.resolve("test_nonexist.wal");
        try (StorageEngine engine = new StorageEngine(walPath)) {
            assertNull(engine.get("unknown"));
        }
    }

    @Test
    void testDeleteExisting() {
        Path walPath = tempDir.resolve("test_del.wal");
        try (StorageEngine engine = new StorageEngine(walPath)) {
            engine.set("key1", "val1", -1);
            assertTrue(engine.delete("key1"));
            assertNull(engine.get("key1"));
        }
    }

    @Test
    void testDeleteNonExistent() {
        Path walPath = tempDir.resolve("test_del_none.wal");
        try (StorageEngine engine = new StorageEngine(walPath)) {
            assertFalse(engine.delete("unknown"));
        }
    }

    @Test
    void testOverwriteValue() {
        Path walPath = tempDir.resolve("test_overwrite.wal");
        try (StorageEngine engine = new StorageEngine(walPath)) {
            engine.set("key1", "val1", -1);
            engine.set("key1", "val2", -1);
            assertEquals("val2", engine.get("key1"));
        }
    }

    @Test
    void testListKeys() {
        Path walPath = tempDir.resolve("test_keys.wal");
        try (StorageEngine engine = new StorageEngine(walPath)) {
            engine.set("b", "val", -1);
            engine.set("a", "val", -1);
            engine.set("c", "val", -1);
            List<String> keys = engine.listKeys();
            assertEquals(List.of("a", "b", "c"), keys);
        }
    }

    @Test
    void testPassiveExpiration() throws InterruptedException {
        Path walPath = tempDir.resolve("test_passive_ttl.wal");
        try (StorageEngine engine = new StorageEngine(walPath)) {
            engine.set("key1", "val1", 1);
            Thread.sleep(1500);
            assertNull(engine.get("key1"));
        }
    }

    @Test
    @Timeout(15)
    void testActiveExpiration() throws InterruptedException {
        Path walPath = tempDir.resolve("test_active_ttl.wal");
        try (StorageEngine engine = new StorageEngine(walPath)) {
            engine.set("key1", "val1", 1);
            Thread.sleep(7000);
            assertNull(engine.get("key1"));
            assertFalse(engine.listKeys().contains("key1"));
        }
    }

    @Test
    @Timeout(30)
    void testConcurrentWritesAndReads() throws InterruptedException {
        Path walPath = tempDir.resolve("test_concurrent.wal");
        try (StorageEngine engine = new StorageEngine(walPath)) {
            int threadCount = 10;
            int keysPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < keysPerThread; j++) {
                            engine.set("key_" + threadId + "_" + j, "val", -1);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(20, TimeUnit.SECONDS));

            for (int i = 0; i < threadCount; i++) {
                for (int j = 0; j < keysPerThread; j++) {
                    assertEquals("val", engine.get("key_" + i + "_" + j));
                }
            }

            executor.shutdown();
        }
    }

    @Test
    void testCompaction() {
        Path walPath = tempDir.resolve("test_compact.wal");
        try (StorageEngine engine = new StorageEngine(walPath)) {
            engine.set("key1", "val1", -1);
            engine.set("key2", "val2", -1);
            engine.delete("key1");
            engine.compact();
            assertNull(engine.get("key1"));
            assertEquals("val2", engine.get("key2"));
        }
    }

    @Test
    void testCrashRecovery() {
        Path walPath = tempDir.resolve("test_recovery.wal");

        try (StorageEngine engine = new StorageEngine(walPath)) {
            engine.set("key1", "val1", -1);
            engine.set("key2", "val2", -1);
        }

        try (StorageEngine recovered = new StorageEngine(walPath)) {
            assertEquals("val1", recovered.get("key1"));
            assertEquals("val2", recovered.get("key2"));
        }
    }

    @Test
    void testStats() {
        Path walPath = tempDir.resolve("test_stats.wal");
        try (StorageEngine engine = new StorageEngine(walPath)) {
            engine.set("key1", "val1", -1);
            engine.set("key2", "val2", -1);
            String stats = engine.stats();
            assertNotNull(stats);
            assertTrue(stats.contains("Keys: 2"));
        }
    }
}

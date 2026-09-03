package com.kronokv;

import com.kronokv.storage.WriteAheadLog;
import com.kronokv.model.DataEntry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class WriteAheadLogTest {

    @TempDir
    Path tempDir;

    @Test
    void testAppendAndReplay() throws IOException {
        Path walPath = tempDir.resolve("append_replay.wal");
        try (WriteAheadLog wal = new WriteAheadLog(walPath)) {
            wal.append("SET", "key1", "val1", -1);
            wal.append("SET", "key2", "val2", -1);

            Map<String, DataEntry> entries = wal.replay();
            assertEquals(2, entries.size());
            assertEquals("val1", entries.get("key1").value());
            assertEquals("val2", entries.get("key2").value());
        }
    }

    @Test
    void testReplayWithDeletes() throws IOException {
        Path walPath = tempDir.resolve("replay_del.wal");
        try (WriteAheadLog wal = new WriteAheadLog(walPath)) {
            wal.append("SET", "key1", "val1", -1);
            wal.append("DEL", "key1", "", -1);

            Map<String, DataEntry> entries = wal.replay();
            assertFalse(entries.containsKey("key1"));
        }
    }

    @Test
    void testReplaySkipsExpiredEntries() throws IOException {
        Path walPath = tempDir.resolve("replay_expired.wal");
        try (WriteAheadLog wal = new WriteAheadLog(walPath)) {
            long pastTime = System.currentTimeMillis() - 10000;
            wal.append("SET", "key1", "val1", pastTime);
            wal.append("SET", "key2", "val2", -1);

            Map<String, DataEntry> entries = wal.replay();
            assertFalse(entries.containsKey("key1"));
            assertTrue(entries.containsKey("key2"));
        }
    }

    @Test
    void testCrashRecoveryPersistence() throws IOException {
        Path walPath = tempDir.resolve("crash_recovery.wal");

        try (WriteAheadLog wal = new WriteAheadLog(walPath)) {
            wal.append("SET", "key1", "val1", -1);
        }

        try (WriteAheadLog recovered = new WriteAheadLog(walPath)) {
            Map<String, DataEntry> entries = recovered.replay();
            assertEquals(1, entries.size());
            assertEquals("val1", entries.get("key1").value());
        }
    }

    @Test
    void testCompactionReducesFileSize() throws IOException {
        Path walPath = tempDir.resolve("compaction.wal");
        try (WriteAheadLog wal = new WriteAheadLog(walPath)) {
            for (int i = 0; i < 100; i++) {
                wal.append("SET", "key" + i, "val" + i, -1);
            }
            for (int i = 0; i < 90; i++) {
                wal.append("DEL", "key" + i, "", -1);
            }

            long sizeBefore = Files.size(walPath);

            ConcurrentHashMap<String, DataEntry> activeEntries = new ConcurrentHashMap<>();
            for (int i = 90; i < 100; i++) {
                activeEntries.put("key" + i, new DataEntry("key" + i, "val" + i, -1));
            }

            wal.compact(activeEntries);

            long sizeAfter = Files.size(walPath);
            assertTrue(sizeAfter < sizeBefore, "WAL should be smaller after compaction");

            Map<String, DataEntry> replayed = wal.replay();
            assertEquals(10, replayed.size());
        }
    }

    @Test
    void testReplayHandlesCorruptedLines() throws IOException {
        Path walPath = tempDir.resolve("corrupted.wal");
        try (WriteAheadLog wal = new WriteAheadLog(walPath)) {
            wal.append("SET", "key1", "val1", -1);

            Files.writeString(walPath, "CORRUPTED_LINE\n", StandardOpenOption.APPEND);

            wal.append("SET", "key2", "val2", -1);

            Map<String, DataEntry> entries = wal.replay();
            assertEquals(2, entries.size());
            assertTrue(entries.containsKey("key1"));
            assertTrue(entries.containsKey("key2"));
        }
    }

    @Test
    void testSpecialCharactersInValues() throws IOException {
        Path walPath = tempDir.resolve("special_chars.wal");
        try (WriteAheadLog wal = new WriteAheadLog(walPath)) {
            String keyWithPipe = "key|special";
            String valueWithPipe = "val|with\\backslash";
            wal.append("SET", keyWithPipe, valueWithPipe, -1);

            Map<String, DataEntry> entries = wal.replay();
            assertEquals(1, entries.size());
            assertTrue(entries.containsKey(keyWithPipe));
            assertEquals(valueWithPipe, entries.get(keyWithPipe).value());
        }
    }
}

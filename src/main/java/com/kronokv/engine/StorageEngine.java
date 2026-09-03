package com.kronokv.engine;

import com.kronokv.exception.KronoException;
import com.kronokv.model.DataEntry;
import com.kronokv.storage.WriteAheadLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class StorageEngine implements AutoCloseable {
    private final ConcurrentHashMap<String, DataEntry> store;
    private final ReentrantReadWriteLock rwLock;
    private final WriteAheadLog wal;
    private final TtlManager ttlManager;

    public StorageEngine(Path walPath) {
        this.store = new ConcurrentHashMap<>();
        this.rwLock = new ReentrantReadWriteLock();
        try {
            this.wal = new WriteAheadLog(walPath);
            Map<String, DataEntry> recovered = wal.replay();
            store.putAll(recovered);
        } catch (IOException e) {
            throw new KronoException("Failed to initialize WAL at " + walPath, e);
        }

        this.ttlManager = new TtlManager(store, key -> {
            try {
                wal.append("DEL", key, "", -1);
            } catch (IOException e) {
                System.err.println("Failed to log TTL expiration for key: " + key);
            }
        });
        this.ttlManager.start();
    }

    public void set(String key, String value, long ttlSeconds) {
        rwLock.writeLock().lock();
        try {
            DataEntry entry = (ttlSeconds <= 0 && ttlSeconds != -1)
                ? DataEntry.of(key, value)
                : (ttlSeconds == -1 ? DataEntry.of(key, value) : DataEntry.of(key, value, ttlSeconds));
            try {
                wal.append("SET", key, value, entry.expiresAt());
            } catch (IOException e) {
                throw new KronoException("WAL append failed for SET " + key, e);
            }
            store.put(key, entry);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public String get(String key) {
        rwLock.readLock().lock();
        try {
            DataEntry entry = store.get(key);
            if (entry == null) {
                return null;
            }
            if (!entry.hasExpired()) {
                return entry.value();
            }
        } finally {
            rwLock.readLock().unlock();
        }

        rwLock.writeLock().lock();
        try {
            DataEntry entry = store.get(key);
            if (entry != null && entry.hasExpired()) {
                store.remove(key);
                try {
                    wal.append("DEL", key, "", -1);
                } catch (IOException e) {
                    System.err.println("Failed to log passive expiration for key: " + key);
                }
            }
            return null;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public boolean delete(String key) {
        rwLock.writeLock().lock();
        try {
            DataEntry removed = store.remove(key);
            if (removed != null) {
                try {
                    wal.append("DEL", key, "", -1);
                } catch (IOException e) {
                    throw new KronoException("WAL append failed for DEL " + key, e);
                }
                return true;
            }
            return false;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public List<String> listKeys() {
        rwLock.readLock().lock();
        try {
            return store.entrySet().stream()
                    .filter(e -> !e.getValue().hasExpired())
                    .map(Map.Entry::getKey)
                    .sorted()
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void compact() {
        rwLock.writeLock().lock();
        try {
            store.entrySet().removeIf(e -> e.getValue().hasExpired());
            try {
                wal.compact(store);
            } catch (IOException e) {
                throw new KronoException("WAL compaction failed", e);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public String stats() {
        rwLock.readLock().lock();
        try {
            long activeKeys = store.values().stream().filter(e -> !e.hasExpired()).count();
            long walSize;
            try {
                walSize = wal.fileSize();
            } catch (IOException e) {
                walSize = -1;
            }
            return String.format("Keys: %d | WAL size: %d bytes", activeKeys, walSize);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        if (ttlManager != null) {
            ttlManager.stop();
        }
        if (wal != null) {
            try {
                wal.close();
            } catch (IOException e) {
                System.err.println("Error closing WAL: " + e.getMessage());
            }
        }
    }
}

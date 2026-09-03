package com.kronokv.storage;

import com.kronokv.exception.CorruptedLogException;
import com.kronokv.model.DataEntry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class WriteAheadLog implements AutoCloseable {
    private final Path walPath;
    private final ReentrantLock lock = new ReentrantLock();
    private FileChannel fileChannel;

    public WriteAheadLog(Path walPath) throws IOException {
        this.walPath = walPath;
        if (walPath.getParent() != null && !Files.exists(walPath.getParent())) {
            Files.createDirectories(walPath.getParent());
        }
        if (!Files.exists(walPath)) {
            Files.createFile(walPath);
        }
        openChannel();
    }

    private void openChannel() throws IOException {
        this.fileChannel = FileChannel.open(walPath, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    public void append(String op, String key, String value, long expiresAt) throws IOException {
        lock.lock();
        try {
            String escapedKey = escape(key);
            String escapedValue = escape(value);
            String line = String.format("%s|%s|%s|%d\n", op, escapedKey, escapedValue, expiresAt);
            ByteBuffer buffer = ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8));
            while (buffer.hasRemaining()) {
                fileChannel.write(buffer);
            }
            fileChannel.force(false);
        } finally {
            lock.unlock();
        }
    }

    public Map<String, DataEntry> replay() throws IOException {
        Map<String, DataEntry> state = new HashMap<>();
        if (!Files.exists(walPath)) {
            return state;
        }

        List<String> lines = Files.readAllLines(walPath, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            try {
                String[] parts = splitUnescapedPipe(line);
                if (parts.length != 4) {
                    throw new CorruptedLogException("Invalid field count in WAL line");
                }
                String op = parts[0];
                String key = unescape(parts[1]);
                String value = unescape(parts[2]);
                long expiresAt = Long.parseLong(parts[3]);

                if ("SET".equals(op)) {
                    DataEntry entry = new DataEntry(key, value, expiresAt);
                    if (!entry.hasExpired()) {
                        state.put(key, entry);
                    } else {
                        state.remove(key);
                    }
                } else if ("DEL".equals(op)) {
                    state.remove(key);
                }
            } catch (CorruptedLogException | NumberFormatException e) {
                System.err.println("Skipping corrupted WAL line: " + line + " (" + e.getMessage() + ")");
            }
        }
        return state;
    }

    public void compact(Map<String, DataEntry> activeState) throws IOException {
        lock.lock();
        try {
            Path tmpPath = walPath.resolveSibling(walPath.getFileName() + ".tmp");
            try (FileChannel tmpChannel = FileChannel.open(tmpPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (Map.Entry<String, DataEntry> entry : activeState.entrySet()) {
                    DataEntry data = entry.getValue();
                    if (!data.hasExpired()) {
                        String escapedKey = escape(entry.getKey());
                        String escapedValue = escape(data.value());
                        String line = String.format("SET|%s|%s|%d\n", escapedKey, escapedValue, data.expiresAt());
                        ByteBuffer buffer = ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8));
                        while (buffer.hasRemaining()) {
                            tmpChannel.write(buffer);
                        }
                    }
                }
                tmpChannel.force(false);
            }

            fileChannel.close();

            try {
                Files.move(tmpPath, walPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpPath, walPath, StandardCopyOption.REPLACE_EXISTING);
            }

            openChannel();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            if (fileChannel != null && fileChannel.isOpen()) {
                fileChannel.close();
            }
        } finally {
            lock.unlock();
        }
    }

    public long fileSize() throws IOException {
        return Files.size(walPath);
    }

    private String escape(String str) {
        if (str == null) return "null";
        return str.replace("\\", "\\\\").replace("|", "\\|");
    }

    private String unescape(String str) {
        if ("null".equals(str)) return null;
        return str.replace("\\|", "|").replace("\\\\", "\\");
    }

    private String[] splitUnescapedPipe(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                currentPart.append('\\').append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '|') {
                parts.add(currentPart.toString());
                currentPart.setLength(0);
            } else {
                currentPart.append(c);
            }
        }
        if (escaped) {
            currentPart.append('\\');
        }
        parts.add(currentPart.toString());
        return parts.toArray(new String[0]);
    }
}

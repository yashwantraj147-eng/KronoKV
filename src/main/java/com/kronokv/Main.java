package com.kronokv;

import com.kronokv.engine.StorageEngine;
import com.kronokv.parser.CommandParser;
import com.kronokv.model.Command;
import com.kronokv.exception.InvalidCommandException;
import com.kronokv.exception.KronoException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        String walPathStr = System.getProperty("kronokv.wal.path", "kronokv.wal");
        Path walPath = Paths.get(walPathStr);

        boolean isInteractive = System.console() != null;

        StorageEngine engine = null;
        try {
            engine = new StorageEngine(walPath);
        } catch (Exception e) {
            System.err.println("Failed to start StorageEngine: " + e.getMessage());
            System.exit(1);
        }

        final StorageEngine finalEngine = engine;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                finalEngine.close();
            } catch (Exception e) {
                System.err.println("Error closing StorageEngine: " + e.getMessage());
            }
        }));

        if (isInteractive) {
            System.out.println("╔══════════════════════════════════════╗");
            System.out.println("║         KronoKV v1.0.0              ║");
            System.out.println("║   In-Memory Key-Value Store + WAL   ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.println("Type 'HELP' for available commands.");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                if (isInteractive) {
                    System.out.print("kronokv> ");
                }
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    Command command = CommandParser.parse(line);
                    
                    if (command.type() == Command.CommandType.EXIT) {
                        break;
                    }
                    
                    executeCommand(finalEngine, command);
                } catch (InvalidCommandException e) {
                    System.err.println("Error: " + e.getMessage());
                } catch (KronoException e) {
                    System.err.println("Error: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Unexpected error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("I/O error reading input: " + e.getMessage());
        } finally {
            try {
                finalEngine.close();
            } catch (Exception e) {
                System.err.println("Error closing StorageEngine: " + e.getMessage());
            }
        }
    }

    private static void executeCommand(StorageEngine engine, Command command) {
        switch (command.type()) {
            case SET:
                engine.set(command.key(), command.value(), command.ttl());
                System.out.println("OK");
                break;
            case GET:
                String value = engine.get(command.key());
                System.out.println(value != null ? value : "(nil)");
                break;
            case DEL:
                boolean deleted = engine.delete(command.key());
                System.out.println(deleted ? "(deleted)" : "(not found)");
                break;
            case KEYS:
                List<String> keys = engine.listKeys();
                if (keys.isEmpty()) {
                    System.out.println("(empty)");
                } else {
                    for (int i = 0; i < keys.size(); i++) {
                        System.out.println((i + 1) + ") " + keys.get(i));
                    }
                }
                break;
            case COMPACT:
                engine.compact();
                System.out.println("Compaction complete.");
                break;
            case STATS:
                System.out.println(engine.stats());
                break;
            case HELP:
                printHelp();
                break;
            default:
                break;
        }
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  SET <key> <value> [ttl]  - Set a key-value pair, optionally with TTL in seconds");
        System.out.println("  GET <key>                - Get the value of a key");
        System.out.println("  DEL <key>                - Delete a key");
        System.out.println("  KEYS                     - List all keys");
        System.out.println("  COMPACT                  - Compact the Write-Ahead Log");
        System.out.println("  STATS                    - Show store statistics");
        System.out.println("  HELP                     - Show this help message");
        System.out.println("  EXIT                     - Exit the CLI");
    }
}

package com.kronokv.model;

public record Command(CommandType type, String key, String value, long ttl) {

    public enum CommandType {
        SET, GET, DEL, KEYS, COMPACT, STATS, HELP, EXIT
    }

    public static Command of(CommandType type) {
        return new Command(type, null, null, -1);
    }

    public static Command of(CommandType type, String key) {
        return new Command(type, key, null, -1);
    }

    public static Command of(CommandType type, String key, String value, long ttl) {
        return new Command(type, key, value, ttl);
    }
}

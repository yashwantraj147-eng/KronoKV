package com.kronokv.parser;

import com.kronokv.model.Command;
import com.kronokv.model.Command.CommandType;
import com.kronokv.exception.InvalidCommandException;

import java.util.ArrayList;
import java.util.List;

public class CommandParser {

    public static Command parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new InvalidCommandException("Command cannot be empty");
        }

        List<String> tokens = tokenize(input.trim());
        if (tokens.isEmpty()) {
            throw new InvalidCommandException("Command cannot be empty");
        }

        String commandName = tokens.get(0).toUpperCase();
        CommandType type;
        try {
            type = CommandType.valueOf(commandName);
        } catch (IllegalArgumentException e) {
            throw new InvalidCommandException("Unknown command: " + commandName);
        }

        switch (type) {
            case SET:
                if (tokens.size() != 3 && tokens.size() != 4) {
                    throw new InvalidCommandException("SET requires 2 or 3 arguments: key, value, [optional TTL in seconds]");
                }
                String setKey = tokens.get(1);
                String setValue = tokens.get(2);
                long ttl = -1;
                if (tokens.size() == 4) {
                    try {
                        ttl = Long.parseLong(tokens.get(3));
                        if (ttl <= 0) {
                            throw new InvalidCommandException("TTL must be a positive integer");
                        }
                    } catch (NumberFormatException e) {
                        throw new InvalidCommandException("TTL must be a positive integer");
                    }
                }
                return new Command(type, setKey, setValue, ttl);

            case GET:
                if (tokens.size() != 2) {
                    throw new InvalidCommandException("GET requires exactly 1 argument: key");
                }
                return new Command(type, tokens.get(1), null, -1);

            case DEL:
                if (tokens.size() != 2) {
                    throw new InvalidCommandException("DEL requires exactly 1 argument: key");
                }
                return new Command(type, tokens.get(1), null, -1);

            case KEYS:
            case COMPACT:
            case STATS:
            case HELP:
            case EXIT:
                if (tokens.size() != 1) {
                    throw new InvalidCommandException(type.name() + " requires 0 arguments");
                }
                return new Command(type, null, null, -1);

            default:
                throw new InvalidCommandException("Unknown command: " + commandName);
        }
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (Character.isWhitespace(c) && !inSingleQuotes && !inDoubleQuotes) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else {
                currentToken.append(c);
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        if (inSingleQuotes || inDoubleQuotes) {
            throw new InvalidCommandException("Unclosed string literal");
        }

        return tokens;
    }
}

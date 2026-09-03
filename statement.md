# KronoKV: A Lightweight Embedded Key-Value Store

## Problem Statement
Basically, while standard tools like Redis or Memcached are great, they can be a bit overkill for small applications. Setting up and maintaining a separate server process just for caching or simple key-value storage adds unnecessary operational overhead and network latency. I wanted to build an embedded key-value store that runs directly in the application's memory space, removing that network barrier completely. The goal was to build something fast, reliable, and easy to drop into any Java project without pulling in a ton of third-party dependencies.

## Project Scope
KronoKV is an in-memory key-value store written in Java 17. The main features I focused on are:
- **Write-ahead logging (WAL):** To make sure we don't lose data on a crash, all writes are appended to a WAL on disk before being applied to memory. I also added WAL compaction to keep the log file size manageable over time, which meant handling some interesting edge cases.
- **TTL-based key expiration:** Keys can be set to expire automatically. I implemented this using a hybrid approach: passive expiration (checking if a key is expired right when it's read) and an active background sweeper thread that cleans up stale data so memory doesn't just fill up silently.
- **Concurrency:** The whole thing is thread-safe. I used `ConcurrentHashMap` for the core storage and a `ReentrantReadWriteLock` for handling thread synchronization around the WAL and storage engine. 
- **CLI REPL:** There's a command-line interface that supports an interactive REPL and piped stdin, so you can interact with it easily from the terminal or bash scripts.
- **Zero dependencies:** Everything is built using standard Java 17 libraries. No external runtime dependencies.

## Target Users
This project is mostly targeted at developers who need a quick, embedded caching layer or temporary data store inside a Java application. It's perfect for local development, small-to-medium single-node applications, or situations where deploying a full Redis instance is just too much hassle. It's also a great learning resource for students wanting to understand how database internals like WALs and TTL managers actually work under the hood.

## High-Level Architecture
The system is broken down into a few distinct components:
- **StorageEngine:** This is the core. It holds the `ConcurrentHashMap` and handles the actual read/write operations.
- **WriteAheadLog:** Handles appending operations to disk for durability. The StorageEngine talks to this before confirming a write.
- **TtlManager:** This runs as a daemon thread in the background. It periodically scans the StorageEngine to evict expired keys.
- **CommandParser:** Takes string inputs from the user (or stdin) and translates them into actual commands for the StorageEngine.
- **Main (REPL):** The entry point that glues it all together and provides the interactive terminal interface.

Basically, when a write comes in through the REPL, the CommandParser figures out what to do. The StorageEngine then takes a lock, writes to the WAL, updates the in-memory map, and releases the lock. Meanwhile, the TtlManager just sits in the background cleaning up. Turns out, this clean separation of concerns makes it pretty easy to test and extend.

# KronoKV

Hey! This is my submission for the Java programming course project. 
Basically, KronoKV is an in-memory key-value store. I wrote it in Java 17 and added a couple of cool features: write-ahead logging (WAL) so we don't lose data if it crashes, and TTL (time-to-live) so keys can expire on their own. Turns out keeping it simple is best, so there are zero runtime dependencies outside of standard Java. 

## What you need to run this
Before you start, make sure you have:
- **Java 17 or higher** (I built this on 17, so earlier versions might complain about records)
- **Maven 3.8+** (for building the project)

## How to build it
The idea here is to just use Maven to pull everything together. Open your terminal, go to the `KronoKV` folder, and run:

```bash
mvn clean package
```
This will compile the code, run the tests, and spit out an executable JAR file in the `target/` directory.

## How to run it
You can run this project in two ways. 

**1. Interactive Mode (REPL)**
If you just want to type commands and see what happens, start the interactive loop:
```bash
java -jar target/kronokv-1.0.0.jar
```
You'll see a prompt where you can start typing commands.

**2. Piped Input**
If you want to feed it a file or a quick string from the terminal, you can pipe it in:
```bash
echo "SET hello world" | java -jar target/kronokv-1.0.0.jar
```

## Available Commands
So, once you have it running, here are the commands you can use:

| Command | What it does | Example |
|---|---|---|
| `SET key value [ttl]` | Saves a value to a key. TTL is optional (in seconds). | `SET mykey "hello there" 60` |
| `GET key` | Gets the value for a key. | `GET mykey` |
| `DEL key` | Deletes a key. | `DEL mykey` |
| `KEYS` | Lists all the keys currently in memory. | `KEYS` |
| `COMPACT` | Manually triggers the WAL to compact (cleans up old log entries). | `COMPACT` |
| `STATS` | Shows some basic stats about the store. | `STATS` |
| `HELP` | Prints out command help. | `HELP` |
| `EXIT` | Quits the program. | `EXIT` |

*Note: For strings with spaces, just wrap them in quotes like the example above.*

## Running Tests
I wrote some tests for the storage engine and the write-ahead log. You can run them separately using Maven:
```bash
mvn clean test
```
There are 19 tests in total (12 for the StorageEngine and 7 for the WriteAheadLog).

## Project Structure
Here's how I organized the code. I tried to keep the engine logic separate from the storage and parsing stuff.

```text
KronoKV/
├── pom.xml
├── README.md
├── statement.md
├── PROJECT_REPORT.md
└── src/
    ├── main/java/com/kronokv/
    │   ├── Main.java              (CLI entry point, REPL loop)
    │   ├── model/
    │   │   ├── DataEntry.java     (record: key, value, expiresAt)
    │   │   └── Command.java       (CommandType enum + Command record)
    │   ├── engine/
    │   │   ├── StorageEngine.java  (ConcurrentHashMap + RWLock, coordinates WAL)
    │   │   └── TtlManager.java    (background daemon, sweeps expired keys)
    │   ├── storage/
    │   │   └── WriteAheadLog.java  (append-only NIO FileChannel, replay, compact)
    │   ├── parser/
    │   │   └── CommandParser.java  (tokenizer with quoted string support)
    │   └── exception/
    │       ├── KronoException.java
    │       ├── CorruptedLogException.java
    │       └── InvalidCommandException.java
    └── test/java/com/kronokv/
        ├── StorageEngineTest.java  (12 tests)
        └── WriteAheadLogTest.java  (7 tests)
```

package com.kronokv.engine;

import com.kronokv.model.DataEntry;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class TtlManager implements AutoCloseable {
    private final ConcurrentHashMap<String, DataEntry> map;
    private final Consumer<String> onExpire;
    private final ScheduledExecutorService executorService;

    public TtlManager(ConcurrentHashMap<String, DataEntry> map, Consumer<String> onExpire) {
        this.map = map;
        this.onExpire = onExpire;
        this.executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "krono-ttl-sweeper");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public void start() {
        executorService.scheduleAtFixedRate(this::sweep, 5, 5, TimeUnit.SECONDS);
    }

    private void sweep() {
        Iterator<Map.Entry<String, DataEntry>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, DataEntry> entry = it.next();
            if (entry.getValue().hasExpired()) {
                it.remove();
                if (onExpire != null) {
                    onExpire.accept(entry.getKey());
                }
            }
        }
    }

    public void stop() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }
}

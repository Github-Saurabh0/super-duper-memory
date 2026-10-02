package com.saurabh.banking.audit;

import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuditLogger {
    private final CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();

    public void log(String event) {
        String message = "[%s] %s".formatted(LocalDateTime.now(), event);
        events.add(message);
        System.out.println(message);
    }

    public CopyOnWriteArrayList<String> getEvents() {
        return events;
    }
}

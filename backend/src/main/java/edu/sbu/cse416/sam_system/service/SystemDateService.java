package edu.sbu.cse416.sam_system.service;

import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TP: Used cursor to implement service for system date management.
 */
@Service
public class SystemDateService {
    private final AtomicReference<LocalDate> override = new AtomicReference<>(null);
    private final ZoneId zone = ZoneId.systemDefault(); 
    
    // TP: Used cursor to get current (or overridden) system date
    public LocalDate today() {
        LocalDate o = override.get();
        return (o != null) ? o : LocalDate.now(zone);
    }

    // TP: Used cursor to set an override system date
    public void set(LocalDate date) {
        override.set(date);
    }

    // TP: Used cursor to clear the override and use real date
    public void clear() {
        override.set(null);
    }

    // TP: Used cursor to check whether a date override is active
    public boolean isOverridden() {
        return override.get() != null;
    }
}
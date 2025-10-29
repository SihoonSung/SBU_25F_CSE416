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
    
    public LocalDate today() {
        LocalDate o = override.get();
        return (o != null) ? o : LocalDate.now(zone);
    }

    public void set(LocalDate date) {
        override.set(date);
    }

    public void clear() {
        override.set(null);
    }

    public boolean isOverridden() {
        return override.get() != null;
    }
}
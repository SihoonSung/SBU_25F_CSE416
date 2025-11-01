package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.service.SystemDateService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

/**
 * TP: Used cursor to implement REST controller for system date management.
 */
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/api/admin/system-date")
public class SystemDateController {

    private final SystemDateService systemDateService;

    public SystemDateController(SystemDateService systemDateService) {
        this.systemDateService = systemDateService;
    }

    @GetMapping
    // TP: Used cursor to read current (or overridden) system date
    public Map<String, Object> get() {
        return Map.of(
                "currentDate", systemDateService.today().toString(),
                "overridden", systemDateService.isOverridden()
        );
    }

    @PutMapping
    // TP: Used cursor to set an override system date
    public Map<String, Object> set(@RequestParam("date") String date) {
        LocalDate d = LocalDate.parse(date);
        systemDateService.set(d);
        return Map.of(
                "currentDate", systemDateService.today().toString(),
                "overridden", true
        );
    }

    @DeleteMapping
    // TP: Used cursor to clear system date override
    public Map<String, Object> clear() {
        systemDateService.clear();
        return Map.of(
                "currentDate", systemDateService.today().toString(),
                "overridden", false
        );
    }
}
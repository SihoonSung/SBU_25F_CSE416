package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.service.SystemDateService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/api/admin/system-date")
public class SystemDateController {

    private final SystemDateService systemDateService;

    public SystemDateController(SystemDateService systemDateService) {
        this.systemDateService = systemDateService;
    }

    @GetMapping
    public Map<String, Object> get() {
        return Map.of(
                "currentDate", systemDateService.today().toString(),
                "overridden", systemDateService.isOverridden()
        );
    }

    @PutMapping
    public Map<String, Object> set(@RequestParam("date") String date) {
        LocalDate d = LocalDate.parse(date);
        systemDateService.set(d);
        return Map.of(
                "currentDate", systemDateService.today().toString(),
                "overridden", true
        );
    }

    @DeleteMapping
    public Map<String, Object> clear() {
        systemDateService.clear();
        return Map.of(
                "currentDate", systemDateService.today().toString(),
                "overridden", false
        );
    }
}
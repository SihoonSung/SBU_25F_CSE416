package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.util.TimeConflictChecker;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple REST controller for time conflict checking
 */
@RestController
@RequestMapping("/api/time-conflict")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class TimeConflictController {
    
    /**
     * Test time conflict between two schedules
     * GET /api/time-conflict/test?schedule1=MW 05:00-06:20PM&schedule2=MW 05:30-06:50PM
     */
    @GetMapping("/test")
    public Map<String, Object> testTimeConflict(
            @RequestParam String schedule1,
            @RequestParam String schedule2) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean hasConflict = TimeConflictChecker.hasTimeConflict(schedule1, schedule2);
            
            response.put("schedule1", schedule1);
            response.put("schedule2", schedule2);
            response.put("hasConflict", hasConflict);
            response.put("message", hasConflict ? "Time conflict detected!" : "No time conflict");
            
        } catch (Exception e) {
            response.put("error", "Failed to check time conflict: " + e.getMessage());
            response.put("hasConflict", false);
        }
        
        return response;
    }
}
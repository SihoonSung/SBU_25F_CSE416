package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.RegistrationSchedule;
import edu.sbu.cse416.sam_system.service.RegistrationScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing registration schedules.
 * 
 * TP: Used Cursor to implement REST controller for registration schedules.
 */
@RestController
@RequestMapping("/api/admin/registration-schedules")
@CrossOrigin(origins = "http://localhost:5173")
public class RegistrationScheduleController {
    
    @Autowired
    private RegistrationScheduleService scheduleService;
    
    /**
     * Create a new registration schedule
     * POST /api/admin/registration-schedules
     * Body: {
     *   "semester": "Fall",
     *   "year": 2025,
     *   "classStanding": "SENIOR" (optional),
     *   "minCredits": 90 (optional),
     *   "maxCredits": null (optional),
     *   "startDate": "2025-10-15",
     *   "endDate": "2025-10-22",
     *   "createdById": 1,
     *   "description": "Senior registration period"
     * }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createSchedule(@RequestBody Map<String, Object> body) {
        try {
            String semester = body.get("semester").toString();
            Integer year = Integer.parseInt(body.get("year").toString());
            String classStanding = body.containsKey("classStanding") && body.get("classStanding") != null 
                ? body.get("classStanding").toString() : null;
            Integer minCredits = body.containsKey("minCredits") && body.get("minCredits") != null
                ? Integer.parseInt(body.get("minCredits").toString()) : null;
            Integer maxCredits = body.containsKey("maxCredits") && body.get("maxCredits") != null
                ? Integer.parseInt(body.get("maxCredits").toString()) : null;
            LocalDate startDate = body.containsKey("startDate") && body.get("startDate") != null && !body.get("startDate").toString().trim().isEmpty()
                ? LocalDate.parse(body.get("startDate").toString()) : null;
            LocalDate endDate = body.containsKey("endDate") && body.get("endDate") != null && !body.get("endDate").toString().trim().isEmpty()
                ? LocalDate.parse(body.get("endDate").toString()) : null;
            Long createdById = Long.parseLong(body.get("createdById").toString());
            String description = body.containsKey("description") && body.get("description") != null
                ? body.get("description").toString() : null;
            
            RegistrationSchedule schedule = scheduleService.createSchedule(
                semester, year, classStanding, minCredits, maxCredits,
                startDate, endDate, createdById, description);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Registration schedule created successfully");
            response.put("schedule", buildScheduleResponse(schedule));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to create schedule: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Get all schedules for a term
     * GET /api/admin/registration-schedules?semester=Fall&year=2025
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getSchedules(
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) Integer year) {
        try {
            List<RegistrationSchedule> schedules;
            if (semester != null && year != null) {
                schedules = scheduleService.getSchedulesForTerm(semester, year);
            } else {
                // If no parameters, return empty list or all schedules
                // For now, return empty list - can be extended to return all if needed
                schedules = java.util.Collections.emptyList();
            }
            
            List<Map<String, Object>> response = schedules.stream()
                .map(this::buildScheduleResponse)
                .toList();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get schedule by ID
     * GET /api/admin/registration-schedules/{scheduleId}
     */
    @GetMapping("/{scheduleId}")
    public ResponseEntity<Map<String, Object>> getScheduleById(@PathVariable Long scheduleId) {
        try {
            RegistrationSchedule schedule = scheduleService.getScheduleById(scheduleId);
            return ResponseEntity.ok(buildScheduleResponse(schedule));
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Schedule not found: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Update a schedule
     * PUT /api/admin/registration-schedules/{scheduleId}
     */
    @PutMapping("/{scheduleId}")
    public ResponseEntity<Map<String, Object>> updateSchedule(
            @PathVariable Long scheduleId,
            @RequestBody Map<String, Object> body) {
        try {
            String semester = body.get("semester").toString();
            Integer year = Integer.parseInt(body.get("year").toString());
            String classStanding = body.containsKey("classStanding") && body.get("classStanding") != null
                ? body.get("classStanding").toString() : null;
            Integer minCredits = body.containsKey("minCredits") && body.get("minCredits") != null
                ? Integer.parseInt(body.get("minCredits").toString()) : null;
            Integer maxCredits = body.containsKey("maxCredits") && body.get("maxCredits") != null
                ? Integer.parseInt(body.get("maxCredits").toString()) : null;
            LocalDate startDate = LocalDate.parse(body.get("startDate").toString());
            LocalDate endDate = LocalDate.parse(body.get("endDate").toString());
            String description = body.containsKey("description") && body.get("description") != null
                ? body.get("description").toString() : null;
            
            RegistrationSchedule schedule = scheduleService.updateSchedule(
                scheduleId, semester, year, classStanding, minCredits, maxCredits,
                startDate, endDate, description);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Schedule updated successfully");
            response.put("schedule", buildScheduleResponse(schedule));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to update schedule: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Delete a schedule
     * DELETE /api/admin/registration-schedules/{scheduleId}
     */
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Map<String, Object>> deleteSchedule(@PathVariable Long scheduleId) {
        try {
            scheduleService.deleteSchedule(scheduleId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Schedule deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to delete schedule: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Check if a student can register for a term
     * GET /api/admin/registration-schedules/check/{userId}?semester=Fall&year=2025
     */
    @GetMapping("/check/{userId}")
    public ResponseEntity<Map<String, Object>> checkRegistrationEligibility(
            @PathVariable Long userId,
            @RequestParam String semester,
            @RequestParam Integer year) {
        try {
            Map<String, Object> eligibility = scheduleService.getRegistrationEligibility(userId, semester, year);
            return ResponseEntity.ok(eligibility);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to check eligibility: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Build response map from schedule entity
     */
    private Map<String, Object> buildScheduleResponse(RegistrationSchedule schedule) {
        Map<String, Object> response = new HashMap<>();
        response.put("scheduleId", schedule.getScheduleId());
        response.put("semester", schedule.getSemester());
        response.put("year", schedule.getYear());
        response.put("classStanding", schedule.getClassStanding());
        response.put("minCredits", schedule.getMinCredits());
        response.put("maxCredits", schedule.getMaxCredits());
        response.put("startDate", schedule.getStartDate());
        response.put("endDate", schedule.getEndDate());
        response.put("description", schedule.getDescription());
        if (schedule.getCreatedBy() != null) {
            response.put("createdBy", schedule.getCreatedBy().getUserId());
            response.put("createdByName", 
                schedule.getCreatedBy().getFirstName() + " " + schedule.getCreatedBy().getLastName());
        }
        response.put("createdAt", schedule.getCreatedAt());
        return response;
    }
}


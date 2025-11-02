package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.service.DegreeRequirementStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for degree requirement status checking.
 * Allows students and advisors to view requirement fulfillment status.
 * 
 * TP: Used Cursor to implement degree requirement status API
 */
@RestController
@RequestMapping("/api/degree-requirements-status")
@CrossOrigin(origins = "http://localhost:5173")
public class DegreeRequirementStatusController {
    
    @Autowired
    private DegreeRequirementStatusService statusService;
    
    /**
     * Get degree requirement status for a student
     * GET /api/degree-requirements-status/student/{userId}
     */
    @GetMapping("/student/{userId}")
    public ResponseEntity<Map<String, Object>> getStudentRequirementStatus(@PathVariable Long userId) {
        try {
            Map<String, Object> status = statusService.getDegreeRequirementsStatus(userId);
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to get requirement status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}


package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.service.PlanValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for plan validation.
 * Only provides validation endpoint: show unsatisfied prerequisites and degree requirement final status.
 */
@RestController
@RequestMapping("/api/planner")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class PlannerController {

    @Autowired
    private PlanValidationService planValidationService;

    /**
     * Get plan validation report - shows unsatisfied prerequisites and degree requirement final status.
     * This is the "show" functionality for class schedule planning.
     * 
     * For full plan validation (validate plan function), a separate endpoint will be implemented later.
     * TP: Used cursor to implement plan validation endpoint.
     */
    @GetMapping("/{userId}/validate")
    public ResponseEntity<?> getPlanValidationReport(@PathVariable Long userId) {
        try {
            Map<String, Object> validationResult = planValidationService.getPlanValidationReport(userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "validation", validationResult
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to validate plan: " + e.getMessage()
            ));
        }
    }
}


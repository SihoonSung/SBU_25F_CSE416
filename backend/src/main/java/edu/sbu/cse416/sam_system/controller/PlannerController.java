package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.service.PlanValidationService;
import edu.sbu.cse416.sam_system.dto.PlannerDtos;
import edu.sbu.cse416.sam_system.dto.PlannerDtos.AutoPlannerRequest;
import edu.sbu.cse416.sam_system.dto.PlannerDtos.AutoPlannerResponse;
import edu.sbu.cse416.sam_system.dto.PlannerDtos.PlannerCourseRequest;
import edu.sbu.cse416.sam_system.dto.PlannerDtos.PlannerSummaryDto;
import edu.sbu.cse416.sam_system.service.PlannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @Autowired
    private PlannerService plannerService;

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

    /**
     * Get planner summary for a user.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<PlannerSummaryDto> getPlanner(@PathVariable Long userId) {
        return ResponseEntity.ok(plannerService.getPlannerSummary(userId));
    }

    /**
     * Add a course to the planner.
     */
    @PostMapping("/{userId}")
    public ResponseEntity<?> addCourse(@PathVariable Long userId,
                                       @RequestBody PlannerCourseRequest request) {
        try {
            plannerService.addCourse(userId, request);
            PlannerSummaryDto summary = plannerService.getPlannerSummary(userId);
            return ResponseEntity.ok(summary);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Remove a planned course.
     */
    @DeleteMapping("/{userId}/{planId}")
    public ResponseEntity<?> removeCourse(@PathVariable Long userId,
                                          @PathVariable Long planId) {
        try {
            plannerService.removeCourse(userId, planId);
            PlannerSummaryDto summary = plannerService.getPlannerSummary(userId);
            return ResponseEntity.ok(summary);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Generate an auto-planned course schedule for a student.
     * POST /api/planner/{userId}/auto-generate
     */
    @PostMapping("/{userId}/auto-generate")
    public ResponseEntity<?> generateAutoPlan(@PathVariable Long userId,
                                             @RequestBody AutoPlannerRequest request) {
        try {
            // Validate request
            if (request == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Request body is required"));
            }
            if (request.workloadLimits() == null || request.workloadLimits().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "workloadLimits is required"));
            }
            if (request.graduationSemester() == null || request.graduationYear() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "graduationSemester and graduationYear are required"));
            }
            
            AutoPlannerResponse response = plannerService.generateAutoPlan(userId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            System.err.println("AutoPlanner IllegalArgumentException: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("AutoPlanner Exception: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Confirm and save the auto-generated plan to the student's course plan.
     * POST /api/planner/{userId}/confirm
     * Body: List<PlannerCourseDto> (the proposedPlan from AutoPlannerResponse)
     */
    @PostMapping("/{userId}/confirm")
    public ResponseEntity<?> confirmAutoPlan(@PathVariable Long userId,
                                             @RequestBody List<PlannerDtos.PlannerCourseDto> planCourses) {
        try {
            if (planCourses == null || planCourses.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Plan courses are required"));
            }
            
            plannerService.saveAutoPlan(userId, planCourses);
            PlannerSummaryDto summary = plannerService.getPlannerSummary(userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Plan confirmed and saved successfully",
                    "summary", summary
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            System.err.println("Confirm AutoPlan Exception: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Internal server error: " + e.getMessage()
            ));
        }
    }
}


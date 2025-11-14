package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.dto.PlannerDtos.PlannerCourseRequest;
import edu.sbu.cse416.sam_system.dto.PlannerDtos.PlannerSummaryDto;
import edu.sbu.cse416.sam_system.service.PlannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the course planner feature.
 */
@RestController
@RequestMapping("/api/planner")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class PlannerController {

    @Autowired
    private PlannerService plannerService;

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
}


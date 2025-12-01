package edu.sbu.cse416.sam_system.dto;

import java.util.List;
import java.util.Map;

/**
 * DTOs used by the planner feature (course planning UI).
 */
public class PlannerDtos {

    private PlannerDtos() {
        // Utility class
    }

    public record PlannerCourseRequest(
            String department,
            Integer courseNum,
            String semester,
            Integer year
    ) { }

    public record PlannerCourseDto(
            Long id,
            String department,
            Integer courseNum,
            String title,
            String semester,
            Integer year,
            Integer credits,
            List<String> sbcs,
            List<String> unsatisfiedPrerequisites
    ) { }

    public record PlannerSummaryDto(
            List<PlannerCourseDto> plannedCourses,
            Map<String, Integer> creditsByTerm,
            int totalCredits,
            Map<String, Integer> sbcCounts,
            Map<String, Object> degreeRequirementForecast
    ) { }

    /**
     * Request DTO for auto-planner
     */
    public record AutoPlannerRequest(
            String graduationSemester,
            Integer graduationYear,
            Map<String, Double> workloadLimits, // e.g., {"Fall 2025": 17.0, "Spring 2026": 17.0}
            List<String> avoidCourses, // e.g., ["CSE 101", "MAT 123"]
            List<String> preferredCourses // e.g., ["CSE 320", "AMS 301"] - courses student wants to take
    ) {}

    /**
     * Response DTO for auto-planner
     */
    public record AutoPlannerResponse(
            List<PlannerCourseDto> proposedPlan,
            Map<String, List<PlannerCourseDto>> proposedPlanByTerm,
            Map<String, Double> creditsByTerm,
            Double totalCredits,
            Map<String, Integer> sbcCounts,
            Map<String, Object> degreeRequirementForecast,
            List<String> warnings,
            List<String> nonRequiredCourses
    ) {}
}


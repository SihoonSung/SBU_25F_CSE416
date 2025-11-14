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
            List<String> sbcs
    ) { }

    public record PlannerSummaryDto(
            List<PlannerCourseDto> plannedCourses,
            Map<String, Integer> creditsByTerm,
            int totalCredits,
            Map<String, Integer> sbcCounts,
            Map<String, Object> degreeRequirementForecast
    ) { }
}


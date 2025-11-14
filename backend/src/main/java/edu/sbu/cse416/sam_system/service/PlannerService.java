package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.dto.PlannerDtos.PlannerCourseDto;
import edu.sbu.cse416.sam_system.dto.PlannerDtos.PlannerCourseRequest;
import edu.sbu.cse416.sam_system.dto.PlannerDtos.PlannerSummaryDto;
import edu.sbu.cse416.sam_system.entity.Course;
import edu.sbu.cse416.sam_system.entity.Term;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserCoursePlan;
import edu.sbu.cse416.sam_system.repository.CourseRepository;
import edu.sbu.cse416.sam_system.repository.TermRepository;
import edu.sbu.cse416.sam_system.repository.UserCoursePlanRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service layer for course planner functionality.
 * Handles requisite validation, persistence and summary calculation.
 */
@Service
@Transactional
public class PlannerService {

    private static final Pattern COURSE_CODE_PATTERN = Pattern.compile("\\b([A-Z]{2,4})\\s+(\\d{3,4})\\b");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCoursePlanRepository userCoursePlanRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private PrerequisitesValidationService prerequisitesValidationService;

    @Autowired
    private DegreeRequirementStatusService degreeRequirementStatusService;

    /**
     * Get planner summary with aggregated info.
     */
    public PlannerSummaryDto getPlannerSummary(Long userId) {
        List<UserCoursePlan> planned = userCoursePlanRepository.findByUserId(userId);
        Map<Long, Course> courseMap = loadCoursesForPlans(planned);

        List<PlannerCourseDto> courseDtos = planned.stream()
                .map(plan -> toPlannerCourseDto(plan, courseMap.get(plan.getId())))
                .sorted(Comparator.comparing(PlannerCourseDto::semester)
                        .thenComparing(PlannerCourseDto::year)
                        .thenComparing(PlannerCourseDto::department)
                        .thenComparing(PlannerCourseDto::courseNum, Comparator.nullsLast(Integer::compareTo)))
                .toList();

        Map<String, Integer> creditsByTerm = new HashMap<>();
        int totalCredits = 0;
        Map<String, Integer> sbcCounts = new HashMap<>();

        for (PlannerCourseDto dto : courseDtos) {
            String termKey = buildTermKey(dto.semester(), dto.year());
            if (dto.credits() != null) {
                creditsByTerm.merge(termKey, dto.credits(), Integer::sum);
                totalCredits += dto.credits();
            }
            for (String sbc : dto.sbcs()) {
                sbcCounts.merge(sbc, 1, Integer::sum);
            }
        }

        Map<String, Object> degreeForecast = calculateDegreeForecast(userId, planned);

        return new PlannerSummaryDto(courseDtos, creditsByTerm, totalCredits, sbcCounts, degreeForecast);
    }

    /**
     * Add a course to the user's planner with requisite validation.
     */
    public PlannerCourseDto addCourse(Long userId, PlannerCourseRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        validateRequest(request);

        String department = request.department().trim().toUpperCase();
        Integer courseNum = request.courseNum();

        if (userCoursePlanRepository.existsByUserIdAndDepartmentAndCourseNum(userId, department, courseNum)) {
            throw new IllegalStateException("Course is already in planner for user.");
        }

        Course course = resolveCourse(department, courseNum, request.semester(), request.year())
                .orElseThrow(() -> new IllegalArgumentException("Course not found in catalog: "
                        + department + " " + courseNum));

        List<UserCoursePlan> existingPlans = userCoursePlanRepository.findByUserId(userId);

        validateRequisites(userId, request, course, existingPlans);

        UserCoursePlan newPlan = new UserCoursePlan();
        newPlan.setUser(user);
        newPlan.setDepartment(department);
        newPlan.setCourseNum(courseNum);
        newPlan.setSemester(normalizeSemester(request.semester()));
        newPlan.setYear(request.year());
        newPlan.setCredits(course.getCredits());

        UserCoursePlan saved = userCoursePlanRepository.save(newPlan);

        return toPlannerCourseDto(saved, course);
    }

    /**
     * Remove a planned course.
     */
    public void removeCourse(Long userId, Long planId) {
        UserCoursePlan plan = userCoursePlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Planner entry not found: " + planId));

        if (!Objects.equals(plan.getUser().getUserId(), userId)) {
            throw new IllegalArgumentException("Planner entry does not belong to user.");
        }

        userCoursePlanRepository.delete(plan);
    }

    // ---------- Helpers ----------

    private void validateRequest(PlannerCourseRequest request) {
        if (request.department() == null || request.department().isBlank()) {
            throw new IllegalArgumentException("Department is required.");
        }
        if (request.courseNum() == null) {
            throw new IllegalArgumentException("Course number is required.");
        }
        if (request.semester() == null || request.semester().isBlank()) {
            throw new IllegalArgumentException("Semester is required.");
        }
        if (request.year() == null) {
            throw new IllegalArgumentException("Year is required.");
        }
    }

    private Optional<Course> resolveCourse(String department, Integer courseNum, String semester, Integer year) {
        String termName = buildTermKey(semester, year);
        if (termName != null) {
            Optional<Term> termOpt = termRepository.findByName(termName);
            if (termOpt.isPresent()) {
                Optional<Course> course = courseRepository
                        .findBySubjectAndCourseNumberAndTerm(department, courseNum.toString(), termOpt.get());
                if (course.isPresent()) {
                    return course;
                }
            }
        }
        return courseRepository.findBySubjectAndCourseNumber(department, courseNum.toString());
    }

    private void validateRequisites(Long userId,
                                    PlannerCourseRequest request,
                                    Course course,
                                    List<UserCoursePlan> existingPlans) {
        String prerequisites = normalize(course.getPrerequisites());
        String antirequisites = normalize(course.getAntirequisites());
        String corequisites = normalize(course.getCorequisites());

        if (prerequisites != null && !prerequisites.equalsIgnoreCase("none")) {
            boolean ok = prerequisitesValidationService.validatePrerequisites(userId, prerequisites);
            if (!ok) {
                throw new IllegalStateException("Prerequisites not satisfied: " + prerequisites);
            }
        }

        if (antirequisites != null && !antirequisites.isBlank()) {
            boolean ok = prerequisitesValidationService.validateAntirequisites(userId, antirequisites);
            if (!ok) {
                throw new IllegalStateException("Antirequisites conflict: " + antirequisites);
            }
        }

        if (corequisites != null && !corequisites.equalsIgnoreCase("none")) {
            String semester = normalizeSemester(request.semester());
            Integer year = request.year();
            boolean ok = prerequisitesValidationService.validateCorequisites(
                    userId, corequisites, prerequisites, semester, year);
            if (!ok) {
                throw new IllegalStateException("Corequisites not satisfied: " + corequisites);
            }

            // Ensure corequisite courses already exist in planner for the same term
            Set<String> plannedCodes = existingPlans.stream()
                    .filter(plan -> Objects.equals(plan.getSemester(), semester) && Objects.equals(plan.getYear(), year))
                    .map(plan -> plan.getDepartment() + " " + plan.getCourseNum())
                    .collect(Collectors.toSet());
            // Include the course being added
            plannedCodes.add(request.department().trim().toUpperCase() + " " + request.courseNum());

            Set<String> requiredCodes = extractCourseCodes(corequisites);
            requiredCodes.remove(request.department().trim().toUpperCase() + " " + request.courseNum());

            if (!plannedCodes.containsAll(requiredCodes)) {
                throw new IllegalStateException("Corequisites must also be planned for the same term: " + requiredCodes);
            }
        }
    }

    private Map<Long, Course> loadCoursesForPlans(List<UserCoursePlan> plans) {
        Map<Long, Course> map = new HashMap<>();
        for (UserCoursePlan plan : plans) {
            resolveCourse(plan.getDepartment(), plan.getCourseNum(), plan.getSemester(), plan.getYear())
                    .ifPresent(course -> map.put(plan.getId(), course));
        }
        return map;
    }

    private PlannerCourseDto toPlannerCourseDto(UserCoursePlan plan, Course course) {
        String title = course != null ? course.getCourseName() : plan.getDepartment() + " " + plan.getCourseNum();
        List<String> sbcs = course != null ? extractSbcs(course.getSbcsSatisfied()) : List.of();
        Integer credits = plan.getCredits();
        if (credits == null && course != null) {
            credits = course.getCredits();
        }
        return new PlannerCourseDto(
                plan.getId(),
                plan.getDepartment(),
                plan.getCourseNum(),
                title,
                plan.getSemester(),
                plan.getYear(),
                credits,
                sbcs
        );
    }

    private String normalizeSemester(String semester) {
        if (semester == null) return null;
        String trimmed = semester.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1).toLowerCase();
    }

    private String buildTermKey(String semester, Integer year) {
        if (semester == null || year == null) {
            return null;
        }
        String normalized = normalizeSemester(semester);
        if (normalized == null) return null;
        return normalized + " " + year;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> extractSbcs(String sbcString) {
        if (sbcString == null || sbcString.isBlank() || "unknown".equalsIgnoreCase(sbcString)) {
            return List.of();
        }
        String cleaned = sbcString.replace("Partially fulfills:", "")
                .replace("Partially fulfills", "")
                .replace("SBC:", "");
        String[] parts = cleaned.split("[,;]");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String token = part.trim();
            if (!token.isEmpty()) {
                result.add(token.toUpperCase());
            }
        }
        return result;
    }

    private Set<String> extractCourseCodes(String requisiteText) {
        Set<String> codes = new HashSet<>();
        Matcher matcher = COURSE_CODE_PATTERN.matcher(requisiteText);
        while (matcher.find()) {
            String dept = matcher.group(1).toUpperCase();
            String num = matcher.group(2);
            codes.add(dept + " " + num);
        }
        return codes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> calculateDegreeForecast(Long userId, List<UserCoursePlan> planned) {
        try {
            return (Map<String, Object>) DegreeRequirementStatusService.class
                    .getMethod("getDegreeRequirementsStatusWithPlannedCourses", Long.class, List.class)
                    .invoke(degreeRequirementStatusService, userId, planned);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            return degreeRequirementStatusService.getDegreeRequirementsStatus(userId);
        }
    }
}


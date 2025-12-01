package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.dto.PlannerDtos.AutoPlannerRequest;
import edu.sbu.cse416.sam_system.dto.PlannerDtos.AutoPlannerResponse;
import edu.sbu.cse416.sam_system.dto.PlannerDtos.PlannerCourseDto;
import edu.sbu.cse416.sam_system.dto.PlannerDtos.PlannerCourseRequest;
import edu.sbu.cse416.sam_system.dto.PlannerDtos.PlannerSummaryDto;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.Course;
import edu.sbu.cse416.sam_system.entity.DegreeRequirement;
import edu.sbu.cse416.sam_system.entity.DegreeType;
import edu.sbu.cse416.sam_system.entity.Term;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserClasses;
import edu.sbu.cse416.sam_system.entity.UserCoursePlan;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import edu.sbu.cse416.sam_system.repository.CourseRepository;
import edu.sbu.cse416.sam_system.repository.DegreeRequirementRepository;
import edu.sbu.cse416.sam_system.repository.TermRepository;
import edu.sbu.cse416.sam_system.repository.UserClassesRepository;
import edu.sbu.cse416.sam_system.repository.UserCoursePlanRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.util.TimeConflictChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service layer for course planner functionality.
 * Handles requisite validation, persistence and summary calculation.
 * TP: Used Cursor to implement auto-planner service for generating course plans
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
    private UserClassesRepository userClassesRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private PrerequisitesValidationService prerequisitesValidationService;

    @Autowired
    private DegreeRequirementStatusService degreeRequirementStatusService;

    @Autowired
    private DegreeRequirementRepository degreeRequirementRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get planner summary with aggregated info, including unsatisfied prerequisites and degree requirement forecast.
     * 
     * 1. Shows unsatisfied prerequisites for each planned course
     * 2. Shows degree requirement final status assuming all planned courses are completed with C grade
     */
    public PlannerSummaryDto getPlannerSummary(Long userId) {
        List<UserCoursePlan> planned = userCoursePlanRepository.findByUserId(userId);
        Map<Long, Course> courseMap = loadCoursesForPlans(planned);

        // Check unsatisfied prerequisites for each planned course
        List<PlannerCourseDto> courseDtos = planned.stream()
                .map(plan -> {
                    Course course = courseMap.get(plan.getId());
                    return toPlannerCourseDto(plan, course, userId, planned);
                })
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

        // Get degree requirement forecast (assumes all planned courses completed with C grade)
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

    /**
     * Save auto-generated plan to the student's course plan.
     * This replaces all existing planned courses with the new plan.
     * Accepts PlannerCourseDto (from AutoPlannerResponse)
     */
    @Transactional
    public void saveAutoPlan(Long userId, List<PlannerCourseDto> planCourses) {
        // Get student
        User student = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + userId));

        // Delete all existing planned courses
        userCoursePlanRepository.deleteByUserId(userId);

        // Save new planned courses
        for (PlannerCourseDto dto : planCourses) {
            String department = dto.department();
            Integer courseNum = dto.courseNum();
            String semester = normalizeSemester(dto.semester());
            Integer year = dto.year();

            if (department == null || department.isBlank()) {
                throw new IllegalArgumentException("Department is required for course: " + courseNum);
            }
            if (courseNum == null) {
                throw new IllegalArgumentException("Course number is required for department: " + department);
            }
            if (semester == null || semester.isBlank()) {
                throw new IllegalArgumentException("Semester is required for course: " + department + " " + courseNum);
            }
            if (year == null) {
                throw new IllegalArgumentException("Year is required for course: " + department + " " + courseNum);
            }

            // Find course to get credits (use credits from DTO if available, otherwise from course)
            Optional<Course> courseOpt = resolveCourse(department, courseNum, semester, year);
            Integer credits = dto.credits();
            if (credits == null && courseOpt.isPresent()) {
                credits = courseOpt.get().getCredits();
            }
            if (credits == null) {
                throw new IllegalArgumentException("Course not found or credits unavailable: " + department + " " + courseNum);
            }

            // Create and save new plan entry
            UserCoursePlan newPlan = new UserCoursePlan();
            newPlan.setUser(student);
            newPlan.setDepartment(department);
            newPlan.setCourseNum(courseNum);
            newPlan.setSemester(semester);
            newPlan.setYear(year);
            newPlan.setCredits(credits);

            userCoursePlanRepository.save(newPlan);
        }
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
        return toPlannerCourseDto(plan, course, null, null);
    }

    private PlannerCourseDto toPlannerCourseDto(UserCoursePlan plan, Course course, Long userId, List<UserCoursePlan> allPlanned) {
        String title = course != null ? course.getCourseName() : plan.getDepartment() + " " + plan.getCourseNum();
        List<String> sbcs = course != null ? extractSbcs(course.getSbcsSatisfied()) : List.of();
        Integer credits = plan.getCredits();
        if (credits == null && course != null) {
            credits = course.getCredits();
        }

        // Check unsatisfied prerequisites if userId and allPlanned are provided
        List<String> unsatisfiedPrerequisites = List.of();
        if (userId != null && allPlanned != null && course != null) {
            unsatisfiedPrerequisites = checkUnsatisfiedPrerequisites(userId, course, plan, allPlanned);
        }

        return new PlannerCourseDto(
                plan.getId(),
                plan.getDepartment(),
                plan.getCourseNum(),
                title,
                plan.getSemester(),
                plan.getYear(),
                credits,
                sbcs,
                unsatisfiedPrerequisites
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

    private Map<String, Object> calculateDegreeForecast(Long userId, List<UserCoursePlan> planned) {
        // Get degree requirement status assuming all planned courses are completed with C grade
        return degreeRequirementStatusService.getDegreeRequirementsStatusWithPlannedCourses(userId, planned);
    }

    /**
     * Check unsatisfied prerequisites for a planned course.
     * Plan에 추가된 과목들도 prerequisite으로 인정합니다 (이전 term 또는 같은 term에 있으면).
     * 
     * Example: If CSE 214 is planned for Spring 2026 but CSE 114 is not planned 
     * in any prior term, returns ["CSE 114"].
     */
    private List<String> checkUnsatisfiedPrerequisites(Long userId, Course course, UserCoursePlan plan, List<UserCoursePlan> allPlanned) {
        String prerequisites = normalize(course.getPrerequisites());
        if (prerequisites == null || prerequisites.equalsIgnoreCase("none")) {
            return List.of();
        }

        List<String> unsatisfied = new ArrayList<>();
        Set<String> prereqCodes = extractCourseCodes(prerequisites);
        
        // Get user's completed and in-progress courses
        List<UserClasses> completedClasses = userClassesRepository.findCompletedClassesByUserId(userId);
        List<UserClasses> allUserClasses = userClassesRepository.findByUserId(userId);
        List<UserClasses> inProgressClasses = allUserClasses.stream()
                .filter(uc -> uc.getGrade() == null || uc.getGrade().trim().isEmpty() || uc.getGrade().equals("I"))
                .collect(Collectors.toList());

        Set<String> completedCourseCodes = completedClasses.stream()
                .map(uc -> uc.getDepartment() + " " + uc.getCourseNum())
                .collect(Collectors.toSet());
        Set<String> inProgressCourseCodes = inProgressClasses.stream()
                .map(uc -> uc.getDepartment() + " " + uc.getCourseNum())
                .collect(Collectors.toSet());

        // Check each prerequisite
        for (String prereqCode : prereqCodes) {
            // Check if already completed or in-progress
            if (completedCourseCodes.contains(prereqCode) || inProgressCourseCodes.contains(prereqCode)) {
                continue;
            }

            // Check if in plan (before or same term)
            boolean foundInPlan = false;
            for (UserCoursePlan planned : allPlanned) {
                String plannedCode = planned.getDepartment() + " " + planned.getCourseNum();
                if (plannedCode.equals(prereqCode)) {
                    // Check if planned course is before or in same term
                    if (isBeforeOrSameTerm(planned.getSemester(), planned.getYear(), plan.getSemester(), plan.getYear())) {
                        foundInPlan = true;
                        break;
                    }
                }
            }

            if (!foundInPlan) {
                unsatisfied.add(prereqCode);
            }
        }

        return unsatisfied;
    }

    /**
     * Check if term1 is before or same as term2.
     */
    private boolean isBeforeOrSameTerm(String sem1, Integer year1, String sem2, Integer year2) {
        if (year1 < year2) return true;
        if (year1 > year2) return false;
        // Same year, check semester
        if (Objects.equals(sem1, sem2)) return true;
        String[] order = {"Fall", "Spring", "Summer"};
        int index1 = Arrays.asList(order).indexOf(sem1);
        int index2 = Arrays.asList(order).indexOf(sem2);
        if (index1 == -1) index1 = 999;
        if (index2 == -1) index2 = 999;
        return index1 <= index2;
    }

    /**
     * Generate an auto-planned course schedule for a student.
     * 핵심: 하나의 리스트(allAvailableClasses)에 completed + inProgress + planned 과목들을 모아서 prerequisite 체크
     * 
     * @param userId Student user ID
     * @param request Auto-planner request with graduation info and workload limits
     * @return AutoPlannerResponse with proposed plan
     */
    public AutoPlannerResponse generateAutoPlan(Long userId, AutoPlannerRequest request) {
        // Get student
        User student = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + userId));

        // Get all courses from catalog
        List<Course> allCourses = courseRepository.findAll();

        // Get student's completed and in-progress classes
        List<UserClasses> allUserClasses = userClassesRepository.findByUserId(userId);
        List<UserClasses> completedClasses = allUserClasses.stream()
                .filter(uc -> uc.getGrade() != null && !uc.getGrade().trim().isEmpty() && !uc.getGrade().equals("I"))
                .collect(Collectors.toList());
        List<UserClasses> inProgressClasses = allUserClasses.stream()
                .filter(uc -> uc.getGrade() == null || uc.getGrade().trim().isEmpty() || uc.getGrade().equals("I"))
                .collect(Collectors.toList());

        // Core: Collect all courses in a single list
        // This list is continuously updated (whenever a course is added to the plan)
        List<UserClasses> allAvailableClasses = new ArrayList<>();
        allAvailableClasses.addAll(completedClasses);
        allAvailableClasses.addAll(inProgressClasses);

        // Build available terms
        List<TermInfo> availableTerms = buildAvailableTerms(request.workloadLimits(), 
                                                           request.graduationSemester(), 
                                                           request.graduationYear());

        // Proposed plan
        List<PlannerCourseDto> proposedPlan = new ArrayList<>();
        Map<String, Double> creditsByTerm = new HashMap<>();
        Map<String, Integer> majorCoursesByTerm = new HashMap<>(); // Track major courses per term (max 3)
        Set<String> processedCourses = new HashSet<>(); // Track courses being processed to avoid duplicates
        List<String> warnings = new ArrayList<>();

        // Get total credits required and degree credits required from degree requirements
        int totalCreditsRequired = getTotalCreditsRequired(student);
        int degreeCreditsRequired = getDegreeCreditsRequired(student);
        
        // Get student's major subject codes (for calculating major course credits)
        Set<String> majorSubjectCodes = getMajorSubjectCodes(student);

        // Get all required courses from degree requirements (for 400-level restriction)
        Set<String> requiredCourseCodes = getAllRequiredCourseCodes(student);
        
        // Calculate current credits (completed + inProgress)
        double completedCredits = completedClasses.stream()
                .mapToDouble(uc -> uc.getCredits() != null ? uc.getCredits() : 0.0)
                .sum();
        double inProgressCredits = inProgressClasses.stream()
                .mapToDouble(uc -> uc.getCredits() != null ? uc.getCredits() : 0.0)
                .sum();
        double currentTotalCredits = completedCredits + inProgressCredits;
        
        // Calculate current major credits (completed + inProgress)
        double currentMajorCredits = calculateMajorCredits(completedClasses, inProgressClasses, majorSubjectCodes);

        // Iterative planning: repeatedly try required → electives → SBCs
        // This allows electives or SBCs to be added, satisfying prerequisites,
        // enabling more required courses to be added in the next iteration
        int maxGlobalIterations = 10; // Maximum number of iterations
        for (int globalIteration = 0; globalIteration < maxGlobalIterations; globalIteration++) {
            int coursesBefore = proposedPlan.size();
            
            // 1. Add required courses (from degree requirements)
            addRequiredCourses(student, allCourses, availableTerms, proposedPlan, creditsByTerm,
                              majorCoursesByTerm, allAvailableClasses, completedClasses, inProgressClasses,
                              warnings, request.workloadLimits(), request.avoidCourses(), processedCourses,
                              requiredCourseCodes, request.graduationSemester(), request.graduationYear());

            // 2. Add preferred courses (courses student wants to take)
            if (request.preferredCourses() != null && !request.preferredCourses().isEmpty()) {
                addPreferredCourses(student, allCourses, availableTerms, proposedPlan, creditsByTerm,
                                  majorCoursesByTerm, allAvailableClasses, completedClasses, inProgressClasses,
                                  warnings, request.workloadLimits(), request.avoidCourses(), processedCourses,
                                  requiredCourseCodes, request.preferredCourses(), request.graduationSemester(), request.graduationYear());
            }

            // 3. Add electives (from degree requirements)
            addElectives(student, allCourses, availableTerms, proposedPlan, creditsByTerm,
                        majorCoursesByTerm, allAvailableClasses, completedClasses, inProgressClasses,
                        warnings, request.workloadLimits(), request.avoidCourses(), processedCourses,
                        requiredCourseCodes, request.graduationSemester(), request.graduationYear());

            // 4. Add courses to satisfy SBC requirements
            addSBCsAndCredits(student, allCourses, availableTerms, proposedPlan, creditsByTerm,
                             majorCoursesByTerm, allAvailableClasses, completedClasses, inProgressClasses,
                             warnings, request.workloadLimits(), request.avoidCourses(), totalCreditsRequired,
                             degreeCreditsRequired, currentTotalCredits, currentMajorCredits, majorSubjectCodes,
                             processedCourses, requiredCourseCodes, request.graduationSemester(), request.graduationYear());
            
            int coursesAfter = proposedPlan.size();
            int addedThisIteration = coursesAfter - coursesBefore;
            
            // Stop if no courses were added in this iteration
            if (addedThisIteration == 0) {
                break;
            }
            
            // Stop if credits or degree requirements are satisfied
            double currentTotal = currentTotalCredits + creditsByTerm.values().stream().mapToDouble(Double::doubleValue).sum();
            if (currentTotal >= totalCreditsRequired) {
                break;
            }
            
            if (areDegreeRequirementsSatisfied(student, proposedPlan, completedClasses, inProgressClasses)) {
                break;
            }
        }

        // Group proposed plan by term
        Map<String, List<PlannerCourseDto>> proposedPlanByTerm = new HashMap<>();
        for (PlannerCourseDto course : proposedPlan) {
            String termKey = buildTermKey(course.semester(), course.year());
            proposedPlanByTerm.computeIfAbsent(termKey, k -> new ArrayList<>()).add(course);
        }

        // Sort courses within each term
        for (List<PlannerCourseDto> termCourses : proposedPlanByTerm.values()) {
            termCourses.sort(Comparator.comparing(PlannerCourseDto::department)
                    .thenComparing(PlannerCourseDto::courseNum, Comparator.nullsLast(Integer::compareTo)));
        }

        // Calculate total credits
        double totalCredits = creditsByTerm.values().stream().mapToDouble(Double::doubleValue).sum();

        // Calculate SBC counts
        Map<String, Integer> sbcCounts = calculateSBCCounts(proposedPlan);

        // Convert proposedPlan to UserCoursePlan list for degree requirement forecast
        List<UserCoursePlan> plannedCourses = proposedPlan.stream()
                .map(course -> {
                    UserCoursePlan plan = new UserCoursePlan();
                    plan.setUser(student);
                    plan.setDepartment(course.department());
                    plan.setCourseNum(course.courseNum());
                    plan.setSemester(course.semester());
                    plan.setYear(course.year());
                    plan.setCredits(course.credits());
                    return plan;
                })
                .collect(Collectors.toList());

        // Get degree requirement forecast including planned courses
        Map<String, Object> degreeRequirementForecast = degreeRequirementStatusService
                .getDegreeRequirementsStatusWithPlannedCourses(userId, plannedCourses);

        // Identify non-required courses
        List<String> nonRequiredCourses = identifyNonRequiredCourses(proposedPlan, student);

        return new AutoPlannerResponse(
                proposedPlan,
                proposedPlanByTerm,
                creditsByTerm,
                totalCredits,
                sbcCounts,
                degreeRequirementForecast,
                warnings,
                nonRequiredCourses
        );
    }

    /**
     * Build list of available terms from workload limits
     */
    private List<TermInfo> buildAvailableTerms(Map<String, Double> workloadLimits, 
                                              String graduationSemester, 
                                              Integer graduationYear) {
        List<TermInfo> terms = new ArrayList<>();
        
        // Parse term keys and sort chronologically
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(workloadLimits.entrySet());
        sortedEntries.sort((e1, e2) -> {
            String[] parts1 = e1.getKey().split("\\s+");
            String[] parts2 = e2.getKey().split("\\s+");
            if (parts1.length < 2 || parts2.length < 2) return 0;
            
            int year1 = Integer.parseInt(parts1[1]);
            int year2 = Integer.parseInt(parts2[1]);
            if (year1 != year2) return Integer.compare(year1, year2);
            
            // Spring < Summer < Fall
            int sem1 = getSemesterOrder(parts1[0]);
            int sem2 = getSemesterOrder(parts2[0]);
            return Integer.compare(sem1, sem2);
        });
        
        for (Map.Entry<String, Double> entry : sortedEntries) {
            String[] parts = entry.getKey().split("\\s+");
            if (parts.length >= 2) {
                String semester = parts[0];
                Integer year = Integer.parseInt(parts[1]);
                terms.add(new TermInfo(semester, year, entry.getValue()));
            }
        }
        
        return terms;
    }

    private int getSemesterOrder(String semester) {
        return switch (semester.toLowerCase()) {
            case "spring" -> 1;
            case "summer" -> 2;
            case "fall" -> 3;
            default -> 0;
        };
    }

    /**
     * Get total credits required from student's declared majors
     */
    private int getTotalCreditsRequired(User student) {
        int maxCredits = 120; // Default if no major found or parsing fails

        try {
            List<Map<String, Object>> declaredMajors = parseMajorsOrMinors(
                    student.getMajors(), student.getDegrees(), student.getMajorRequirementVersions());

            for (Map<String, Object> major : declaredMajors) {
                String subjectCode = (String) major.get("subjectCode");
                String degreeTypeStr = (String) major.get("degreeType");

                if (subjectCode != null && degreeTypeStr != null) {
                    try {
                        DegreeType degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
                        Optional<DegreeRequirement> reqOpt = degreeRequirementRepository
                                .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);
                        if (reqOpt.isPresent()) {
                            Integer credits = reqOpt.get().getTotalCreditsRequired();
                            if (credits != null && credits > maxCredits) {
                                maxCredits = credits;
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        // Invalid DegreeType, skip
                    }
                }
            }
        } catch (Exception e) {
            // Error getting total credits required, skip
        }
        
        return maxCredits;
    }

    /**
     * Get degree credits required (major course credits) from student's declared majors
     * This is parsed from YAML course_requirements JSON
     */
    @SuppressWarnings("unchecked")
    private int getDegreeCreditsRequired(User student) {
        int maxCredits = 0; // Default if no major found

        try {
            List<Map<String, Object>> declaredMajors = parseMajorsOrMinors(
                    student.getMajors(), student.getDegrees(), student.getMajorRequirementVersions());

            for (Map<String, Object> major : declaredMajors) {
                String subjectCode = (String) major.get("subjectCode");
                String degreeTypeStr = (String) major.get("degreeType");

                if (subjectCode == null || degreeTypeStr == null) continue;

                try {
                    DegreeType degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
                    Optional<DegreeRequirement> reqOpt = degreeRequirementRepository
                            .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);
                    if (reqOpt.isEmpty()) continue;

                    DegreeRequirement requirement = reqOpt.get();
                    String courseReqsJson = requirement.getCourseRequirements();
                    if (courseReqsJson == null || courseReqsJson.trim().isEmpty()) continue;

                    // Try to read from YAML file directly
                    // Since DB doesn't store degree_credits_required, we need to parse from YAML
                    try {
                        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                        java.io.InputStream inputStream = getClass().getClassLoader()
                                .getResourceAsStream("course_yaml/degree_requirements.yaml");
                        if (inputStream != null) {
                            Map<String, Object> yamlData = yaml.load(inputStream);
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> degreeReqs = (List<Map<String, Object>>) yamlData.get("degree_requirements");
                            if (degreeReqs != null) {
                                for (Map<String, Object> req : degreeReqs) {
                                    String reqSubjectCode = (String) req.get("subject_code");
                                    if (subjectCode.equals(reqSubjectCode)) {
                                        @SuppressWarnings("unchecked")
                                        List<Map<String, Object>> entries = (List<Map<String, Object>>) req.get("entries");
                                        if (entries != null) {
                                            for (Map<String, Object> entry : entries) {
                                                String entryDegreeType = (String) entry.get("degree_type");
                                                if (degreeTypeStr.equals(entryDegreeType)) {
                                                    Integer degreeCredits = (Integer) entry.get("degree_credits_required");
                                                    if (degreeCredits != null) {
                                                        maxCredits = Math.max(maxCredits, degreeCredits);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            inputStream.close();
                        }
                    } catch (Exception e) {
                        // If YAML parsing fails, use estimate
                        if ("CSE".equals(subjectCode) && "BS".equals(degreeTypeStr)) {
                            maxCredits = Math.max(maxCredits, 80);
                        } else {
                            int totalCredits = requirement.getTotalCreditsRequired();
                            maxCredits = Math.max(maxCredits, (int)(totalCredits * 0.67)); // Rough estimate
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Invalid degree type, skip
                } catch (Exception e) {
                    // Error getting degree credits required, skip
                }
            }
        } catch (Exception e) {
            // Error getting degree credits required, skip
        }
        
        return maxCredits > 0 ? maxCredits : 60; // Default to 60 if not found
    }

    /**
     * Get major subject codes from student's declared majors
     * Note: Auto-planner only uses declared majors, not planned majors
     */
    private Set<String> getMajorSubjectCodes(User student) {
        Set<String> subjectCodes = new HashSet<>();
        
        try {
            // Only parse declared majors (not planned majors)
            List<Map<String, Object>> declaredMajors = parseMajorsOrMinors(
                    student.getMajors(), student.getDegrees(), student.getMajorRequirementVersions());
            
            for (Map<String, Object> major : declaredMajors) {
                String subjectCode = (String) major.get("subjectCode");
                if (subjectCode != null) {
                    subjectCodes.add(subjectCode);
                }
            }
        } catch (Exception e) {
            // Error getting major subject codes, skip
        }
        
        return subjectCodes;
    }

    /**
     * Calculate major credits from completed and in-progress classes
     */
    private double calculateMajorCredits(List<UserClasses> completedClasses,
                                        List<UserClasses> inProgressClasses,
                                        Set<String> majorSubjectCodes) {
        double credits = 0;
        
        for (UserClasses uc : completedClasses) {
            if (uc.getDepartment() != null && majorSubjectCodes.contains(uc.getDepartment())) {
                credits += uc.getCredits() != null ? uc.getCredits() : 0;
            }
        }
        
        for (UserClasses uc : inProgressClasses) {
            if (uc.getDepartment() != null && majorSubjectCodes.contains(uc.getDepartment())) {
                credits += uc.getCredits() != null ? uc.getCredits() : 0;
            }
        }
        
        return credits;
    }

    /**
     * Parse majors or minors JSON from User entity
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseMajorsOrMinors(String subjectsJson, String degreesJson, String versionsJson) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (subjectsJson == null || subjectsJson.trim().isEmpty()) {
            return result;
        }

        try {
            List<?> subjects = objectMapper.readValue(subjectsJson, List.class);
            List<?> degrees = null;

            if ("MINOR".equals(degreesJson)) {
                for (int i = 0; i < subjects.size(); i++) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("subjectCode", subjects.get(i).toString());
                    item.put("degreeType", "MINOR");
                    result.add(item);
                }
                return result;
            }

            degrees = (degreesJson != null && !degreesJson.trim().isEmpty())
                    ? objectMapper.readValue(degreesJson, List.class) : null;

            for (int i = 0; i < subjects.size(); i++) {
                Map<String, Object> item = new HashMap<>();
                item.put("subjectCode", subjects.get(i).toString());
                if (degrees != null && i < degrees.size()) {
                    item.put("degreeType", degrees.get(i).toString());
                }
                result.add(item);
            }
        } catch (Exception e) {
            // Error parsing majors/minors JSON, skip
        }
        return result;
    }

    /**
     * Calculate SBC counts from proposed plan
     */
    private Map<String, Integer> calculateSBCCounts(List<PlannerCourseDto> proposedPlan) {
        Map<String, Integer> counts = new HashMap<>();

        for (PlannerCourseDto course : proposedPlan) {
            for (String sbc : course.sbcs()) {
                String cleanSBC = sbc.replace("Partially fulfills:", "").trim();
                if (!cleanSBC.isEmpty() && !cleanSBC.equals("unknown")) {
                    counts.put(cleanSBC, counts.getOrDefault(cleanSBC, 0) + 1);
                }
            }
        }

        return counts;
    }

    /**
     * Get all required course codes from degree requirements
     */
    @SuppressWarnings("unchecked")
    private Set<String> getAllRequiredCourseCodes(User student) {
        Set<String> requiredCodes = new HashSet<>();
        
        try {
            List<Map<String, Object>> declaredMajors = parseMajorsOrMinors(
                    student.getMajors(), student.getDegrees(), student.getMajorRequirementVersions());
            
            for (Map<String, Object> major : declaredMajors) {
                String subjectCode = (String) major.get("subjectCode");
                String degreeTypeStr = (String) major.get("degreeType");
                
                if (subjectCode == null || degreeTypeStr == null) continue;
                
                try {
                    DegreeType degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
                    Optional<DegreeRequirement> requirementOpt = degreeRequirementRepository
                            .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);
                    
                    if (requirementOpt.isEmpty()) continue;
                    
                    DegreeRequirement requirement = requirementOpt.get();
                    String courseReqsJson = requirement.getCourseRequirements();
                    if (courseReqsJson == null || courseReqsJson.trim().isEmpty()) continue;
                    
                    Map<String, Object> courseReqs = objectMapper.readValue(courseReqsJson, Map.class);
                    List<Map<String, Object>> groups = (List<Map<String, Object>>) courseReqs.get("groups");
                    
                    if (groups == null) continue;
                    
                    // Extract all course codes from groups
                    for (Map<String, Object> group : groups) {
                        String type = (String) group.get("type");
                        
                        // Extract from courses list
                        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
                        if (courses != null) {
                            for (Map<String, Object> courseReq : courses) {
                                String code = (String) courseReq.get("code");
                                if (code != null) {
                                    requiredCodes.add(code);
                                }
                            }
                        }
                        
                        // Extract from sequences
                        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
                        if (sequences != null) {
                            for (List<Map<String, Object>> sequence : sequences) {
                                for (Map<String, Object> courseReq : sequence) {
                                    String code = (String) courseReq.get("code");
                                    if (code != null) {
                                        requiredCodes.add(code);
                                    }
                                }
                            }
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Invalid degree type, skip
                } catch (Exception e) {
                    // Error extracting required courses, skip
                }
            }
        } catch (Exception e) {
            // Error getting required course codes, skip
        }
        
        return requiredCodes;
    }

    /**
     * Check if a 400-level course is allowed (must be in degree requirements)
     */
    private boolean is400LevelCourseAllowed(String courseCode, Set<String> requiredCourseCodes, List<String> warnings) {
        try {
            String[] parts = courseCode.split("\\s+");
            if (parts.length < 2) return true; // Can't determine, allow it
            
            String courseNum = parts[1];
            int courseNumber = Integer.parseInt(courseNum);
            
            // If it's 400-level (400-499)
            if (courseNumber >= 400 && courseNumber < 500) {
                // Must be in required course codes
                boolean isRequired = requiredCourseCodes.contains(courseCode);
                return isRequired;
            }
            
            // Non-400-level courses are always allowed
            return true;
        } catch (NumberFormatException e) {
            // Can't parse course number, allow it
            return true;
        }
    }

    /**
     * Identify courses that don't contribute to degree requirements
     */
    private List<String> identifyNonRequiredCourses(List<PlannerCourseDto> proposedPlan, User student) {
        List<String> nonRequired = new ArrayList<>();
        
        if (proposedPlan == null || proposedPlan.isEmpty()) {
            return nonRequired;
        }
        
        // Get all courses that are needed for prerequisites
        Set<String> coursesNeededForPrerequisites = getCoursesNeededForPrerequisites(proposedPlan);
        
        // Get all courses that satisfy degree requirements
        Set<String> coursesNeededForDegreeRequirements = getCoursesNeededForDegreeRequirements(student, proposedPlan);
        
        // Check each planned course
        for (PlannerCourseDto course : proposedPlan) {
            String courseCode = course.department() + " " + course.courseNum();
            
            // Check if this course is needed for prerequisites or degree requirements
            boolean neededForPrerequisites = coursesNeededForPrerequisites.contains(courseCode);
            boolean neededForDegreeRequirements = coursesNeededForDegreeRequirements.contains(courseCode);
            
            // If not needed for either, it's a non-required class
            if (!neededForPrerequisites && !neededForDegreeRequirements) {
                nonRequired.add(courseCode);
            }
        }
        
        return nonRequired;
    }
    
    /**
     * Get all courses that are needed as prerequisites for courses in the proposed plan
     */
    private Set<String> getCoursesNeededForPrerequisites(List<PlannerCourseDto> proposedPlan) {
        Set<String> neededCourses = new HashSet<>();
        
        // Get all courses from catalog
        List<Course> allCourses = courseRepository.findAll();
        Map<String, Course> courseMap = new HashMap<>();
        for (Course c : allCourses) {
            courseMap.put(c.getSubject() + " " + c.getCourseNumber(), c);
        }
        
        // For each planned course, check what prerequisites it needs
        for (PlannerCourseDto plan : proposedPlan) {
            String courseCode = plan.department() + " " + plan.courseNum();
            Course course = courseMap.get(courseCode);
            
            if (course == null) {
                continue;
            }
            
            String prerequisites = course.getPrerequisites();
            String corequisites = course.getCorequisites();
            
            if (prerequisites != null && !prerequisites.trim().isEmpty()) {
                // Extract course codes from prerequisites
                Matcher matcher = COURSE_CODE_PATTERN.matcher(prerequisites);
                while (matcher.find()) {
                    String dept = matcher.group(1);
                    String courseNum = matcher.group(2);
                    neededCourses.add(dept + " " + courseNum);
                }
            }
            
            if (corequisites != null && !corequisites.trim().isEmpty()) {
                // Extract course codes from corequisites
                Matcher matcher = COURSE_CODE_PATTERN.matcher(corequisites);
                while (matcher.find()) {
                    String dept = matcher.group(1);
                    String courseNum = matcher.group(2);
                    neededCourses.add(dept + " " + courseNum);
                }
            }
        }
        
        return neededCourses;
    }
    
    /**
     * Get all courses that satisfy degree requirements
     */
    private Set<String> getCoursesNeededForDegreeRequirements(User student, List<PlannerCourseDto> proposedPlan) {
        Set<String> neededCourses = new HashSet<>();
        
        // Get all required courses from degree requirements
        Set<String> requiredCourseCodes = getAllRequiredCourseCodes(student);
        neededCourses.addAll(requiredCourseCodes);
        
        // Also check if courses are needed for electives or sequences in degree requirements
        // This is already covered by getAllRequiredCourseCodes, but we can add more logic if needed
        
        return neededCourses;
    }

    /**
     * Add required courses from degree requirements
     */
    @SuppressWarnings("unchecked")
    private void addRequiredCourses(User student, List<Course> allCourses, List<TermInfo> availableTerms,
                                   List<PlannerCourseDto> proposedPlan,
                                   Map<String, Double> creditsByTerm, Map<String, Integer> majorCoursesByTerm,
                                   List<UserClasses> allAvailableClasses,
                                   List<UserClasses> completedClasses, List<UserClasses> inProgressClasses,
                                   List<String> warnings, Map<String, Double> workloadLimits,
                                   List<String> avoidCourses, Set<String> processedCourses,
                                   Set<String> requiredCourseCodes,
                                   String graduationSemester, Integer graduationYear) {
        try {
            List<Map<String, Object>> declaredMajors = parseMajorsOrMinors(
                    student.getMajors(), student.getDegrees(), student.getMajorRequirementVersions());
            
            for (Map<String, Object> major : declaredMajors) {
                String subjectCode = (String) major.get("subjectCode");
                String degreeTypeStr = (String) major.get("degreeType");
                
                if (subjectCode == null || degreeTypeStr == null) continue;
                
                try {
                    DegreeType degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
                    Optional<DegreeRequirement> requirementOpt = degreeRequirementRepository
                            .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);
                    
                    if (requirementOpt.isEmpty()) continue;
                    
                    DegreeRequirement requirement = requirementOpt.get();
                    String courseReqsJson = requirement.getCourseRequirements();
                    if (courseReqsJson == null || courseReqsJson.trim().isEmpty()) continue;
                    
                    Map<String, Object> courseReqs = objectMapper.readValue(courseReqsJson, Map.class);
                    List<Map<String, Object>> groups = (List<Map<String, Object>>) courseReqs.get("groups");
                    
                    if (groups == null) continue;
                    
                    // Process non-electives groups (all_of, one_of, one_of_sequences, two_of)
                    for (Map<String, Object> group : groups) {
                        String type = (String) group.get("type");
                        if ("electives".equals(type)) continue; // Electives are handled separately
                        
                        // Extract required courses from this group
                        Set<String> requiredCourses = extractRequiredCoursesFromGroup(group, type,
                                completedClasses, inProgressClasses, allAvailableClasses);
                        
                        // Add each required course to plan (try once, repeated in global iteration)
                        for (String courseCode : requiredCourses) {
                            if (isAvoided(courseCode, avoidCourses)) {
                                continue;
                            }
                            if (isAlreadyPlannedOrCompleted(courseCode, proposedPlan, completedClasses, inProgressClasses)) {
                                continue; // Already added
                            }
                            
                            // Try to add course (will check prerequisites and find suitable term)
                            tryAddCourseWithPrerequisites(courseCode, allCourses, availableTerms, proposedPlan,
                                    creditsByTerm, majorCoursesByTerm, allAvailableClasses, completedClasses,
                                    inProgressClasses, warnings, student, workloadLimits, processedCourses,
                                    avoidCourses, requiredCourseCodes, graduationSemester, graduationYear);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Invalid degree type, skip
                } catch (Exception e) {
                    // Error processing required courses, skip
                }
            }
        } catch (Exception e) {
            // Error adding required courses, skip
        }
    }

    /**
     * Add preferred courses (courses student wants to take)
     * These are added after required courses but before electives
     */
    private void addPreferredCourses(User student, List<Course> allCourses, List<TermInfo> availableTerms,
                                    List<PlannerCourseDto> proposedPlan,
                                    Map<String, Double> creditsByTerm,
                                    Map<String, Integer> majorCoursesByTerm,
                                    List<UserClasses> allAvailableClasses,
                                    List<UserClasses> completedClasses,
                                    List<UserClasses> inProgressClasses,
                                    List<String> warnings,
                                    Map<String, Double> workloadLimits,
                                    List<String> avoidCourses,
                                    Set<String> processedCourses,
                                    Set<String> requiredCourseCodes,
                                    List<String> preferredCourses,
                                    String graduationSemester, Integer graduationYear) {
        try {
            for (String courseCode : preferredCourses) {
                // Skip if in avoid list
                if (isAvoided(courseCode, avoidCourses)) {
                    continue;
                }
                
                // Skip if already planned or completed
                if (isAlreadyPlannedOrCompleted(courseCode, proposedPlan, completedClasses, inProgressClasses)) {
                    continue; // Already added
                }
                
                // Try to add course (will check prerequisites and find suitable term)
                tryAddCourseWithPrerequisites(courseCode, allCourses, availableTerms, proposedPlan,
                        creditsByTerm, majorCoursesByTerm, allAvailableClasses, completedClasses,
                        inProgressClasses, warnings, student, workloadLimits, processedCourses, avoidCourses,
                        requiredCourseCodes, graduationSemester, graduationYear);
            }
        } catch (Exception e) {
            // Error adding preferred courses, skip
        }
    }

    /**
     * Add electives from degree requirements
     */
    @SuppressWarnings("unchecked")
    private void addElectives(User student, List<Course> allCourses, List<TermInfo> availableTerms,
                             List<PlannerCourseDto> proposedPlan,
                             Map<String, Double> creditsByTerm, Map<String, Integer> majorCoursesByTerm,
                             List<UserClasses> allAvailableClasses,
                             List<UserClasses> completedClasses, List<UserClasses> inProgressClasses,
                             List<String> warnings, Map<String, Double> workloadLimits,
                             List<String> avoidCourses, Set<String> processedCourses,
                             Set<String> requiredCourseCodes,
                             String graduationSemester, Integer graduationYear) {
        try {
            List<Map<String, Object>> declaredMajors = parseMajorsOrMinors(
                    student.getMajors(), student.getDegrees(), student.getMajorRequirementVersions());
            
            for (Map<String, Object> major : declaredMajors) {
                String subjectCode = (String) major.get("subjectCode");
                String degreeTypeStr = (String) major.get("degreeType");
                
                if (subjectCode == null || degreeTypeStr == null) continue;
                
                try {
                    DegreeType degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
                    Optional<DegreeRequirement> requirementOpt = degreeRequirementRepository
                            .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);
                    
                    if (requirementOpt.isEmpty()) continue;
                    
                    DegreeRequirement requirement = requirementOpt.get();
                    String courseReqsJson = requirement.getCourseRequirements();
                    if (courseReqsJson == null || courseReqsJson.trim().isEmpty()) continue;
                    
                    Map<String, Object> courseReqs = objectMapper.readValue(courseReqsJson, Map.class);
                    List<Map<String, Object>> groups = (List<Map<String, Object>>) courseReqs.get("groups");
                    
                    if (groups == null) continue;
                    
                    // Process electives groups
                    for (Map<String, Object> group : groups) {
                        String type = (String) group.get("type");
                        if (!"electives".equals(type)) continue;
                        
                        Integer creditsRequired = (Integer) group.get("credits_required");
                        if (creditsRequired == null) continue;
                        
                        // Calculate current credits for this electives group
                        double currentCredits = calculateElectivesCredits(group, proposedPlan,
                                completedClasses, inProgressClasses);
                        double needed = Math.max(0, creditsRequired - currentCredits);
                        
                        if (needed <= 0) continue; // Already satisfied
                        
                        // Get excluded courses
                        Set<String> excludedCodes = getExcludedCourses(group);
                        
                        // Try to add courses from sequences first
                        addElectivesFromSequences(group, allCourses, availableTerms, proposedPlan,
                                creditsByTerm, majorCoursesByTerm, allAvailableClasses, completedClasses,
                                inProgressClasses, warnings, student, workloadLimits, avoidCourses,
                                processedCourses, needed, excludedCodes, requiredCourseCodes,
                                graduationSemester, graduationYear);
                        
                        // Try to add courses from courses list (including patterns like "CSE 3xx+")
                        addElectivesFromCoursesList(group, allCourses, availableTerms, proposedPlan,
                                creditsByTerm, majorCoursesByTerm, allAvailableClasses, completedClasses,
                                inProgressClasses, warnings, student, workloadLimits, avoidCourses,
                                processedCourses, needed, excludedCodes, requiredCourseCodes,
                                graduationSemester, graduationYear);
                    }
                } catch (IllegalArgumentException e) {
                    // Invalid degree type, skip
                } catch (Exception e) {
                    // Error processing electives, skip
                }
            }
        } catch (Exception e) {
            // Error adding electives, skip
        }
    }

    /**
     * Add courses to fill SBCs and total credits
     */
    private void addSBCsAndCredits(User student, List<Course> allCourses, List<TermInfo> availableTerms,
                                   List<PlannerCourseDto> proposedPlan,
                                   Map<String, Double> creditsByTerm, Map<String, Integer> majorCoursesByTerm,
                                   List<UserClasses> allAvailableClasses,
                                   List<UserClasses> completedClasses, List<UserClasses> inProgressClasses,
                                   List<String> warnings, Map<String, Double> workloadLimits,
                                   List<String> avoidCourses, int totalCreditsRequired, int degreeCreditsRequired,
                                   double currentTotalCredits, double currentMajorCredits, Set<String> majorSubjectCodes,
                                   Set<String> processedCourses, Set<String> requiredCourseCodes,
                                   String graduationSemester, Integer graduationYear) {
        // Calculate current total credits (completed + inProgress + planned)
        double plannedCredits = creditsByTerm.values().stream().mapToDouble(Double::doubleValue).sum();
        double totalCredits = currentTotalCredits + plannedCredits;
        double creditsNeeded = Math.max(0, totalCreditsRequired - totalCredits);
        
        // Calculate current major credits (completed + inProgress + planned)
        double plannedMajorCredits = calculatePlannedMajorCredits(proposedPlan, majorSubjectCodes);
        double totalMajorCredits = currentMajorCredits + plannedMajorCredits;
        double majorCreditsNeeded = Math.max(0, degreeCreditsRequired - totalMajorCredits);
        
        // Get SBC requirements from degree requirements
        Map<String, Integer> sbcRequirements = getSBCRequirements(student);
        
        // Calculate current SBC counts (including completed + inProgress)
        Map<String, Integer> currentSBCCounts = calculateSBCCounts(proposedPlan);
        // Add SBCs from completed and in-progress classes
        addSBCCountsFromUserClasses(currentSBCCounts, completedClasses, allCourses);
        addSBCCountsFromUserClasses(currentSBCCounts, inProgressClasses, allCourses);
        
        // Add courses to fill SBCs and credits
        // Priority: courses that satisfy unmet SBCs, then courses with no prerequisites, then by course number
        List<Course> candidateCourses = allCourses.stream()
                .filter(c -> !isAvoided(c.getSubject() + " " + c.getCourseNumber(), avoidCourses))
                .filter(c -> !isAlreadyPlannedOrCompleted(c.getSubject() + " " + c.getCourseNumber(),
                        proposedPlan, completedClasses, inProgressClasses))
                .filter(c -> is400LevelCourseAllowed(c.getSubject() + " " + c.getCourseNumber(), requiredCourseCodes, warnings))
                .sorted((c1, c2) -> {
                    // First, prefer courses that satisfy unmet SBCs
                    int sbcScore1 = calculateSBCScore(c1, sbcRequirements, currentSBCCounts);
                    int sbcScore2 = calculateSBCScore(c2, sbcRequirements, currentSBCCounts);
                    if (sbcScore1 != sbcScore2) return Integer.compare(sbcScore2, sbcScore1);
                    
                    // Then, prefer courses with no prerequisites
                    boolean noPrereq1 = c1.getPrerequisites() == null || c1.getPrerequisites().trim().isEmpty()
                            || c1.getPrerequisites().equalsIgnoreCase("none");
                    boolean noPrereq2 = c2.getPrerequisites() == null || c2.getPrerequisites().trim().isEmpty()
                            || c2.getPrerequisites().equalsIgnoreCase("none");
                    if (noPrereq1 && !noPrereq2) return -1;
                    if (!noPrereq1 && noPrereq2) return 1;
                    
                    // Finally, sort by course number (lower first)
                    try {
                        int num1 = Integer.parseInt(c1.getCourseNumber());
                        int num2 = Integer.parseInt(c2.getCourseNumber());
                        return Integer.compare(num1, num2);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .collect(Collectors.toList());
        
        for (Course course : candidateCourses) {
            // Skip 0-credit courses
            if (course.getCredits() == null || course.getCredits() <= 0) {
                continue;
            }
            
            // Check if we've reached total credits required
            if (totalCredits >= totalCreditsRequired) {
                // Check if all requirements are satisfied
                if (areSBCsSatisfied(sbcRequirements, currentSBCCounts) && 
                    totalMajorCredits >= degreeCreditsRequired) {
                    // Check degree requirement status
                    if (areDegreeRequirementsSatisfied(student, proposedPlan, completedClasses, inProgressClasses)) {
                        break; // All requirements satisfied, stop adding courses
                    }
                }
                // If total credits are met and SBCs/major credits are met, don't add more courses
                if (areSBCsSatisfied(sbcRequirements, currentSBCCounts) && 
                    totalMajorCredits >= degreeCreditsRequired) {
                    break; // Requirements satisfied, stop
                }
            }
            
            String courseCode = course.getSubject() + " " + course.getCourseNumber();
            if (processedCourses.contains(courseCode)) continue;
            
            // Check prerequisites using allAvailableClasses
            if (!checkPrerequisitesWithAllAvailableClasses(course.getPrerequisites(), allAvailableClasses, student)) {
                continue;
            }
            
            // Check if this is a major course and if we need more major credits
            boolean isMajorCourse = majorSubjectCodes.contains(course.getSubject());
            if (isMajorCourse && totalMajorCredits >= degreeCreditsRequired) {
                // Major credits satisfied, skip major courses unless needed for other requirements
                continue;
            }
            
            // If total credits are already met, only add if needed for SBCs
            if (totalCredits >= totalCreditsRequired) {
                // Check if this course satisfies unmet SBCs
                List<String> sbcs = extractSbcs(course.getSbcsSatisfied());
                boolean satisfiesUnmetSBC = false;
                for (String sbc : sbcs) {
                    String cleanSBC = sbc.replace("Partially fulfills:", "").trim();
                    if (!cleanSBC.isEmpty() && !cleanSBC.equals("unknown")) {
                        int required = sbcRequirements.getOrDefault(cleanSBC, 0);
                        int current = currentSBCCounts.getOrDefault(cleanSBC, 0);
                        if (current < required) {
                            satisfiesUnmetSBC = true;
                            break;
                        }
                    }
                }
                if (!satisfiesUnmetSBC) {
                    continue; // Skip courses that don't satisfy unmet SBCs
                }
            }
            
            // Try to add course
            if (tryAddCourseWithPrerequisites(courseCode, allCourses, availableTerms, proposedPlan,
                    creditsByTerm, majorCoursesByTerm, allAvailableClasses, completedClasses,
                    inProgressClasses, warnings, student, workloadLimits, processedCourses, avoidCourses,
                    requiredCourseCodes, graduationSemester, graduationYear)) {
                // Update SBC counts
                List<String> sbcs = extractSbcs(course.getSbcsSatisfied());
                for (String sbc : sbcs) {
                    String cleanSBC = sbc.replace("Partially fulfills:", "").trim();
                    if (!cleanSBC.isEmpty() && !cleanSBC.equals("unknown")) {
                        currentSBCCounts.put(cleanSBC, currentSBCCounts.getOrDefault(cleanSBC, 0) + 1);
                    }
                }
                
                // Update credits
                if (course.getCredits() != null) {
                    double courseCredits = course.getCredits();
                    totalCredits += courseCredits;
                    creditsNeeded -= courseCredits;
                    
                    if (isMajorCourse) {
                        totalMajorCredits += courseCredits;
                        majorCreditsNeeded -= courseCredits;
                    }
                }
                
                // Check if all requirements are satisfied after adding this course
                if (totalCredits >= totalCreditsRequired) {
                    if (areSBCsSatisfied(sbcRequirements, currentSBCCounts) && 
                        totalMajorCredits >= degreeCreditsRequired) {
                        if (areDegreeRequirementsSatisfied(student, proposedPlan, completedClasses, inProgressClasses)) {
                            break; // All requirements satisfied, stop adding courses
                        }
                    }
                }
            }
        }
    }

    // ========== Helper methods for auto-planner ==========
    
    /**
     * Extract required courses from a requirement group
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractRequiredCoursesFromGroup(Map<String, Object> group, String type,
                                                        List<UserClasses> completedClasses,
                                                        List<UserClasses> inProgressClasses,
                                                        List<UserClasses> allAvailableClasses) {
        Set<String> requiredCourses = new LinkedHashSet<>();
        
        switch (type) {
            case "all_of":
                List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
                if (courses != null) {
                    for (Map<String, Object> courseReq : courses) {
                        String code = (String) courseReq.get("code");
                        if (code != null && !isAlreadyPlannedOrCompleted(code, new ArrayList<>(),
                                completedClasses, inProgressClasses)) {
                            requiredCourses.add(code);
                        }
                    }
                }
                break;
                
            case "one_of":
                courses = (List<Map<String, Object>>) group.get("courses");
                if (courses != null) {
                    // Check if any course is already satisfied
                    boolean anySatisfied = false;
                    for (Map<String, Object> courseReq : courses) {
                        String code = (String) courseReq.get("code");
                        if (code != null && isAlreadyPlannedOrCompleted(code, new ArrayList<>(),
                                completedClasses, inProgressClasses)) {
                            anySatisfied = true;
                            break;
                        }
                    }
                    // If none satisfied, add the first one
                    if (!anySatisfied && !courses.isEmpty()) {
                        String code = (String) courses.get(0).get("code");
                        if (code != null) {
                            requiredCourses.add(code);
                        }
                    }
                }
                break;
                
            case "one_of_sequences":
                List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
                if (sequences != null) {
                    // Find sequence with most completed courses
                    List<Map<String, Object>> bestSequence = null;
                    int maxCompleted = -1;
                    
                    for (List<Map<String, Object>> sequence : sequences) {
                        int completed = 0;
                        for (Map<String, Object> courseReq : sequence) {
                            String code = (String) courseReq.get("code");
                            if (code != null && isAlreadyPlannedOrCompleted(code, new ArrayList<>(),
                                    completedClasses, inProgressClasses)) {
                                completed++;
                            }
                        }
                        if (completed > maxCompleted) {
                            maxCompleted = completed;
                            bestSequence = sequence;
                        }
                    }
                    
                    // Add missing courses from best sequence
                    if (bestSequence != null) {
                        for (Map<String, Object> courseReq : bestSequence) {
                            String code = (String) courseReq.get("code");
                            if (code != null && !isAlreadyPlannedOrCompleted(code, new ArrayList<>(),
                                    completedClasses, inProgressClasses)) {
                                requiredCourses.add(code);
                            }
                        }
                    }
                }
                break;
                
            case "two_of":
                courses = (List<Map<String, Object>>) group.get("courses");
                if (courses != null) {
                    int satisfied = 0;
                    for (Map<String, Object> courseReq : courses) {
                        String code = (String) courseReq.get("code");
                        if (code != null && isAlreadyPlannedOrCompleted(code, new ArrayList<>(),
                                completedClasses, inProgressClasses)) {
                            satisfied++;
                        }
                    }
                    // If less than 2 satisfied, add missing ones
                    if (satisfied < 2) {
                        for (Map<String, Object> courseReq : courses) {
                            String code = (String) courseReq.get("code");
                            if (code != null && !isAlreadyPlannedOrCompleted(code, new ArrayList<>(),
                                    completedClasses, inProgressClasses)) {
                                requiredCourses.add(code);
                                if (requiredCourses.size() >= (2 - satisfied)) break;
                            }
                        }
                    }
                }
                break;
        }
        
        return requiredCourses;
    }

    /**
     * Try to add a course with its prerequisites recursively
     */
    private boolean tryAddCourseWithPrerequisites(String courseCode, List<Course> allCourses,
                                                 List<TermInfo> availableTerms,
                                                 List<PlannerCourseDto> proposedPlan,
                                                 Map<String, Double> creditsByTerm,
                                                 Map<String, Integer> majorCoursesByTerm,
                                                 List<UserClasses> allAvailableClasses,
                                                 List<UserClasses> completedClasses,
                                                 List<UserClasses> inProgressClasses,
                                                 List<String> warnings, User student,
                                                 Map<String, Double> workloadLimits,
                                                 Set<String> processedCourses,
                                                 List<String> avoidCourses,
                                                 Set<String> requiredCourseCodes,
                                                 String graduationSemester, Integer graduationYear) {
        if (processedCourses.contains(courseCode)) {
            boolean alreadyDone = isAlreadyPlannedOrCompleted(courseCode, proposedPlan, completedClasses, inProgressClasses);
            return alreadyDone;
        }
        
        processedCourses.add(courseCode);
        
        Course course = findCourseByCode(allCourses, courseCode);
        if (course == null) {
            return false;
        }
        
        // Skip 0-credit courses
        if (course.getCredits() == null || course.getCredits() <= 0) {
            return false;
        }
        
        // Check prerequisites using allAvailableClasses
        // Special case: WRT 101 can be added without prerequisites
        String prerequisites = course.getPrerequisites();
        
        if (prerequisites != null && !prerequisites.trim().isEmpty() && !prerequisites.equalsIgnoreCase("none")) {
            // Skip prerequisite check for WRT 101
            if (!"WRT 101".equals(courseCode)) {
                boolean prereqsSatisfied = checkPrerequisitesWithAllAvailableClasses(prerequisites, allAvailableClasses, student);
                
                // First, try to add missing prerequisites recursively
                if (!prereqsSatisfied) {
                    List<String> missingPrereqs = extractMissingPrerequisites(prerequisites, allAvailableClasses, requiredCourseCodes);
                    for (String prereqCode : missingPrereqs) {
                        if (isAvoided(prereqCode, avoidCourses)) {
                            continue;
                        }
                        // Recursively try to add prerequisite
                        tryAddCourseWithPrerequisites(prereqCode, allCourses, availableTerms, proposedPlan,
                                creditsByTerm, majorCoursesByTerm, allAvailableClasses, completedClasses,
                                inProgressClasses, warnings, student, workloadLimits, processedCourses, avoidCourses,
                                requiredCourseCodes, graduationSemester, graduationYear);
                    }
                    
                    // Check again after adding prerequisites
                    prereqsSatisfied = checkPrerequisitesWithAllAvailableClasses(prerequisites, allAvailableClasses, student);
                }
            }
        }
        
        // Find best term for this course
        // Note: findBestTermForCourse checks prerequisites for each term separately,
        // so if prerequisites are satisfied in a later term, it will find that term
        TermInfo bestTerm = findBestTermForCourse(course, availableTerms, proposedPlan, creditsByTerm,
                majorCoursesByTerm, allAvailableClasses, completedClasses, inProgressClasses,
                student, workloadLimits, warnings, graduationSemester, graduationYear, allCourses);
        
        if (bestTerm == null) {
            // Could not find a suitable term (prerequisites not met in any term, or workload limits)
            // This course will be skipped for now, but if prerequisites are added later,
            // it might be added in a subsequent iteration or in a different phase
            return false;
        }
        
        // Add course to plan
        boolean added = addCourseToTerm(courseCode, bestTerm, allCourses, proposedPlan, creditsByTerm,
                majorCoursesByTerm, allAvailableClasses, completedClasses, inProgressClasses,
                warnings, student, workloadLimits, requiredCourseCodes);
        
        return added;
    }

    /**
     * Add course to a specific term
     */
    private boolean addCourseToTerm(String courseCode, TermInfo term, List<Course> allCourses,
                                   List<PlannerCourseDto> proposedPlan,
                                   Map<String, Double> creditsByTerm,
                                   Map<String, Integer> majorCoursesByTerm,
                                   List<UserClasses> allAvailableClasses,
                                   List<UserClasses> completedClasses,
                                   List<UserClasses> inProgressClasses,
                                   List<String> warnings, User student,
                                   Map<String, Double> workloadLimits,
                                   Set<String> requiredCourseCodes) {
        Course course = findCourseByCode(allCourses, courseCode);
        if (course == null) return false;
        
        // Skip 0-credit courses
        if (course.getCredits() == null || course.getCredits() <= 0) {
            return false;
        }
        
        String termKey = buildTermKey(term.semester, term.year);
        
        // Check if already in plan for this term
        for (PlannerCourseDto planned : proposedPlan) {
            String plannedTermKey = buildTermKey(planned.semester(), planned.year());
            if (termKey.equals(plannedTermKey) &&
                courseCode.equals(planned.department() + " " + planned.courseNum())) {
                return true; // Already in plan for this term
            }
        }
        
        // Check workload limit
        double currentWorkload = creditsByTerm.getOrDefault(termKey, 0.0);
        double courseCredits = course.getCredits() != null ? course.getCredits().doubleValue() : 0.0;
        Double limit = workloadLimits.get(termKey);
        if (limit != null && currentWorkload + courseCredits > limit) {
            return false; // Workload limit exceeded
        }
        
        // Check major course limit
        boolean isMajorCourse = isMajorCourse(course, student);
        if (isMajorCourse) {
            int majorCount = majorCoursesByTerm.getOrDefault(termKey, 0);
            if (majorCount >= 3) {
                return false; // Major course limit reached
            }
        }
        
        // Check time conflicts with other courses in the same term
        String[] courseCodeParts = courseCode.split(" ");
        String dept = courseCodeParts[0];
        Integer courseNum = Integer.parseInt(courseCodeParts[1]);
        
        // Find ClassEntity for this course in the same term to get schedule
        List<ClassEntity> classEntities = classRepository.searchClasses(
                dept, courseNum, null, null, term.semester, term.year);
        
        if (!classEntities.isEmpty()) {
            // Use the first available class entity's schedule
            ClassEntity classEntity = classEntities.get(0);
            String newCourseSchedule = classEntity.getSchedule();
            String newCourseLecSchedule = classEntity.getLecSchedule();
            
            // Check conflicts with other courses already in plan for this term
            for (PlannerCourseDto planned : proposedPlan) {
                String plannedTermKey = buildTermKey(planned.semester(), planned.year());
                if (termKey.equals(plannedTermKey)) {
                    // Same term, check for time conflict
                    List<ClassEntity> plannedClassEntities = classRepository.searchClasses(
                            planned.department(), planned.courseNum(), null, null, 
                            planned.semester(), planned.year());
                    
                    if (!plannedClassEntities.isEmpty()) {
                        ClassEntity plannedClassEntity = plannedClassEntities.get(0);
                        String plannedSchedule = plannedClassEntity.getSchedule();
                        String plannedLecSchedule = plannedClassEntity.getLecSchedule();
                        
                        // Check main schedule conflict
                        if (newCourseSchedule != null && !newCourseSchedule.trim().isEmpty() &&
                            plannedSchedule != null && !plannedSchedule.trim().isEmpty()) {
                            if (TimeConflictChecker.hasTimeConflict(newCourseSchedule, plannedSchedule)) {
                                return false; // Time conflict detected, skip this term
                            }
                        }
                        
                        // Check lecture schedule conflict (for courses with LAB sections)
                        if (newCourseLecSchedule != null && !newCourseLecSchedule.trim().isEmpty() &&
                            plannedSchedule != null && !plannedSchedule.trim().isEmpty()) {
                            if (TimeConflictChecker.hasTimeConflict(newCourseLecSchedule, plannedSchedule)) {
                                return false; // Time conflict detected, skip this term
                            }
                        }
                        
                        if (newCourseSchedule != null && !newCourseSchedule.trim().isEmpty() &&
                            plannedLecSchedule != null && !plannedLecSchedule.trim().isEmpty()) {
                            if (TimeConflictChecker.hasTimeConflict(newCourseSchedule, plannedLecSchedule)) {
                                return false; // Time conflict detected, skip this term
                            }
                        }
                        
                        if (newCourseLecSchedule != null && !newCourseLecSchedule.trim().isEmpty() &&
                            plannedLecSchedule != null && !plannedLecSchedule.trim().isEmpty()) {
                            if (TimeConflictChecker.hasTimeConflict(newCourseLecSchedule, plannedLecSchedule)) {
                                return false; // Time conflict detected, skip this term
                            }
                        }
                    }
                }
            }
        }
        
        // Add course to plan
        PlannerCourseDto courseDto = new PlannerCourseDto(
                null,
                course.getSubject(),
                Integer.parseInt(course.getCourseNumber()),
                course.getCourseName() != null ? course.getCourseName() : "",
                term.semester,
                term.year,
                course.getCredits(),
                extractSbcs(course.getSbcsSatisfied()),
                new ArrayList<>() // unsatisfiedPrerequisites - will be calculated later
        );
        
        proposedPlan.add(courseDto);
        creditsByTerm.put(termKey, creditsByTerm.getOrDefault(termKey, 0.0) + courseDto.credits());
        if (isMajorCourse) {
            majorCoursesByTerm.put(termKey, majorCoursesByTerm.getOrDefault(termKey, 0) + 1);
        }
        
        // Core: Add courses added to plan to allAvailableClasses as well
        UserClasses synthetic = new UserClasses();
        synthetic.setUser(student);
        synthetic.setDepartment(course.getSubject());
        synthetic.setCourseNum(Integer.parseInt(course.getCourseNumber()));
        synthetic.setGrade("C"); // Assume C for planned courses
        synthetic.setCredits(course.getCredits());
        allAvailableClasses.add(synthetic);
        
        return true;
    }

    /**
     * Find best term for a course
     */
    private TermInfo findBestTermForCourse(Course course, List<TermInfo> availableTerms,
                                          List<PlannerCourseDto> proposedPlan,
                                          Map<String, Double> creditsByTerm,
                                          Map<String, Integer> majorCoursesByTerm,
                                          List<UserClasses> allAvailableClasses,
                                          List<UserClasses> completedClasses,
                                          List<UserClasses> inProgressClasses,
                                          User student, Map<String, Double> workloadLimits,
                                          List<String> warnings,
                                          String graduationSemester, Integer graduationYear,
                                          List<Course> allCourses) {
        // Sort terms by workload and major course count
        boolean isMajorCourse = isMajorCourse(course, student);
        List<TermInfo> sortedTerms = new ArrayList<>(availableTerms);
        sortedTerms.sort((t1, t2) -> {
            String key1 = buildTermKey(t1.semester, t1.year);
            String key2 = buildTermKey(t2.semester, t2.year);
            double workload1 = creditsByTerm.getOrDefault(key1, 0.0);
            double workload2 = creditsByTerm.getOrDefault(key2, 0.0);
            
            if (isMajorCourse) {
                int majorCount1 = majorCoursesByTerm.getOrDefault(key1, 0);
                int majorCount2 = majorCoursesByTerm.getOrDefault(key2, 0);
                int majorCompare = Integer.compare(majorCount1, majorCount2);
                if (majorCompare != 0) return majorCompare;
            }
            
            return Double.compare(workload1, workload2);
        });
        
        String courseCode = course.getSubject() + " " + course.getCourseNumber();
        
        // For each term, check if prerequisites are satisfied and workload allows
        for (TermInfo term : sortedTerms) {
            String termKey = buildTermKey(term.semester, term.year);
            double currentWorkload = creditsByTerm.getOrDefault(termKey, 0.0);
            double courseCredits = course.getCredits() != null ? course.getCredits().doubleValue() : 0.0;
            Double limit = workloadLimits.get(termKey);
            
            if (limit != null && currentWorkload + courseCredits > limit) {
                continue;
            }
            
            int majorCount = majorCoursesByTerm.getOrDefault(termKey, 0);
            if (isMajorCourse && majorCount >= 3) {
                continue;
            }
            
            // Check prerequisites (must be satisfied before this term)
            String prerequisites = course.getPrerequisites();
            // Special case: WRT 101 can be added without prerequisites
            if (prerequisites == null || prerequisites.trim().isEmpty() || prerequisites.equalsIgnoreCase("none") ||
                "WRT 101".equals(course.getSubject() + " " + course.getCourseNumber())) {
                // For courses with standing requirements, check if student has the required standing at this term
                if (prerequisites != null && prerequisites.toLowerCase().contains("standing")) {
                    boolean standingMet = checkStandingRequirementForTerm(prerequisites, completedClasses, inProgressClasses, 
                                                         proposedPlan, term.semester, term.year, warnings,
                                                         graduationSemester, graduationYear);
                    if (!standingMet) {
                        continue; // Standing requirement not met for this term
                    }
                }
                return term; // No prerequisites or WRT 101
            }
            
            // Check if prerequisites are satisfied using allAvailableClasses
            // Include courses from earlier terms AND the same term (only if corequisite relationship exists)
            List<UserClasses> plannedBeforeOrAtTerm = new ArrayList<>();
            
            // Get current course's corequisites
            String courseCorequisites = course.getCorequisites();
            Set<String> corequisiteCourseCodes = new HashSet<>();
            if (courseCorequisites != null && !courseCorequisites.trim().isEmpty()) {
                // Parse corequisites (semicolon-separated or comma-separated)
                String[] coreqParts = courseCorequisites.split("[;,]");
                for (String part : coreqParts) {
                    part = part.trim();
                    Matcher coreqMatcher = COURSE_CODE_PATTERN.matcher(part);
                    while (coreqMatcher.find()) {
                        String dept = coreqMatcher.group(1);
                        String courseNum = coreqMatcher.group(2);
                        corequisiteCourseCodes.add(dept + " " + courseNum);
                    }
                }
            }
            
            for (PlannerCourseDto planned : proposedPlan) {
                String plannedCourseCode = planned.department() + " " + planned.courseNum();
                
                // Always include courses from earlier terms
                if (isBeforeTerm(planned.semester(), planned.year(), term.semester, term.year)) {
                    UserClasses synthetic = new UserClasses();
                    synthetic.setUser(student);
                    synthetic.setDepartment(planned.department());
                    synthetic.setCourseNum(planned.courseNum());
                    synthetic.setGrade("C");
                    synthetic.setCredits(planned.credits());
                    plannedBeforeOrAtTerm.add(synthetic);
                }
                // For same term: only include if it's a corequisite
                else if (planned.semester().equals(term.semester) && planned.year().equals(term.year)) {
                    // Check if planned course is in current course's corequisites
                    if (corequisiteCourseCodes.contains(plannedCourseCode)) {
                        UserClasses synthetic = new UserClasses();
                        synthetic.setUser(student);
                        synthetic.setDepartment(planned.department());
                        synthetic.setCourseNum(planned.courseNum());
                        synthetic.setGrade("C");
                        synthetic.setCredits(planned.credits());
                        plannedBeforeOrAtTerm.add(synthetic);
                    } else {
                        // Also check if planned course has current course as corequisite (bidirectional check)
                        Course plannedCourseObj = findCourseByCode(allCourses, plannedCourseCode);
                        if (plannedCourseObj != null && plannedCourseObj.getCorequisites() != null) {
                            String plannedCorequisites = plannedCourseObj.getCorequisites();
                            if (plannedCorequisites.toLowerCase().contains(courseCode.toLowerCase())) {
                                UserClasses synthetic = new UserClasses();
                                synthetic.setUser(student);
                                synthetic.setDepartment(planned.department());
                                synthetic.setCourseNum(planned.courseNum());
                                synthetic.setGrade("C");
                                synthetic.setCredits(planned.credits());
                                plannedBeforeOrAtTerm.add(synthetic);
                            }
                        }
                    }
                }
            }
            
            // Combine with completed and in-progress classes
            List<UserClasses> allClassesForPrereqCheck = new ArrayList<>();
            allClassesForPrereqCheck.addAll(completedClasses);
            allClassesForPrereqCheck.addAll(inProgressClasses);
            allClassesForPrereqCheck.addAll(plannedBeforeOrAtTerm);
            
            // Check standing requirements for this term
            if (prerequisites.toLowerCase().contains("standing")) {
                boolean standingMet = checkStandingRequirementForTerm(prerequisites, completedClasses, inProgressClasses,
                                                     proposedPlan, term.semester, term.year, warnings,
                                                     graduationSemester, graduationYear);
                if (!standingMet) {
                    continue; // Standing requirement not met for this term
                }
            }
            
            // Pass term and graduation information so standing requirement can be bypassed if needed
            boolean prereqsSatisfied = checkPrerequisitesWithAllAvailableClasses(prerequisites, allClassesForPrereqCheck, student, 
                                                                                term.semester, term.year, graduationSemester, graduationYear);
            
            if (prereqsSatisfied) {
                return term;
            }
        }
        
        return null; // No suitable term found
    }

    /**
     * Check prerequisites using allAvailableClasses list
     */
    private boolean checkPrerequisitesWithAllAvailableClasses(String prerequisites, List<UserClasses> allAvailableClasses, User student) {
        return checkPrerequisitesWithAllAvailableClasses(prerequisites, allAvailableClasses, student, null, null, null, null);
    }
    
    private boolean checkPrerequisitesWithAllAvailableClasses(String prerequisites, List<UserClasses> allAvailableClasses, User student, 
                                                              String termSemester, Integer termYear, 
                                                              String graduationSemester, Integer graduationYear) {
        if (prerequisites == null || prerequisites.trim().isEmpty() || prerequisites.equalsIgnoreCase("none")) {
            return true;
        }
        
        // Use PrerequisitesValidationService logic but with our allAvailableClasses
        // Parse prerequisites string (semicolon-separated groups)
        String[] prerequisiteGroups = prerequisites.split(";");
        
        // Get student's declared majors
        Set<String> declaredMajorCodes = new HashSet<>();
        if (student != null) {
            try {
                List<Map<String, Object>> declaredMajors = parseMajorsOrMinors(
                        student.getMajors(), student.getDegrees(), student.getMajorRequirementVersions());
                for (Map<String, Object> major : declaredMajors) {
                    String subjectCode = (String) major.get("subjectCode");
                    if (subjectCode != null) {
                        declaredMajorCodes.add(subjectCode);
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        
        for (String group : prerequisiteGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;
            
            String lowerGroup = group.toLowerCase();
            
            // Check major requirement (e.g., "CSE, ISE or DAS major")
            if (lowerGroup.contains("major")) {
                // Extract major codes from the requirement
                // Pattern: "CSE, ISE or DAS major" or "CSE major" etc.
                Set<String> requiredMajors = new HashSet<>();
                if (lowerGroup.contains("cse")) requiredMajors.add("CSE");
                if (lowerGroup.contains("ise")) requiredMajors.add("ISE");
                if (lowerGroup.contains("das")) requiredMajors.add("DAS");
                // Add more majors as needed
                
                // Check if student has any of the required majors
                boolean hasRequiredMajor = false;
                for (String requiredMajor : requiredMajors) {
                    if (declaredMajorCodes.contains(requiredMajor)) {
                        hasRequiredMajor = true;
                        break;
                    }
                }
                
                if (!hasRequiredMajor && !requiredMajors.isEmpty()) {
                    return false; // Major requirement not met
                }
                continue; // Major requirement satisfied or no major requirement
            }
            
            // Check SBC requirement (e.g., "one D.E.C. E or SNW course")
            if (lowerGroup.contains("d.e.c.") || lowerGroup.contains("dec") || 
                lowerGroup.contains("snw") || lowerGroup.contains("sbc")) {
                // For now, assume SBC requirements are satisfied if student has taken courses
                // This is a simplified check - in reality, we'd need to check Course.sbcsSatisfied
                // For auto-planner purposes, we'll assume this can be satisfied by adding SBC courses
                continue; // Assume satisfied for now (will be handled by SBC planning phase)
            }
            
            // Extract course codes from group
            List<String> groupCourses = new ArrayList<>();
            Matcher matcher = COURSE_CODE_PATTERN.matcher(group);
            while (matcher.find()) {
                String dept = matcher.group(1);
                String courseNum = matcher.group(2);
                groupCourses.add(dept + " " + courseNum);
            }
            
            if (groupCourses.isEmpty()) {
                // No course codes, might be standing requirement
                if (lowerGroup.contains("standing")) {
                    // If termSemester and termYear are provided, check if this is graduation term
                    // U3 requirement: allow in graduation term or before graduation
                    // U4 requirement: allow in graduation term only
                    if (termSemester != null && termYear != null && graduationSemester != null && graduationYear != null) {
                        boolean isGraduationTerm = (termSemester.equals(graduationSemester) && termYear.equals(graduationYear));
                        boolean isBeforeGraduationTerm = isBeforeTerm(termSemester, termYear, graduationSemester, graduationYear);
                        
                        // U3 requirement: allow in graduation term or before
                        if (lowerGroup.contains("u3") || lowerGroup.contains("junior")) {
                            if (isBeforeGraduationTerm || isGraduationTerm) {
                                continue; // Bypass U3 requirement in graduation term or before
                            }
                        } else if (lowerGroup.contains("u4") || lowerGroup.contains("senior")) {
                            if (isGraduationTerm) {
                                continue; // Bypass U4 requirement in graduation term
                            }
                        }
                    }
                    
                    // If termSemester and termYear are provided, standing was already checked by checkStandingRequirementForTerm
                    // So we can skip it here to avoid double-checking
                    if (termSemester != null && termYear != null) {
                        continue; // Standing already checked separately
                    }
                    
                    // Fallback: Check standing requirement using allAvailableClasses to calculate credits
                    int totalCredits = 0;
                    for (UserClasses uc : allAvailableClasses) {
                        if (uc.getCredits() != null) {
                            totalCredits += uc.getCredits();
                        }
                    }
                    String standing = calculateStanding(totalCredits);
                    
                    if (lowerGroup.contains("u4") || lowerGroup.contains("senior")) {
                        if (!standing.equals("U4")) return false;
                    } else if (lowerGroup.contains("u3") || lowerGroup.contains("junior")) {
                        if (!standing.equals("U3") && !standing.equals("U4")) return false;
                    } else if (lowerGroup.contains("u2") || lowerGroup.contains("sophomore")) {
                        if (!standing.equals("U2") && !standing.equals("U3") && !standing.equals("U4")) return false;
                    }
                    // U1 or just "standing" without specific level - assume satisfied
                    continue;
                }
                // Other non-course requirements - assume satisfied for now
                continue;
            }
            
            // Check if "or" keyword is present
            boolean hasOrKeyword = group.toLowerCase().matches(".*\\bor\\b.*");
            
            if (hasOrKeyword) {
                // OR logic: any one course is enough
                boolean found = false;
                for (String courseCode : groupCourses) {
                    String[] parts = courseCode.split("\\s+");
                    if (parts.length >= 2) {
                        String dept = parts[0];
                        int courseNum = Integer.parseInt(parts[1]);
                        for (UserClasses uc : allAvailableClasses) {
                            if (dept.equals(uc.getDepartment()) && courseNum == uc.getCourseNum()) {
                                found = true;
                                break;
                            }
                        }
                        if (found) break;
                    }
                }
                if (!found) return false;
            } else {
                // AND logic: all courses must be present
                for (String courseCode : groupCourses) {
                    String[] parts = courseCode.split("\\s+");
                    if (parts.length >= 2) {
                        String dept = parts[0];
                        int courseNum = Integer.parseInt(parts[1]);
                        boolean found = false;
                        for (UserClasses uc : allAvailableClasses) {
                            if (dept.equals(uc.getDepartment()) && courseNum == uc.getCourseNum()) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) return false;
                    }
                }
            }
        }
        
        return true;
    }

    /**
     * Extract missing prerequisites
     */
    private List<String> extractMissingPrerequisites(String prerequisites, List<UserClasses> allAvailableClasses, Set<String> requiredCourseCodes) {
        List<String> missing = new ArrayList<>();
        
        if (prerequisites == null || prerequisites.trim().isEmpty() || prerequisites.equalsIgnoreCase("none")) {
            return missing;
        }
        
        String[] prerequisiteGroups = prerequisites.split(";");
        
        for (String group : prerequisiteGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;
            
            List<String> groupCourses = new ArrayList<>();
            Matcher matcher = COURSE_CODE_PATTERN.matcher(group);
            while (matcher.find()) {
                String dept = matcher.group(1);
                String courseNum = matcher.group(2);
                groupCourses.add(dept + " " + courseNum);
            }
            
            boolean hasOrKeyword = group.toLowerCase().matches(".*\\bor\\b.*");
            boolean groupSatisfied = false;
            List<String> missingInGroup = new ArrayList<>();
            
            for (String courseCode : groupCourses) {
                String[] parts = courseCode.split("\\s+");
                if (parts.length < 2) continue;
                String dept = parts[0];
                int courseNum = Integer.parseInt(parts[1]);
                
                boolean found = false;
                for (UserClasses uc : allAvailableClasses) {
                    if (dept.equals(uc.getDepartment()) && courseNum == uc.getCourseNum()) {
                        found = true;
                        groupSatisfied = true;
                        break;
                    }
                }
                
                if (!found) {
                    missingInGroup.add(courseCode);
                }
                
                if (groupSatisfied && hasOrKeyword) break; // OR: one is enough, stop checking
            }
            
            if (!groupSatisfied) {
                // If OR condition: add all courses that are in degree requirements
                // If none are in degree requirements, add only the first one
                // If AND condition: add all missing courses
                if (hasOrKeyword && !missingInGroup.isEmpty()) {
                    // Find all courses in degree requirements
                    List<String> requiredCourses = new ArrayList<>();
                    for (String courseCode : missingInGroup) {
                        if (requiredCourseCodes.contains(courseCode)) {
                            requiredCourses.add(courseCode);
                        }
                    }
                    // If any are in degree requirements, add all of them; otherwise add first one
                    if (!requiredCourses.isEmpty()) {
                        missing.addAll(requiredCourses);
                    } else {
                        missing.add(missingInGroup.get(0)); // Only add first missing course if none are in degree requirements
                    }
                } else {
                    missing.addAll(missingInGroup); // Add all missing courses for AND
                }
            }
        }
        
        return missing;
    }

    /**
     * Check standing requirement for a specific term
     * Calculates student's standing at that term (including all courses before/at that term)
     */
    private boolean checkStandingRequirementForTerm(String prerequisites, List<UserClasses> completedClasses,
                                                    List<UserClasses> inProgressClasses,
                                                    List<PlannerCourseDto> proposedPlan,
                                                    String termSemester, Integer termYear,
                                                    List<String> warnings,
                                                    String graduationSemester, Integer graduationYear) {
        if (prerequisites == null || !prerequisites.toLowerCase().contains("standing")) {
            return true; // No standing requirement
        }
        
        // Check if this is the graduation term or the term before graduation
        boolean isGraduationTerm = (termSemester != null && termSemester.equals(graduationSemester) && 
                                   termYear != null && termYear.equals(graduationYear));
        boolean isBeforeGraduationTerm = isBeforeTerm(termSemester, termYear, graduationSemester, graduationYear);
        
        // U3 requirement: allow in the term before graduation (last but one term)
        // U4 requirement: allow in the graduation term (last term)
        String lowerPrereq = prerequisites.toLowerCase();
        if (lowerPrereq.contains("u3") || lowerPrereq.contains("junior")) {
            if (isBeforeGraduationTerm || isGraduationTerm) {
                return true; // Allow U3 courses in graduation term or before
            }
        } else if (lowerPrereq.contains("u4") || lowerPrereq.contains("senior")) {
            if (isGraduationTerm) {
                return true; // Allow U4 courses in graduation term
            }
        }
        
        // Calculate total credits at this term (completed + inProgress + planned before/at this term)
        int totalCredits = 0;
        int completedCredits = 0;
        int inProgressCredits = 0;
        int plannedCredits = 0;
        
        // Add completed classes
        for (UserClasses uc : completedClasses) {
            if (uc.getCredits() != null) {
                completedCredits += uc.getCredits();
            }
        }
        totalCredits += completedCredits;
        
        // Add in-progress classes
        for (UserClasses uc : inProgressClasses) {
            if (uc.getCredits() != null) {
                inProgressCredits += uc.getCredits();
            }
        }
        totalCredits += inProgressCredits;
        
        // Add planned courses before or at this term
        int plannedCount = 0;
        for (PlannerCourseDto planned : proposedPlan) {
            if (isBeforeTerm(planned.semester(), planned.year(), termSemester, termYear) ||
                (planned.semester().equals(termSemester) && planned.year().equals(termYear))) {
                if (planned.credits() != null) {
                    plannedCredits += planned.credits();
                    plannedCount++;
                }
            }
        }
        totalCredits += plannedCredits;
        
        // Calculate standing
        String standing = calculateStanding(totalCredits);
        
        // Check standing requirement (lowerPrereq already declared above)
        String requiredStanding = "";
        boolean result = false;
        
        if (lowerPrereq.contains("u4") || lowerPrereq.contains("senior")) {
            requiredStanding = "U4";
            result = standing.equals("U4");
        } else if (lowerPrereq.contains("u3") || lowerPrereq.contains("junior")) {
            requiredStanding = "U3 or U4";
            result = standing.equals("U3") || standing.equals("U4");
        } else if (lowerPrereq.contains("u2") || lowerPrereq.contains("sophomore")) {
            requiredStanding = "U2 or higher";
            result = standing.equals("U2") || standing.equals("U3") || standing.equals("U4");
        } else if (lowerPrereq.contains("u1") || lowerPrereq.contains("freshman")) {
            requiredStanding = "U1 or higher";
            result = true; // U1 is the minimum, everyone qualifies
        } else {
            requiredStanding = "any";
            result = true; // Default: if just says "standing" without specific level, assume satisfied
        }
        
        return result;
    }
    
    /**
     * Calculate class standing from total credits
     * U1: 0-23, U2: 24-56, U3: 57-84, U4: 85+
     */
    private String calculateStanding(int credits) {
        if (credits < 24) return "U1";
        if (credits < 57) return "U2";
        if (credits < 85) return "U3";
        return "U4";
    }

    /**
     * Helper methods
     */
    private Course findCourseByCode(List<Course> allCourses, String courseCode) {
        String[] parts = courseCode.split("\\s+");
        if (parts.length < 2) return null;
        String dept = parts[0];
        String courseNum = parts[1];
        
        return allCourses.stream()
                .filter(c -> dept.equals(c.getSubject()) && courseNum.equals(c.getCourseNumber()))
                .findFirst()
                .orElse(null);
    }

    private boolean isAlreadyPlannedOrCompleted(String courseCode,
                                                List<PlannerCourseDto> proposedPlan,
                                                List<UserClasses> completedClasses,
                                                List<UserClasses> inProgressClasses) {
        String[] parts = courseCode.split("\\s+");
        if (parts.length < 2) return false;
        String dept = parts[0];
        int courseNum = Integer.parseInt(parts[1]);
        
        for (PlannerCourseDto planned : proposedPlan) {
            if (dept.equals(planned.department()) && courseNum == planned.courseNum()) {
                return true;
            }
        }
        
        for (UserClasses uc : completedClasses) {
            if (dept.equals(uc.getDepartment()) && courseNum == uc.getCourseNum()) {
                return true;
            }
        }
        
        for (UserClasses uc : inProgressClasses) {
            if (dept.equals(uc.getDepartment()) && courseNum == uc.getCourseNum()) {
                return true;
            }
        }
        
        return false;
    }

    private boolean isAvoided(String courseCode, List<String> avoidCourses) {
        return avoidCourses != null && avoidCourses.contains(courseCode);
    }

    private boolean isMajorCourse(Course course, User student) {
        try {
            List<Map<String, Object>> declaredMajors = parseMajorsOrMinors(
                    student.getMajors(), student.getDegrees(), student.getMajorRequirementVersions());
            
            for (Map<String, Object> major : declaredMajors) {
                String subjectCode = (String) major.get("subjectCode");
                if (subjectCode != null && subjectCode.equals(course.getSubject())) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private boolean isBeforeTerm(String sem1, Integer year1, String sem2, Integer year2) {
        if (year1 < year2) return true;
        if (year1 > year2) return false;
        int order1 = getSemesterOrder(sem1);
        int order2 = getSemesterOrder(sem2);
        return order1 < order2;
    }

    /**
     * Calculate current credits for an electives group
     */
    @SuppressWarnings("unchecked")
    private double calculateElectivesCredits(Map<String, Object> electivesGroup,
                                           List<PlannerCourseDto> proposedPlan,
                                           List<UserClasses> completedClasses,
                                           List<UserClasses> inProgressClasses) {
        double credits = 0;
        Set<String> usedCourseCodes = new HashSet<>();
        
        Set<String> excludedCodes = getExcludedCourses(electivesGroup);
        
        // Check sequences (sequence must be complete to count credits)
        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) electivesGroup.get("sequences");
        if (sequences != null) {
            for (List<Map<String, Object>> sequence : sequences) {
                // Check if sequence is complete (all courses in sequence must be present)
                boolean sequenceComplete = true;
                List<String> sequenceCourseCodes = new ArrayList<>();
                
                for (Map<String, Object> courseReq : sequence) {
                    String code = (String) courseReq.get("code");
                    if (code == null || excludedCodes.contains(code)) {
                        sequenceComplete = false;
                        break;
                    }
                    sequenceCourseCodes.add(code);
                    
                    // Check if this course is completed, in-progress, or planned
                    boolean found = false;
                    for (UserClasses uc : completedClasses) {
                        if (code.equals(uc.getDepartment() + " " + uc.getCourseNum())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        for (UserClasses uc : inProgressClasses) {
                            if (code.equals(uc.getDepartment() + " " + uc.getCourseNum())) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        for (PlannerCourseDto planned : proposedPlan) {
                            if (code.equals(planned.department() + " " + planned.courseNum())) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        sequenceComplete = false;
                        break;
                    }
                }
                
                // Only count credits if sequence is complete
                if (sequenceComplete) {
                    for (String code : sequenceCourseCodes) {
                        if (usedCourseCodes.contains(code)) continue;
                        
                        // Check if completed or in-progress
                        for (UserClasses uc : completedClasses) {
                            if (code.equals(uc.getDepartment() + " " + uc.getCourseNum())) {
                                credits += uc.getCredits() != null ? uc.getCredits() : 0;
                                usedCourseCodes.add(code);
                                break;
                            }
                        }
                        if (!usedCourseCodes.contains(code)) {
                            for (UserClasses uc : inProgressClasses) {
                                if (code.equals(uc.getDepartment() + " " + uc.getCourseNum())) {
                                    credits += uc.getCredits() != null ? uc.getCredits() : 0;
                                    usedCourseCodes.add(code);
                                    break;
                                }
                            }
                        }
                        // Check proposed plan
                        if (!usedCourseCodes.contains(code)) {
                            for (PlannerCourseDto planned : proposedPlan) {
                                if (code.equals(planned.department() + " " + planned.courseNum())) {
                                    credits += planned.credits() != null ? planned.credits() : 0;
                                    usedCourseCodes.add(code);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Check courses list
        List<Map<String, Object>> courses = (List<Map<String, Object>>) electivesGroup.get("courses");
        if (courses != null) {
            for (Map<String, Object> courseReq : courses) {
                String code = (String) courseReq.get("code");
                if (code == null || excludedCodes.contains(code)) continue;
                if (code.contains("xx+")) continue; // Patterns handled separately
                if (usedCourseCodes.contains(code)) continue;
                
                for (UserClasses uc : completedClasses) {
                    if (code.equals(uc.getDepartment() + " " + uc.getCourseNum())) {
                        credits += uc.getCredits() != null ? uc.getCredits() : 0;
                        usedCourseCodes.add(code);
                        break;
                    }
                }
                if (!usedCourseCodes.contains(code)) {
                    for (UserClasses uc : inProgressClasses) {
                        if (code.equals(uc.getDepartment() + " " + uc.getCourseNum())) {
                            credits += uc.getCredits() != null ? uc.getCredits() : 0;
                            usedCourseCodes.add(code);
                            break;
                        }
                    }
                }
                if (!usedCourseCodes.contains(code)) {
                    for (PlannerCourseDto planned : proposedPlan) {
                        if (code.equals(planned.department() + " " + planned.courseNum())) {
                            credits += planned.credits() != null ? planned.credits() : 0;
                            usedCourseCodes.add(code);
                            break;
                        }
                    }
                }
            }
        }
        
        return credits;
    }

    /**
     * Get excluded courses from electives group
     */
    @SuppressWarnings("unchecked")
    private Set<String> getExcludedCourses(Map<String, Object> electivesGroup) {
        Set<String> excludedCodes = new HashSet<>();
        List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) electivesGroup.get("excluded_courses");
        if (excludedCourses != null) {
            for (Map<String, Object> excluded : excludedCourses) {
                String code = (String) excluded.get("code");
                if (code != null) {
                    excludedCodes.add(code);
                }
            }
        }
        return excludedCodes;
    }

    /**
     * Add electives from sequences
     */
    @SuppressWarnings("unchecked")
    private void addElectivesFromSequences(Map<String, Object> electivesGroup, List<Course> allCourses,
                                          List<TermInfo> availableTerms,
                                          List<PlannerCourseDto> proposedPlan,
                                          Map<String, Double> creditsByTerm,
                                          Map<String, Integer> majorCoursesByTerm,
                                          List<UserClasses> allAvailableClasses,
                                          List<UserClasses> completedClasses,
                                          List<UserClasses> inProgressClasses,
                                          List<String> warnings, User student,
                                          Map<String, Double> workloadLimits,
                                          List<String> avoidCourses,
                                          Set<String> processedCourses,
                                          double needed, Set<String> excludedCodes,
                                          Set<String> requiredCourseCodes,
                                          String graduationSemester, Integer graduationYear) {
        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) electivesGroup.get("sequences");
        if (sequences == null || sequences.isEmpty()) return;
        
        // Prioritize sequences with already completed courses
        List<List<Map<String, Object>>> sequencesWithCompleted = new ArrayList<>();
        List<List<Map<String, Object>>> sequencesWithoutCompleted = new ArrayList<>();
        
        for (List<Map<String, Object>> sequence : sequences) {
            boolean hasCompleted = false;
            for (Map<String, Object> courseReq : sequence) {
                String code = (String) courseReq.get("code");
                if (code != null && isAlreadyPlannedOrCompleted(code, proposedPlan,
                        completedClasses, inProgressClasses)) {
                    hasCompleted = true;
                    break;
                }
            }
            if (hasCompleted) {
                sequencesWithCompleted.add(sequence);
            } else {
                sequencesWithoutCompleted.add(sequence);
            }
        }
        
        // Process sequences with completed courses first
        // Try to complete sequences that are already partially completed
        for (List<Map<String, Object>> sequence : sequencesWithCompleted) {
            if (needed <= 0) break;
            
            // Try to add all missing courses from this sequence
            boolean sequenceAdded = false;
            for (Map<String, Object> courseReq : sequence) {
                if (needed <= 0) break;
                String code = (String) courseReq.get("code");
                if (code == null || excludedCodes.contains(code)) continue;
                if (isAlreadyPlannedOrCompleted(code, proposedPlan, completedClasses, inProgressClasses)) continue;
                if (isAvoided(code, avoidCourses)) continue;
                
                if (tryAddCourseWithPrerequisites(code, allCourses, availableTerms, proposedPlan,
                        creditsByTerm, majorCoursesByTerm, allAvailableClasses, completedClasses,
                        inProgressClasses, warnings, student, workloadLimits, processedCourses, avoidCourses,
                        requiredCourseCodes, graduationSemester, graduationYear)) {
                    Course course = findCourseByCode(allCourses, code);
                    if (course != null && course.getCredits() != null) {
                        needed -= course.getCredits();
                        sequenceAdded = true;
                    }
                }
            }
            
            // If we successfully added at least one course from this sequence, continue
            // Otherwise, try next sequence
        }
        
        // Then process sequences without completed courses
        // Try to add complete sequences from scratch
        Collections.shuffle(sequencesWithoutCompleted);
        for (List<Map<String, Object>> sequence : sequencesWithoutCompleted) {
            if (needed <= 0) break;
            
            // Try to add all courses from this sequence in order
            boolean allAdded = true;
            double sequenceCredits = 0;
            for (Map<String, Object> courseReq : sequence) {
                String code = (String) courseReq.get("code");
                if (code == null || excludedCodes.contains(code)) {
                    allAdded = false;
                    break;
                }
                if (isAlreadyPlannedOrCompleted(code, proposedPlan, completedClasses, inProgressClasses)) {
                    // Already have this course, count its credits
                    Course course = findCourseByCode(allCourses, code);
                    if (course != null && course.getCredits() != null) {
                        sequenceCredits += course.getCredits();
                    }
                    continue;
                }
                if (isAvoided(code, avoidCourses)) {
                    allAdded = false;
                    break;
                }
                
                if (tryAddCourseWithPrerequisites(code, allCourses, availableTerms, proposedPlan,
                        creditsByTerm, majorCoursesByTerm, allAvailableClasses, completedClasses,
                        inProgressClasses, warnings, student, workloadLimits, processedCourses, avoidCourses,
                        requiredCourseCodes, graduationSemester, graduationYear)) {
                    Course course = findCourseByCode(allCourses, code);
                    if (course != null && course.getCredits() != null) {
                        sequenceCredits += course.getCredits();
                    }
                } else {
                    allAdded = false;
                    break; // Can't complete this sequence, try next one
                }
            }
            
            if (allAdded && sequenceCredits > 0) {
                needed -= sequenceCredits;
            }
        }
    }

    /**
     * Add electives from courses list (including patterns like "CSE 3xx+")
     */
    @SuppressWarnings("unchecked")
    private void addElectivesFromCoursesList(Map<String, Object> electivesGroup, List<Course> allCourses,
                                            List<TermInfo> availableTerms,
                                            List<PlannerCourseDto> proposedPlan,
                                            Map<String, Double> creditsByTerm,
                                            Map<String, Integer> majorCoursesByTerm,
                                            List<UserClasses> allAvailableClasses,
                                            List<UserClasses> completedClasses,
                                            List<UserClasses> inProgressClasses,
                                            List<String> warnings, User student,
                                            Map<String, Double> workloadLimits,
                                            List<String> avoidCourses,
                                            Set<String> processedCourses,
                                            double needed, Set<String> excludedCodes,
                                            Set<String> requiredCourseCodes,
                                            String graduationSemester, Integer graduationYear) {
        List<Map<String, Object>> courses = (List<Map<String, Object>>) electivesGroup.get("courses");
        if (courses == null || courses.isEmpty()) return;
        
        for (Map<String, Object> courseReq : courses) {
            if (needed <= 0) break;
            String code = (String) courseReq.get("code");
            if (code == null || excludedCodes.contains(code)) continue;
            
            // Handle patterns like "CSE 3xx+"
            if (code.contains("xx+") || code.contains("xx")) {
                String[] parts = code.split("\\s+");
                if (parts.length < 2) continue;
                String dept = parts[0];
                String pattern = parts[1];
                
                final int minLevel;
                if (pattern.contains("2xx+")) {
                    minLevel = 200;
                } else if (pattern.contains("4xx+")) {
                    minLevel = 400;
                } else {
                    minLevel = 300; // Default for 3xx+
                }
                
                // Find matching courses with prerequisites satisfied, sorted by course number
                List<Course> matchingCourses = allCourses.stream()
                        .filter(c -> dept.equals(c.getSubject()))
                        .filter(c -> {
                            try {
                                int courseNum = Integer.parseInt(c.getCourseNumber());
                                return courseNum >= minLevel;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        })
                        .filter(c -> {
                            String courseCode = c.getSubject() + " " + c.getCourseNumber();
                            return !excludedCodes.contains(courseCode);
                        })
                        .filter(c -> !isAvoided(c.getSubject() + " " + c.getCourseNumber(), avoidCourses))
                        .filter(c -> !isAlreadyPlannedOrCompleted(c.getSubject() + " " + c.getCourseNumber(),
                                proposedPlan, completedClasses, inProgressClasses))
                        .filter(c -> is400LevelCourseAllowed(c.getSubject() + " " + c.getCourseNumber(), requiredCourseCodes, warnings))
                        .filter(c -> checkPrerequisitesWithAllAvailableClasses(c.getPrerequisites(), allAvailableClasses, student))
                        .sorted((c1, c2) -> {
                            try {
                                int num1 = Integer.parseInt(c1.getCourseNumber());
                                int num2 = Integer.parseInt(c2.getCourseNumber());
                                return Integer.compare(num1, num2);
                            } catch (NumberFormatException e) {
                                return 0;
                            }
                        })
                        .collect(Collectors.toList());
                
                // Try to add matching courses
                for (Course course : matchingCourses) {
                    if (needed <= 0) break;
                    String courseCode = course.getSubject() + " " + course.getCourseNumber();
                    
                    if (tryAddCourseWithPrerequisites(courseCode, allCourses, availableTerms, proposedPlan,
                            creditsByTerm, majorCoursesByTerm, allAvailableClasses, completedClasses,
                            inProgressClasses, warnings, student, workloadLimits, processedCourses, avoidCourses,
                            requiredCourseCodes, graduationSemester, graduationYear)) {
                        if (course.getCredits() != null) {
                            needed -= course.getCredits();
                        }
                    }
                }
            } else {
                // Regular course code
                if (isAlreadyPlannedOrCompleted(code, proposedPlan, completedClasses, inProgressClasses)) continue;
                if (isAvoided(code, avoidCourses)) continue;
                
                if (tryAddCourseWithPrerequisites(code, allCourses, availableTerms, proposedPlan,
                        creditsByTerm, majorCoursesByTerm, allAvailableClasses, completedClasses,
                        inProgressClasses, warnings, student, workloadLimits, processedCourses, avoidCourses,
                        requiredCourseCodes, graduationSemester, graduationYear)) {
                    Course course = findCourseByCode(allCourses, code);
                    if (course != null && course.getCredits() != null) {
                        needed -= course.getCredits();
                    }
                }
            }
        }
    }

    /**
     * Get SBC requirements from degree requirements
     */
    @SuppressWarnings("unchecked")
    private Map<String, Integer> getSBCRequirements(User student) {
        Map<String, Integer> sbcRequirements = new HashMap<>();
        
        try {
            List<Map<String, Object>> declaredMajors = parseMajorsOrMinors(
                    student.getMajors(), student.getDegrees(), student.getMajorRequirementVersions());
            
            for (Map<String, Object> major : declaredMajors) {
                String subjectCode = (String) major.get("subjectCode");
                String degreeTypeStr = (String) major.get("degreeType");
                
                if (subjectCode == null || degreeTypeStr == null) continue;
                
                try {
                    DegreeType degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
                    Optional<DegreeRequirement> requirementOpt = degreeRequirementRepository
                            .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);
                    
                    if (requirementOpt.isEmpty()) continue;
                    
                    DegreeRequirement requirement = requirementOpt.get();
                    String sbcReqsJson = requirement.getSbcRequirements();
                    if (sbcReqsJson == null || sbcReqsJson.trim().isEmpty()) continue;
                    
                    List<Map<String, Object>> sbcReqs = objectMapper.readValue(sbcReqsJson, List.class);
                    
                    for (Map<String, Object> sbcReq : sbcReqs) {
                        String sbc = (String) sbcReq.get("sbc");
                        if (sbc != null) {
                            sbcRequirements.put(sbc, 1); // Default to 1 if not specified
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return sbcRequirements;
    }

    /**
     * Calculate SBC score for a course (higher = better)
     */
    private int calculateSBCScore(Course course, Map<String, Integer> sbcRequirements,
                                 Map<String, Integer> currentSBCCounts) {
        int score = 0;
        List<String> sbcs = extractSbcs(course.getSbcsSatisfied());
        
        for (String sbc : sbcs) {
            String cleanSBC = sbc.replace("Partially fulfills:", "").trim();
            if (cleanSBC.isEmpty() || cleanSBC.equals("unknown")) continue;
            
            if (sbcRequirements.containsKey(cleanSBC)) {
                int currentCount = currentSBCCounts.getOrDefault(cleanSBC, 0);
                int required = sbcRequirements.get(cleanSBC);
                if (currentCount < required) {
                    score += (required - currentCount) * 10; // Higher weight for unmet SBCs
                }
            }
        }
        
        return score;
    }

    /**
     * Check if all SBCs are satisfied
     */
    private boolean areSBCsSatisfied(Map<String, Integer> sbcRequirements, Map<String, Integer> currentSBCCounts) {
        for (Map.Entry<String, Integer> entry : sbcRequirements.entrySet()) {
            String sbc = entry.getKey();
            int required = entry.getValue();
            int current = currentSBCCounts.getOrDefault(sbc, 0);
            if (current < required) {
                return false;
            }
        }
        return true;
    }

    /**
     * Calculate planned major credits from proposed plan
     */
    private double calculatePlannedMajorCredits(List<PlannerCourseDto> proposedPlan, Set<String> majorSubjectCodes) {
        return proposedPlan.stream()
                .filter(course -> majorSubjectCodes.contains(course.department()))
                .mapToDouble(course -> course.credits() != null ? course.credits() : 0.0)
                .sum();
    }

    /**
     * Add SBC counts from user classes (completed or in-progress)
     */
    private void addSBCCountsFromUserClasses(Map<String, Integer> currentSBCCounts,
                                            List<UserClasses> userClasses,
                                            List<Course> allCourses) {
        for (UserClasses uc : userClasses) {
            // Find the course to get SBCs
            Course course = allCourses.stream()
                    .filter(c -> c.getSubject().equals(uc.getDepartment()) && 
                            c.getCourseNumber().equals(String.valueOf(uc.getCourseNum())))
                    .findFirst()
                    .orElse(null);
            
            if (course != null && course.getSbcsSatisfied() != null) {
                List<String> sbcs = extractSbcs(course.getSbcsSatisfied());
                for (String sbc : sbcs) {
                    String cleanSBC = sbc.replace("Partially fulfills:", "").trim();
                    if (!cleanSBC.isEmpty() && !cleanSBC.equals("unknown")) {
                        currentSBCCounts.put(cleanSBC, currentSBCCounts.getOrDefault(cleanSBC, 0) + 1);
                    }
                }
            }
        }
    }

    /**
     * Check if degree requirements are satisfied
     */
    private boolean areDegreeRequirementsSatisfied(User student,
                                                  List<PlannerCourseDto> proposedPlan,
                                                  List<UserClasses> completedClasses,
                                                  List<UserClasses> inProgressClasses) {
        try {
            // Convert proposed plan to UserCoursePlan for degree requirement check
            List<UserCoursePlan> plannedCourses = proposedPlan.stream()
                    .map(course -> {
                        UserCoursePlan plan = new UserCoursePlan();
                        plan.setUser(student);
                        plan.setDepartment(course.department());
                        plan.setCourseNum(course.courseNum());
                        plan.setSemester(course.semester());
                        plan.setYear(course.year());
                        plan.setCredits(course.credits());
                        return plan;
                    })
                    .collect(Collectors.toList());
            
            // Get degree requirement forecast
            Map<String, Object> forecast = degreeRequirementStatusService
                    .getDegreeRequirementsStatusWithPlannedCourses(student.getUserId(), plannedCourses);
            
            // Check if all declared majors are satisfied
            // Note: Auto-planner only checks declared majors/minors, not planned majors/minors
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> declaredMajors = (List<Map<String, Object>>) forecast.get("declaredMajors");
            if (declaredMajors != null) {
                for (Map<String, Object> major : declaredMajors) {
                    String overallStatus = (String) major.get("overallStatus");
                    if (overallStatus == null || !"SATISFIED".equals(overallStatus)) {
                        return false; // At least one declared major is not satisfied
                    }
                }
            }
            
            // Note: We ignore plannedMajors and plannedMinors in auto-planner
            // Only declared majors/minors are checked for graduation requirements
            
            return true;
        } catch (Exception e) {
            return false; // If error, assume not satisfied
        }
    }
    
    /**
     * Helper class to represent a term
     */
    private static class TermInfo {
        String semester;
        Integer year;
        Double workloadLimit;

        TermInfo(String semester, Integer year, Double workloadLimit) {
            this.semester = semester;
            this.year = year;
            this.workloadLimit = workloadLimit;
        }
    }
}


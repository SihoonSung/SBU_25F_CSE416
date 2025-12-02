package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.*;
import edu.sbu.cse416.sam_system.repository.*;
import edu.sbu.cse416.sam_system.entity.ClassRoster;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for validating course plans.
 * 
 * Current functionality:
 * - getPlanValidationReport(): Shows unsatisfied prerequisites and degree requirement final status
 * 
 * Future functionality:
 * - validatePlan(): Full plan validation that checks if plan is valid and lists all reasons
 * 
 * TP: Used cursor to implement plan validation service.
 */
@Service
@Transactional
public class PlanValidationService {

    private static final Pattern COURSE_PATTERN = Pattern.compile("\\b([A-Z]{2,4})\\s+(\\d{3,4})\\b");

    @Autowired
    private UserCoursePlanRepository userCoursePlanRepository;

    @Autowired
    private UserClassesRepository userClassesRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private PlannedMajorRepository plannedMajorRepository;

    @Autowired
    private DegreeRequirementRepository degreeRequirementRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PrerequisitesValidationService prerequisitesValidationService;

    @Autowired
    private ClassRosterRepository classRosterRepository;

    /**
     * Helper method to resolve course by subject and course number.
     * Handles cases where multiple courses exist (same course in different terms) by returning the most recent one.
     */
    private Optional<Course> resolveCourse(String department, String courseNumber) {
        List<Course> courses = courseRepository.findAllBySubjectAndCourseNumber(department, courseNumber);
        if (courses.isEmpty()) {
            return Optional.empty();
        }
        if (courses.size() == 1) {
            return Optional.of(courses.get(0));
        }
        // If multiple courses found, return the one with the most recent term
        return courses.stream()
                .filter(c -> c.getTerm() != null && c.getTerm().getStartDate() != null)
                .max(Comparator.comparing(c -> c.getTerm().getStartDate()))
                .map(Optional::of)
                .orElse(Optional.of(courses.get(0))); // Fallback to first if no dates available
    }

    /**
     * Get plan validation report showing unsatisfied prerequisites and degree requirement final status.
     * This is the "show" functionality - displays what issues exist and what the final status will be.
     * 
     * For full plan validation (checking if plan is valid and listing all reasons), use validatePlan() method.
     */
    public Map<String, Object> getPlanValidationReport(Long userId) {
        Map<String, Object> result = new HashMap<>();

        // Get all planned courses sorted by semester/year
        List<UserCoursePlan> plannedCourses = userCoursePlanRepository.findByUserId(userId);
        System.out.println("[DEBUG getPlanValidationReport] ===== START =====");
        System.out.println("[DEBUG getPlanValidationReport] User ID: " + userId);
        System.out.println("[DEBUG getPlanValidationReport] Querying planned courses from database...");
        System.out.println("[DEBUG getPlanValidationReport] Found " + plannedCourses.size() + " planned courses");
        
        // Also check if user exists and get SBU_ID for verification
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                System.out.println("[DEBUG getPlanValidationReport] User found - SBU_ID: " + user.getSbuId() + ", Name: " + user.getFirstName() + " " + user.getLastName());
                
                // Try to find all UserCoursePlan records for this user (alternative query)
                List<UserCoursePlan> allPlans = userCoursePlanRepository.findAll();
                long countForUser = allPlans.stream()
                    .filter(p -> p.getUser() != null && p.getUser().getUserId().equals(userId))
                    .count();
                System.out.println("[DEBUG getPlanValidationReport] Total UserCoursePlan records in DB: " + allPlans.size());
                System.out.println("[DEBUG getPlanValidationReport] UserCoursePlan records for this userId (" + userId + "): " + countForUser);
                
                // Debug: Print all UserCoursePlan records and their userIds
                System.out.println("[DEBUG getPlanValidationReport] All UserCoursePlan records in DB:");
                for (UserCoursePlan plan : allPlans) {
                    if (plan.getUser() != null) {
                        System.out.println("[DEBUG]   - UserCoursePlan ID: " + plan.getId() + 
                            ", Course: " + plan.getDepartment() + " " + plan.getCourseNum() +
                            ", UserId: " + plan.getUser().getUserId() + 
                            ", SBU_ID: " + plan.getUser().getSbuId());
                    } else {
                        System.out.println("[DEBUG]   - UserCoursePlan ID: " + plan.getId() + 
                            ", Course: " + plan.getDepartment() + " " + plan.getCourseNum() +
                            ", User: NULL");
                    }
                }
            } else {
                System.out.println("[DEBUG getPlanValidationReport] WARNING: User not found with userId: " + userId);
            }
        } catch (Exception e) {
            System.out.println("[DEBUG getPlanValidationReport] Error checking user: " + e.getMessage());
        }
        
        for (UserCoursePlan plan : plannedCourses) {
            System.out.println("[DEBUG getPlanValidationReport] Planned course: " + 
                plan.getDepartment() + " " + plan.getCourseNum() + " (" + plan.getSemester() + " " + plan.getYear() + ")");
        }
        plannedCourses.sort((a, b) -> {
            int yearCompare = a.getYear().compareTo(b.getYear());
            if (yearCompare != 0) return yearCompare;
            // Order: Spring, Summer, Fall
            int semesterOrderA = getSemesterOrder(a.getSemester());
            int semesterOrderB = getSemesterOrder(b.getSemester());
            return Integer.compare(semesterOrderA, semesterOrderB);
        });

        // Check unsatisfied prerequisites
        List<Map<String, Object>> unsatisfiedPrerequisites = checkUnsatisfiedPrerequisites(userId, plannedCourses);
        result.put("unsatisfiedPrerequisites", unsatisfiedPrerequisites);

        // Calculate degree requirement final status
        List<Map<String, Object>> degreeRequirementStatus = calculateDegreeRequirementStatus(userId, plannedCourses);
        result.put("degreeRequirementStatus", degreeRequirementStatus);

        // Identify non-required classes (classes that don't contribute to prerequisites or degree requirements)
        List<Map<String, Object>> nonRequiredClasses = identifyNonRequiredClasses(userId, plannedCourses);
        result.put("nonRequiredClasses", nonRequiredClasses);

        return result;
    }

    /**
     * Check unsatisfied prerequisites for enrolled courses only.
     * Completed courses (with grades) are NOT checked - they are already finished.
     * Planned courses are NOT checked for prerequisites.
     * 
     * Status definitions:
     * - SATISFIED: Course completed with grade >= min_grade (or C if no min_grade specified)
     * - UNSATISFIED: Course not taken, or grade < min_grade (e.g., C- when C is required)
     * - PENDING: Course is enrolled/in-progress (grade is null)
     * 
     * Note: For prerequisites, enrolled/in-progress courses are considered UNSATISFIED
     * because prerequisites must be COMPLETED (have grade) before taking the course.
     * 
     * For corequisites, enrolled/in-progress courses in the same semester are acceptable.
     * For antirequisites, enrolled/in-progress courses violate the requirement.
     */
    private List<Map<String, Object>> checkUnsatisfiedPrerequisites(Long userId, List<UserCoursePlan> plannedCourses) {
        List<Map<String, Object>> unsatisfied = new ArrayList<>();

        // Get completed courses (with grades) - these are used to check if prerequisites are satisfied
        List<UserClasses> completedCourses = userClassesRepository.findCompletedClassesByUserId(userId);
        
        // Get all UserClasses courses (both completed and in-progress) - these are the courses we check
        List<UserClasses> allUserClasses = userClassesRepository.findByUserId(userId);

        // Track courses that are available for prerequisite checking
        // Only include completed courses (NOT in-progress courses for prerequisites, but yes for corequisites)
        Set<String> completedCoursesSet = new HashSet<>();
        for (UserClasses uc : completedCourses) {
            completedCoursesSet.add(uc.getDepartment() + " " + uc.getCourseNum());
        }
        
        // For corequisites and antirequisites checking, we also include in-progress courses and planned courses
        Set<String> availableCourses = new HashSet<>(completedCoursesSet);
        // Include in-progress courses (UserClasses with grade null) for coreq/antireq checking
        for (UserClasses uc : allUserClasses) {
            if (uc.getGrade() == null) { // In-progress courses
                availableCourses.add(uc.getDepartment() + " " + uc.getCourseNum());
            }
        }
        // Include planned courses for coreq/antireq checking (assuming they will be completed)
        for (UserCoursePlan plan : plannedCourses) {
            availableCourses.add(plan.getDepartment() + " " + plan.getCourseNum());
        }

        // Check only in-progress courses (not completed courses) for unsatisfied prerequisites/corequisites/antirequisites
        // Completed courses are already finished, so we don't need to check them
        // Examples:
        // 1. An in-progress course (grade null) with unsatisfied prerequisites
        // 2. An enrolled course with missing corequisites
        for (UserClasses userClass : allUserClasses) {
            // Skip completed courses - they are already finished
            if (userClass.getGrade() != null && !userClass.getGrade().trim().isEmpty() && !userClass.getGrade().equals("I")) {
                continue;
            }
            
            String courseCode = userClass.getDepartment() + " " + userClass.getCourseNum();
            
            // Get course information from Course entity (scraped data)
            Optional<Course> courseOpt = resolveCourse(
                userClass.getDepartment(), userClass.getCourseNum().toString());
            
            if (courseOpt.isEmpty()) {
                // Course not found in catalog - skip prerequisite check
                continue;
            }

            Course course = courseOpt.get();
            String prerequisites = course.getPrerequisites();
            String corequisites = course.getCorequisites();
            String antirequisites = course.getAntirequisites();

            // Skip if no prerequisites, corequisites, or antirequisites to check
            boolean hasPrerequisites = prerequisites != null && !prerequisites.trim().isEmpty() && !prerequisites.trim().equalsIgnoreCase("unknown");
            boolean hasCorequisites = corequisites != null && !corequisites.trim().isEmpty() && !corequisites.trim().equalsIgnoreCase("unknown");
            boolean hasAntirequisites = antirequisites != null && !antirequisites.trim().isEmpty() && !antirequisites.trim().equalsIgnoreCase("unknown");
            
            if (!hasPrerequisites && !hasCorequisites && !hasAntirequisites) {
                continue;
            }

            // Check prerequisites
            // Prerequisites can be satisfied by completed, enrolled (in-progress), or planned courses
            List<String> missingPrereqs = new ArrayList<>();
            if (hasPrerequisites) {
                // Get all courses available before this course (completed + enrolled + planned)
                List<UserClasses> coursesBefore = getCoursesBeforeSemester(completedCourses, userClass.getSemester(), userClass.getYear());
                // Also include in-progress courses (enrolled) before this course
                for (UserClasses uc : allUserClasses) {
                    if (uc.getGrade() == null && isCourseBefore(uc, userClass.getSemester(), userClass.getYear())) {
                        coursesBefore.add(uc);
                    }
                }
                Set<String> coursesBeforeSet = new HashSet<>();
                for (UserClasses uc : coursesBefore) {
                    coursesBeforeSet.add(uc.getDepartment() + " " + uc.getCourseNum());
                }
                // Also add planned courses that come before this one
                for (UserCoursePlan p : plannedCourses) {
                    if (isPlannedCourseBefore(p, userClass.getSemester(), userClass.getYear())) {
                        coursesBeforeSet.add(p.getDepartment() + " " + p.getCourseNum());
                    }
                }
                
                missingPrereqs = checkPrerequisitesForPlan(
                    prerequisites, coursesBefore, coursesBeforeSet, userClass.getSemester(), userClass.getYear());
            }

            // Check corequisites (must be in same semester or already completed)
            // Corequisites can be taken in the same semester, so we pass allUserClasses to check same semester
            List<String> missingCoreqs = new ArrayList<>();
            if (hasCorequisites) {
                missingCoreqs = checkCorequisitesForPlan(
                    corequisites, prerequisites, completedCourses, allUserClasses, 
                    userClass.getSemester(), userClass.getYear());
            }

            // Check antirequisites
            // Antireq: if antireq course is taken/planned, then this course cannot be taken in the same term or after
            List<String> violatedAntireqs = new ArrayList<>();
            if (hasAntirequisites) {
                violatedAntireqs = checkAntirequisitesForPlan(
                    antirequisites, allUserClasses, plannedCourses, userClass.getSemester(), userClass.getYear());
            }

            if (!missingPrereqs.isEmpty() || !missingCoreqs.isEmpty() || !violatedAntireqs.isEmpty()) {
                Map<String, Object> issue = new HashMap<>();
                issue.put("course", courseCode);
                issue.put("courseName", course.getCourseName());
                issue.put("semester", userClass.getSemester());
                issue.put("year", userClass.getYear());
                issue.put("status", "UNSATISFIED"); // Course (completed or in-progress) with unsatisfied prerequisites/corequisites/antirequisites
                
                if (!missingPrereqs.isEmpty()) {
                    issue.put("missingPrerequisites", missingPrereqs);
                }
                if (!missingCoreqs.isEmpty()) {
                    issue.put("missingCorequisites", missingCoreqs);
                }
                if (!violatedAntireqs.isEmpty()) {
                    issue.put("violatedAntirequisites", violatedAntireqs);
                }
                
                unsatisfied.add(issue);
            }
        }

        // Also check planned courses for unsatisfied prerequisites/corequisites/antirequisites
        // This checks courses that the student plans to take
        // Assumes all planned courses will be completed with adequate grades
        // Planned courses are checked in chronological order (sorted by semester/year)
        for (UserCoursePlan plan : plannedCourses) {
            String courseCode = plan.getDepartment() + " " + plan.getCourseNum();
            
            // Get course information from Course entity (scraped data)
            Optional<Course> courseOpt = resolveCourse(
                plan.getDepartment(), plan.getCourseNum().toString());
            
            if (courseOpt.isEmpty()) {
                // Course not found in catalog - skip prerequisite check
                continue;
            }

            Course course = courseOpt.get();
            String prerequisites = course.getPrerequisites();
            String corequisites = course.getCorequisites();
            String antirequisites = course.getAntirequisites();

            // Skip if no prerequisites, corequisites, or antirequisites to check
            boolean hasPrerequisites = prerequisites != null && !prerequisites.trim().isEmpty() && !prerequisites.trim().equalsIgnoreCase("unknown");
            boolean hasCorequisites = corequisites != null && !corequisites.trim().isEmpty() && !corequisites.trim().equalsIgnoreCase("unknown");
            boolean hasAntirequisites = antirequisites != null && !antirequisites.trim().isEmpty() && !antirequisites.trim().equalsIgnoreCase("unknown");
            
            if (!hasPrerequisites && !hasCorequisites && !hasAntirequisites) {
                continue;
            }

            // For prerequisites checking, we need to check if prerequisites are satisfied
            // by completed, enrolled (in-progress), or planned courses that come BEFORE this course
            // (assuming all planned courses will be completed with adequate grades)
            List<String> missingPrereqs = new ArrayList<>();
            if (hasPrerequisites) {
                // Get all courses available before this planned course (completed + enrolled + planned before)
                List<UserClasses> coursesBefore = getCoursesBeforeSemester(completedCourses, plan.getSemester(), plan.getYear());
                // Also include in-progress courses (enrolled) before this planned course
                for (UserClasses uc : allUserClasses) {
                    if (uc.getGrade() == null && isCourseBefore(uc, plan.getSemester(), plan.getYear())) {
                        coursesBefore.add(uc);
                    }
                }
                Set<String> coursesBeforeSet = new HashSet<>();
                for (UserClasses uc : coursesBefore) {
                    coursesBeforeSet.add(uc.getDepartment() + " " + uc.getCourseNum());
                }
                // Also add planned courses that come before this one
                for (UserCoursePlan p : plannedCourses) {
                    if (isPlannedCourseBefore(p, plan.getSemester(), plan.getYear())) {
                        coursesBeforeSet.add(p.getDepartment() + " " + p.getCourseNum());
                    }
                }
                
                missingPrereqs = checkPrerequisitesForPlan(
                    prerequisites, coursesBefore, coursesBeforeSet, plan.getSemester(), plan.getYear());
            }

            // Check corequisites (must be in same semester or already completed/enrolled/planned before)
            // For planned courses, we check if corequisite is in same semester or planned/taken/enrolled before
            List<String> missingCoreqs = new ArrayList<>();
            if (hasCorequisites) {
                // Get all courses (completed + in-progress + planned) for corequisite checking
                List<UserClasses> allCoursesForCoreq = new ArrayList<>(allUserClasses);
                missingCoreqs = checkCorequisitesForPlan(
                    corequisites, prerequisites, completedCourses, allCoursesForCoreq, 
                    plan.getSemester(), plan.getYear());
                
                // Also check if corequisite is in planned courses (same semester or before)
                if (!missingCoreqs.isEmpty()) {
                    // Re-check with planned courses included
                    Matcher matcher = COURSE_PATTERN.matcher(corequisites);
                    while (matcher.find()) {
                        String dept = matcher.group(1);
                        String courseNum = matcher.group(2);
                        // Check if this corequisite is in planned courses (same semester or before)
                        for (UserCoursePlan p : plannedCourses) {
                            if (p.getDepartment().equals(dept) && 
                                p.getCourseNum().toString().equals(courseNum)) {
                                // Check if same semester or before
                                if (p.getYear() != null && plan.getYear() != null &&
                                    p.getSemester() != null && plan.getSemester() != null) {
                                    if (p.getYear().equals(plan.getYear()) &&
                                        p.getSemester().equalsIgnoreCase(plan.getSemester())) {
                                        // Same semester - remove from missing
                                        missingCoreqs.removeIf(m -> m.contains(dept + " " + courseNum));
                                        break;
                                    } else if (isPlannedCourseBefore(p, plan.getSemester(), plan.getYear())) {
                                        // Before this course - remove from missing
                                        missingCoreqs.removeIf(m -> m.contains(dept + " " + courseNum));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Check antirequisites
            // Antireq: if antireq course is taken/planned/enrolled, then this course cannot be taken in the same term or after
            List<String> violatedAntireqs = new ArrayList<>();
            if (hasAntirequisites) {
                violatedAntireqs = checkAntirequisitesForPlan(
                    antirequisites, allUserClasses, plannedCourses, plan.getSemester(), plan.getYear());
            }

            if (!missingPrereqs.isEmpty() || !missingCoreqs.isEmpty() || !violatedAntireqs.isEmpty()) {
                Map<String, Object> issue = new HashMap<>();
                issue.put("course", courseCode);
                issue.put("courseName", course.getCourseName());
                issue.put("semester", plan.getSemester());
                issue.put("year", plan.getYear());
                issue.put("status", "UNSATISFIED"); // Planned course with unsatisfied prerequisites/corequisites/antirequisites
                issue.put("isPlanned", true); // Mark as planned course
                
                if (!missingPrereqs.isEmpty()) {
                    issue.put("missingPrerequisites", missingPrereqs);
                }
                if (!missingCoreqs.isEmpty()) {
                    issue.put("missingCorequisites", missingCoreqs);
                }
                if (!violatedAntireqs.isEmpty()) {
                    issue.put("violatedAntirequisites", violatedAntireqs);
                }
                
                unsatisfied.add(issue);
            }
        }

        return unsatisfied;
    }

    /**
     * Check prerequisites considering both completed and planned courses.
     */
    private List<String> checkPrerequisitesForPlan(String prerequisites, 
                                                     List<UserClasses> completedCourses,
                                                     Set<String> availableCourses,
                                                     String semester, Integer year) {
        List<String> missing = new ArrayList<>();
        
        if (prerequisites == null || prerequisites.trim().isEmpty()) {
            return missing;
        }

        String[] prerequisiteGroups = prerequisites.split(";");
        
        for (String group : prerequisiteGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;
            
            if (!isPrerequisiteGroupSatisfied(group, completedCourses, availableCourses, semester, year)) {
                missing.add(group);
            }
        }
        
        return missing;
    }

    /**
     * Check if a prerequisite group is satisfied.
     * 
     * Status definitions:
     * - SATISFIED: Course completed with grade >= min_grade (or C if no min_grade specified) BEFORE the course being checked
     * - UNSATISFIED: Course not taken, or grade < min_grade, or enrolled/in-progress (no grade), or completed AFTER the course being checked
     * 
     * Note: Prerequisites require COMPLETION (grade received) BEFORE taking the course.
     * Enrolled/in-progress courses (no grade) do NOT satisfy prerequisites.
     * 
     * @param group Prerequisite group to check
     * @param completedCourses All completed courses
     * @param availableCourses Available courses (for corequisites, not used for prerequisites)
     * @param courseSemester Semester of the course being checked
     * @param courseYear Year of the course being checked
     */
    private boolean isPrerequisiteGroupSatisfied(String group, 
                                                  List<UserClasses> completedCourses,
                                                  Set<String> availableCourses,
                                                  String courseSemester, Integer courseYear) {
        // Handle class standing requirements
        if (group.toLowerCase().contains("standing")) {
            // Calculate total credits from completed courses BEFORE the course being checked
            int totalCredits = 0;
            for (UserClasses uc : completedCourses) {
                // Only count credits from courses completed BEFORE the course being checked
                if (isCourseBefore(uc, courseSemester, courseYear) && uc.getCredits() != null) {
                    totalCredits += uc.getCredits();
                }
            }
            // Also count planned courses as credits
            // This is a simplified approach - in reality, we'd need to track planned credits
            return checkStandingRequirement(group, totalCredits);
        }
        
        // Handle grade requirements like "C or higher in BIO 205 or BIO 207"
        if (group.toLowerCase().contains("or higher in") || group.toLowerCase().contains("c or higher")) {
            Matcher matcher = COURSE_PATTERN.matcher(group);
            while (matcher.find()) {
                String department = matcher.group(1);
                String courseNum = matcher.group(2);
                
                // Check completed courses with grade BEFORE the course being checked
                if (hasCompletedCourseWithGrade(completedCourses, department, courseNum, courseSemester, courseYear)) {
                    return true;
                }
            }
            return false;
        }
        
        // Handle simple course requirements
        Matcher matcher = COURSE_PATTERN.matcher(group);
        boolean foundAny = false;
        while (matcher.find()) {
            foundAny = true;
            String department = matcher.group(1);
            String courseNum = matcher.group(2);
            
            // For prerequisites: only check if course is COMPLETED (has grade) BEFORE the course being checked
            // Enrolled/in-progress courses (no grade) do NOT satisfy prerequisites
            // Prerequisites require completion BEFORE taking the course
            if (hasCompletedCourse(completedCourses, department, courseNum, courseSemester, courseYear)) {
                return true;
            }
            
            // Note: We do NOT check availableCourses for prerequisites
            // because enrolled/in-progress courses don't have grades yet
            // and prerequisites require completion (grade received) BEFORE taking the course
        }
        
        // If no course code found, assume satisfied (non-course requirement)
        return !foundAny;
    }

    /**
     * Check corequisites for planned courses.
     * Corequisites must be taken in the same semester or already completed.
     */
    private List<String> checkCorequisitesForPlan(String corequisites,
                                                    String prerequisites,
                                                    List<UserClasses> completedCourses,
                                                    List<UserClasses> allUserClasses,
                                                    String semester, Integer year) {
        List<String> missing = new ArrayList<>();
        
        if (corequisites == null || corequisites.trim().isEmpty() || corequisites.trim().equalsIgnoreCase("unknown")) {
            return missing;
        }

        String[] corequisiteGroups = corequisites.split(";");
        
        for (String group : corequisiteGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;
            
            // Check if corequisite is satisfied
            // Corequisite must be in same semester or already completed
            Matcher matcher = COURSE_PATTERN.matcher(group);
            boolean satisfied = false;
            
            while (matcher.find()) {
                String department = matcher.group(1);
                String courseNum = matcher.group(2);
                
                // Check if already completed (before or in the same semester)
                // Corequisites can be taken in the same semester, so we check if completed before or in same semester
                if (hasCompletedCourseBeforeOrSame(completedCourses, department, courseNum, semester, year)) {
                    satisfied = true;
                    break;
                }
                
                // Check if taking in the same semester (in-progress or completed in same semester)
                for (UserClasses uc : allUserClasses) {
                    if (uc.getDepartment().equals(department) && 
                        uc.getCourseNum().toString().equals(courseNum)) {
                    // Check if it's in the same semester
                        if (uc.getYear() != null && year != null && uc.getYear().equals(year) &&
                            uc.getSemester() != null && semester != null &&
                            uc.getSemester().equalsIgnoreCase(semester)) {
                    satisfied = true;
                            break;
                        }
                    }
                }
                if (satisfied) {
                    break;
                }
            }
            
            if (!satisfied) {
                missing.add(group);
            }
        }
        
        return missing;
    }

    /**
     * Check antirequisites for courses.
     * Antireq rule: If antireq course is taken/planned/enrolled, then this course cannot be taken 
     * in the same term or after the antireq course.
     * 
     * @param antirequisites Antirequisites string
     * @param allUserClasses All UserClasses (completed + enrolled)
     * @param plannedCourses All planned courses
     * @param courseSemester Semester of the course being checked
     * @param courseYear Year of the course being checked
     */
    private List<String> checkAntirequisitesForPlan(String antirequisites,
                                                     List<UserClasses> allUserClasses,
                                                     List<UserCoursePlan> plannedCourses,
                                                     String courseSemester, Integer courseYear) {
        List<String> violated = new ArrayList<>();
        
        if (antirequisites == null || antirequisites.trim().isEmpty()) {
            return violated;
        }

        String[] antirequisiteGroups = antirequisites.split(";");
        
        for (String group : antirequisiteGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;
            
            Matcher matcher = COURSE_PATTERN.matcher(group);
            while (matcher.find()) {
                String department = matcher.group(1);
                String courseNum = matcher.group(2);
                String courseCode = department + " " + courseNum;
                
                // Check if antireq course is taken/planned/enrolled
                // If yes, check if it's in the same term or before the course being checked
                // If antireq is in same term or before, it's a violation
                
                // Check in UserClasses (completed or enrolled)
                for (UserClasses uc : allUserClasses) {
                    if (uc.getDepartment().equals(department) && 
                        uc.getCourseNum().toString().equals(courseNum)) {
                        // Found antireq course - check if it's in same term or before
                        // If antireq is taken in same term or before, then this course cannot be taken (violation)
                        if (uc.getYear() != null && courseYear != null &&
                            uc.getSemester() != null && courseSemester != null) {
                            if (uc.getYear().equals(courseYear) &&
                                uc.getSemester().equalsIgnoreCase(courseSemester)) {
                                // Same term - violation
                    violated.add(courseCode);
                                break;
                            } else if (isCourseBefore(uc, courseSemester, courseYear)) {
                                // Before this course - violation (antireq taken before, so this course cannot be taken)
                                violated.add(courseCode);
                                break;
                            }
                        }
                    }
                }
                
                // Check in planned courses
                for (UserCoursePlan p : plannedCourses) {
                    if (p.getDepartment().equals(department) && 
                        p.getCourseNum().toString().equals(courseNum)) {
                        // Found antireq course in plan - check if it's in same term or before
                        // If antireq is planned in same term or before, then this course cannot be taken (violation)
                        if (p.getYear() != null && courseYear != null &&
                            p.getSemester() != null && courseSemester != null) {
                            if (p.getYear().equals(courseYear) &&
                                p.getSemester().equalsIgnoreCase(courseSemester)) {
                                // Same term - violation
                                violated.add(courseCode);
                                break;
                            } else if (isPlannedCourseBefore(p, courseSemester, courseYear)) {
                                // Before this course - violation (antireq planned before, so this course cannot be taken)
                                violated.add(courseCode);
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        return violated;
    }

    /**
     * Calculate degree requirement final status after plan completion.
     */
    private List<Map<String, Object>> calculateDegreeRequirementStatus(Long userId, 
                                                                        List<UserCoursePlan> plannedCourses) {
        List<Map<String, Object>> statusList = new ArrayList<>();

        // Debug: Log planned courses received
        System.out.println("[DEBUG calculateDegreeRequirementStatus] ===== START =====");
        System.out.println("[DEBUG calculateDegreeRequirementStatus] User ID: " + userId);
        System.out.println("[DEBUG calculateDegreeRequirementStatus] Received plannedCourses size: " + (plannedCourses != null ? plannedCourses.size() : "null"));
        if (plannedCourses != null && !plannedCourses.isEmpty()) {
            for (UserCoursePlan plan : plannedCourses) {
                System.out.println("[DEBUG calculateDegreeRequirementStatus] Planned course: " + 
                    plan.getDepartment() + " " + plan.getCourseNum() + " (" + plan.getSemester() + " " + plan.getYear() + ")");
            }
        }

        // Get student
        User student = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Student not found: " + userId));

        // Get student's planned majors
        List<PlannedMajor> plannedMajors = plannedMajorRepository.findActiveByStudentId(userId);
        
        // Get declared majors from User entity (JSON array stored as string)
        List<Map<String, Object>> declaredMajors = parseDeclaredMajors(student);
        
        // Debug: Log major information
        System.out.println("[DEBUG calculateDegreeRequirementStatus] Planned majors count: " + plannedMajors.size());
        System.out.println("[DEBUG calculateDegreeRequirementStatus] Declared majors count: " + declaredMajors.size());
        System.out.println("[DEBUG calculateDegreeRequirementStatus] Student majors JSON: " + student.getMajors());
        System.out.println("[DEBUG calculateDegreeRequirementStatus] Student degrees JSON: " + student.getDegrees());
        
        // If no planned majors and no declared majors, return empty
        if (plannedMajors.isEmpty() && declaredMajors.isEmpty()) {
            System.out.println("[DEBUG] No majors found, returning empty status list");
            return statusList;
        }

        // Get all user classes (both completed and in-progress)
        List<UserClasses> allUserClasses = userClassesRepository.findByUserId(userId);
        
        // Separate completed courses (with grades) and in-progress courses (grade is null)
        List<UserClasses> completedCourses = new ArrayList<>();
        List<UserClasses> inProgressClasses = new ArrayList<>();
        for (UserClasses uc : allUserClasses) {
            if (uc.getGrade() != null && !uc.getGrade().trim().isEmpty()) {
                completedCourses.add(uc);
            } else {
                // grade is null - this is an enrolled/in-progress course
                inProgressClasses.add(uc);
            }
        }
        
        // Get enrolled courses (from ClassRoster with status = "ENROLLED")
        // Enrolled courses should be included in final status as in-progress courses
        List<ClassRoster> enrolledRosters = classRosterRepository.findActiveEnrollmentsByUser(student);
        Set<String> enrolledCourseCodes = new HashSet<>();
        for (ClassRoster roster : enrolledRosters) {
            ClassEntity classEntity = roster.getClassEntity();
            if (classEntity != null && classEntity.getDepartment() != null && classEntity.getCourseNum() != null) {
                String courseCode = classEntity.getDepartment() + " " + classEntity.getCourseNum();
                enrolledCourseCodes.add(courseCode);
            }
        }
        
        // Also add in-progress courses from user_classes (grade: null) to enrolledCourseCodes
        for (UserClasses uc : inProgressClasses) {
            if (uc.getDepartment() != null && uc.getCourseNum() != null) {
                String courseCode = uc.getDepartment() + " " + uc.getCourseNum();
                enrolledCourseCodes.add(courseCode);
            }
        }

        // Only use completed courses (including in-progress with null grade) and enrolled courses for final status
        // Planned courses are excluded from final degree requirement status
        Set<String> allCourses = new HashSet<>();
        for (UserClasses uc : completedCourses) {
            allCourses.add(uc.getDepartment() + " " + uc.getCourseNum());
        }
        // Add enrolled courses to allCourses
        allCourses.addAll(enrolledCourseCodes);

        // Calculate total credits (from completed courses, enrolled courses, and in-progress courses, not planned)
        // Avoid double counting: enrolledRosters and inProgressClasses may overlap
        int totalCredits = 0;
        Set<String> processedCourseCodes = new HashSet<>(); // Track processed courses to avoid duplicates
        
        // First, count completed courses
        int completedCredits = 0;
        for (UserClasses uc : completedCourses) {
            if (uc.getCredits() != null) {
                completedCredits += uc.getCredits();
                String courseCode = uc.getDepartment() + " " + uc.getCourseNum();
                processedCourseCodes.add(courseCode);
            }
        }
        totalCredits += completedCredits;
        System.out.println("[DEBUG calculateDegreeRequirementStatus] Completed courses credits: " + completedCredits);
        
        // Add credits from enrolled courses (from ClassRoster) - only if not already counted
        int enrolledCredits = 0;
        for (ClassRoster roster : enrolledRosters) {
            ClassEntity classEntity = roster.getClassEntity();
            if (classEntity != null && classEntity.getMinCredits() != null) {
                String courseCode = classEntity.getDepartment() + " " + classEntity.getCourseNum();
                if (!processedCourseCodes.contains(courseCode)) {
                    enrolledCredits += classEntity.getMinCredits();
                    processedCourseCodes.add(courseCode);
                }
            }
        }
        totalCredits += enrolledCredits;
        System.out.println("[DEBUG calculateDegreeRequirementStatus] Enrolled courses credits: " + enrolledCredits);
        
        // Add credits from in-progress courses (grade: null in user_classes) - only if not already counted
        int inProgressCredits = 0;
        for (UserClasses uc : inProgressClasses) {
            if (uc.getCredits() != null) {
                String courseCode = uc.getDepartment() + " " + uc.getCourseNum();
                if (!processedCourseCodes.contains(courseCode)) {
                    inProgressCredits += uc.getCredits();
                    processedCourseCodes.add(courseCode);
                }
            }
        }
        totalCredits += inProgressCredits;
        System.out.println("[DEBUG calculateDegreeRequirementStatus] In-progress courses credits: " + inProgressCredits);
        System.out.println("[DEBUG calculateDegreeRequirementStatus] Total credits calculated: " + totalCredits);

        // Process declared majors
        for (Map<String, Object> declaredMajor : declaredMajors) {
            String subjectCode = (String) declaredMajor.get("subjectCode");
            String degreeTypeStr = (String) declaredMajor.get("degreeType");
            
            DegreeType degreeType;
            try {
                degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                continue; // Skip invalid degree type
            }
            
            Optional<DegreeRequirement> requirementOpt = degreeRequirementRepository
                .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);

            if (requirementOpt.isEmpty()) {
                System.out.println("[DEBUG] No degree requirement found for " + subjectCode + " " + degreeType);
                continue;
            }

            DegreeRequirement requirement = requirementOpt.get();
            System.out.println("[DEBUG] Processing requirement for " + subjectCode + " " + degreeType);
            // Pass plannedCourses for SBC checking (assuming they will be completed with adequate grades)
            // Pass allUserClasses so in-progress courses (grade: null) can be included
            Map<String, Object> status = calculateRequirementStatus(
                subjectCode, degreeType, requirement, allCourses, completedCourses, allUserClasses, plannedCourses, totalCredits, enrolledRosters);
            statusList.add(status);
            System.out.println("[DEBUG] Added status to list. Total status count: " + statusList.size());
        }

        // Process planned majors
        for (PlannedMajor plannedMajor : plannedMajors) {
            Optional<DegreeRequirement> requirementOpt = degreeRequirementRepository
                .findBySubjectCodeAndDegreeTypeAndActive(
                    plannedMajor.getSubjectCode(), 
                    plannedMajor.getDegreeType());

            if (requirementOpt.isEmpty()) {
                continue;
            }

            DegreeRequirement requirement = requirementOpt.get();
            Map<String, Object> status = calculateRequirementStatus(
                plannedMajor.getSubjectCode(), 
                plannedMajor.getDegreeType(), 
                requirement, 
                allCourses, 
                completedCourses,
                allUserClasses,
                plannedCourses, 
                totalCredits,
                enrolledRosters);
            statusList.add(status);
        }

        // Process declared minors (planned minors are excluded from final status)
        List<Map<String, Object>> declaredMinors = parseDeclaredMinors(student);
        for (Map<String, Object> declaredMinor : declaredMinors) {
            String subjectCode = (String) declaredMinor.get("subjectCode");
            
            Optional<DegreeRequirement> requirementOpt = degreeRequirementRepository
                .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, DegreeType.MINOR);

            if (requirementOpt.isEmpty()) {
                continue;
            }

            DegreeRequirement requirement = requirementOpt.get();
            Map<String, Object> status = calculateRequirementStatus(
                subjectCode, DegreeType.MINOR, requirement, allCourses, completedCourses, allUserClasses, plannedCourses, totalCredits, enrolledRosters);
            statusList.add(status);
        }

        return statusList;
    }

    /**
     * Check course requirements against completed and planned courses.
     */
    private Map<String, Object> checkCourseRequirements(String courseRequirementsJson,
                                                          Set<String> allCourses,
                                                          List<UserClasses> completedCourses,
                                                          List<UserClasses> allUserClasses,
                                                          List<UserCoursePlan> plannedCourses,
                                                          List<ClassRoster> enrolledRosters) {
        Map<String, Object> result = new HashMap<>();
        
        if (courseRequirementsJson == null || courseRequirementsJson.trim().isEmpty()) {
            result.put("status", "no_requirements");
            return result;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> requirements = (Map<String, Object>) mapper.readValue(courseRequirementsJson, Map.class);
            
            // Check if using new groups format
            if (requirements.containsKey("groups")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> groups = (List<Map<String, Object>>) requirements.get("groups");
                List<Map<String, Object>> groupStatuses = new ArrayList<>();
                
                // Track used class IDs from non-electives groups
                // This ensures electives don't count courses already used in other groups
                Set<String> usedClassIds = new HashSet<>();
                
                // First pass: Check non-electives groups to track used courses
                for (Map<String, Object> group : groups) {
                    String type = (String) group.get("type");
                    if (!"electives".equals(type)) {
                        // Pass plannedCourses so planned courses are included in missingCourses calculation
                        // Pass enrolledRosters so enrolled courses can be treated as in-progress
                        Map<String, Object> groupStatus = checkGroupRequirementForStatus(
                            group, completedCourses, allUserClasses, plannedCourses, allCourses, enrolledRosters, usedClassIds);
                        groupStatuses.add(groupStatus);
                        
                        // Track used classes from this group's satisfiedCourses
                        @SuppressWarnings("unchecked")
                        List<String> satisfiedCourses = (List<String>) groupStatus.get("satisfiedCourses");
                        if (satisfiedCourses != null) {
                            for (String courseCode : satisfiedCourses) {
                                // Find the UserClasses object for this course and add its classId to usedClassIds
                                String[] parts = parseCourseCode(courseCode);
                                String dept = parts[0];
                                String courseNumStr = parts[1];
                                for (UserClasses uc : completedCourses) {
                                    if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) &&
                                        uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr) &&
                                        uc.getClassId() != null) {
                                        usedClassIds.add(uc.getClassId().toString());
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Second pass: Check electives groups with used classes excluded
                for (Map<String, Object> group : groups) {
                    String type = (String) group.get("type");
                    if ("electives".equals(type)) {
                        // Pass plannedCourses so planned courses are included in missingCourses calculation
                        // Pass enrolledRosters so enrolled courses can be treated as in-progress
                        Map<String, Object> groupStatus = checkGroupRequirementForStatus(
                            group, completedCourses, allUserClasses, plannedCourses, allCourses, enrolledRosters, usedClassIds);
                        groupStatuses.add(groupStatus);
                    }
                }
                
                result.put("groups", groupStatuses);
                return result;
            }
            
            // Old format: Check core courses
            if (requirements.containsKey("core_courses")) {
                @SuppressWarnings("unchecked")
                List<String> coreCourses = (List<String>) requirements.get("core_courses");
                List<String> satisfied = new ArrayList<>();
                List<String> missing = new ArrayList<>();
                
                for (String courseCode : coreCourses) {
                    if (allCourses.contains(courseCode)) {
                        satisfied.add(courseCode);
                    } else {
                        missing.add(courseCode);
                    }
                }
                
                result.put("coreCourses", Map.of(
                    "required", coreCourses.size(),
                    "satisfied", satisfied.size(),
                    "missing", missing,
                    "satisfiedList", satisfied
                ));
            }

            // Check elective courses
            if (requirements.containsKey("elective_courses")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> electiveReq = (Map<String, Object>) requirements.get("elective_courses");
                Integer creditsRequired = (Integer) electiveReq.get("credits_required");
                
                // Count elective credits (simplified - would need to check course catalog)
                int electiveCredits = 0;
                // This is a simplified calculation - in reality, we'd need to check
                // which courses count as electives from the catalog
                
                result.put("electiveCourses", Map.of(
                    "creditsRequired", creditsRequired != null ? creditsRequired : 0,
                    "creditsEarned", electiveCredits,
                    "met", electiveCredits >= (creditsRequired != null ? creditsRequired : 0)
                ));
            }

            // Check math requirements
            if (requirements.containsKey("math_requirements")) {
                @SuppressWarnings("unchecked")
                List<String> mathCourses = (List<String>) requirements.get("math_requirements");
                List<String> satisfied = new ArrayList<>();
                List<String> missing = new ArrayList<>();
                
                for (String courseCode : mathCourses) {
                    if (allCourses.contains(courseCode)) {
                        satisfied.add(courseCode);
                    } else {
                        missing.add(courseCode);
                    }
                }
                
                result.put("mathRequirements", Map.of(
                    "required", mathCourses.size(),
                    "satisfied", satisfied.size(),
                    "missing", missing,
                    "satisfiedList", satisfied
                ));
            }

            // Check science requirements
            if (requirements.containsKey("science_requirements")) {
                @SuppressWarnings("unchecked")
                List<String> scienceCourses = (List<String>) requirements.get("science_requirements");
                List<String> satisfied = new ArrayList<>();
                List<String> missing = new ArrayList<>();
                
                for (String courseCode : scienceCourses) {
                    if (allCourses.contains(courseCode)) {
                        satisfied.add(courseCode);
                    } else {
                        missing.add(courseCode);
                    }
                }
                
                result.put("scienceRequirements", Map.of(
                    "required", scienceCourses.size(),
                    "satisfied", satisfied.size(),
                    "missing", missing,
                    "satisfiedList", satisfied
                ));
            }

        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", "Failed to parse course requirements: " + e.getMessage());
        }

        return result;
    }

    /**
     * Check SBC requirements.
     * SBC logic:
     * - required_major_courses: one of these courses satisfies the SBC
     * - required_major_courses_partial: ALL of these courses must be taken to satisfy the SBC
     * - optional_major_courses_partial: can replace one required_major_courses (for partial requirements)
     * - If no required_major_courses specified, SBC is satisfied by any course with that SBC designation (from catalog)
     */
    private Map<String, Object> checkSbcRequirements(String sbcRequirementsJson,
                                                       Set<String> allCourses,
                                                       List<UserClasses> completedCourses,
                                                       List<UserClasses> allUserClasses,
                                                       List<UserCoursePlan> plannedCourses,
                                                       List<ClassRoster> enrolledRosters) {
        Map<String, Object> result = new HashMap<>();
        
        System.out.println("[DEBUG checkSbcRequirements] ===== START =====");
        System.out.println("[DEBUG checkSbcRequirements] sbcRequirementsJson: " + (sbcRequirementsJson != null ? sbcRequirementsJson.substring(0, Math.min(200, sbcRequirementsJson.length())) : "null"));
        System.out.println("[DEBUG checkSbcRequirements] plannedCourses size: " + (plannedCourses != null ? plannedCourses.size() : "null"));
        System.out.println("[DEBUG checkSbcRequirements] completedCourses size: " + (completedCourses != null ? completedCourses.size() : "null"));
        
        if (sbcRequirementsJson == null || sbcRequirementsJson.trim().isEmpty()) {
            System.out.println("[DEBUG checkSbcRequirements] No SBC requirements JSON found");
            result.put("status", "no_requirements");
            return result;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sbcReqs = (List<Map<String, Object>>) mapper.readValue(sbcRequirementsJson, List.class);
            
            List<Map<String, Object>> sbcStatus = new ArrayList<>();
            
            for (Map<String, Object> sbcReq : sbcReqs) {
                String category = (String) sbcReq.get("sbc");
                if (category == null) {
                    category = (String) sbcReq.get("category");
                }
                
                System.out.println("[DEBUG checkSbcRequirements] Processing SBC category: " + category);
                
                Map<String, Object> status = new HashMap<>();
                status.put("category", category);
                
                // Check required_major_courses (one of these satisfies the SBC)
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> requiredMajorCourses = (List<Map<String, Object>>) sbcReq.get("required_major_courses");
                boolean requiredSatisfied = false;
                List<String> satisfiedRequiredCourses = new ArrayList<>();
                
                if (requiredMajorCourses != null && !requiredMajorCourses.isEmpty()) {
                    for (Map<String, Object> courseReq : requiredMajorCourses) {
                        String code = (String) courseReq.get("code");
                        if (code == null) continue;
                        
                        // Get min_grade from course requirement, default to "C" if not specified
                        String minGrade = (String) courseReq.get("min_grade");
                        if (minGrade == null || minGrade.trim().isEmpty()) {
                            minGrade = "C";
                        }
                        
                        // Check if course is completed with sufficient grade
                        String[] parts = parseCourseCode(code);
                        String dept = parts[0];
                        String courseNumStr = parts[1];
                        
                        UserClasses matchingClass = findMatchingClassWithMinGrade(completedCourses, dept, courseNumStr, minGrade);
                        if (matchingClass != null) {
                            requiredSatisfied = true;
                            satisfiedRequiredCourses.add(code);
                            break; // Only need one
                        }
                        
                        // Check enrolled/in-progress courses (grade: null in user_classes)
                        if (!requiredSatisfied && allUserClasses != null) {
                            for (UserClasses uc : allUserClasses) {
                                if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) &&
                                    uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr) &&
                                    (uc.getGrade() == null || uc.getGrade().trim().isEmpty())) {
                                    // Found enrolled/in-progress course
                                    requiredSatisfied = true;
                                    satisfiedRequiredCourses.add(code);
                                    break; // Only need one
                                }
                            }
                        }
                        
                        // Check enrolled courses (from ClassRoster)
                        if (!requiredSatisfied && enrolledRosters != null) {
                            for (ClassRoster roster : enrolledRosters) {
                                ClassEntity classEntity = roster.getClassEntity();
                                if (classEntity != null && classEntity.getDepartment() != null && 
                                    classEntity.getDepartment().equalsIgnoreCase(dept) &&
                                    classEntity.getCourseNum() != null && 
                                    classEntity.getCourseNum().toString().equals(courseNumStr) &&
                                    "ENROLLED".equals(roster.getStatus())) {
                                    // Found enrolled course
                                    requiredSatisfied = true;
                                    satisfiedRequiredCourses.add(code);
                                    break; // Only need one
                                }
                            }
                        }
                        
                        // Also check planned courses (assuming they will be completed with adequate grades)
                        if (!requiredSatisfied && plannedCourses != null) {
                            for (UserCoursePlan plan : plannedCourses) {
                                if (plan != null && plan.getDepartment() != null && plan.getDepartment().equals(dept) && 
                                    plan.getCourseNum() != null && plan.getCourseNum().toString().equals(courseNumStr)) {
                                    requiredSatisfied = true;
                                    satisfiedRequiredCourses.add(code);
                                    break; // Only need one
                                }
                            }
                        }
                    }
                }
                
                // Check required_major_courses_partial
                // Logic: If 2 courses, need both. If 3+ courses, need 2 of them.
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> requiredMajorCoursesPartial = (List<Map<String, Object>>) sbcReq.get("required_major_courses_partial");
                boolean partialRequiredSatisfied = false;
                List<String> satisfiedPartialRequired = new ArrayList<>();
                List<String> missingPartialRequired = new ArrayList<>();
                
                if (requiredMajorCoursesPartial != null && !requiredMajorCoursesPartial.isEmpty()) {
                    int totalPartialRequired = requiredMajorCoursesPartial.size();
                    
                    for (Map<String, Object> courseReq : requiredMajorCoursesPartial) {
                        String code = (String) courseReq.get("code");
                        if (code == null) continue;
                        
                        // Get min_grade from course requirement, default to "C" if not specified
                        String minGrade = (String) courseReq.get("min_grade");
                        if (minGrade == null || minGrade.trim().isEmpty()) {
                            minGrade = "C";
                        }
                        
                        // Check if course is completed with sufficient grade
                        String[] parts = parseCourseCode(code);
                        String dept = parts[0];
                        String courseNumStr = parts[1];
                        
                        // First check if this course is already satisfied in required_major_courses
                        // If it is, it should also count for partial requirements
                        boolean alreadySatisfied = satisfiedRequiredCourses.contains(code);
                        
                        if (alreadySatisfied) {
                            // Course is already satisfied in required_major_courses, so it also satisfies partial
                            satisfiedPartialRequired.add(code);
                        } else {
                            // Check if course is completed with sufficient grade (even if not in required_major_courses)
                            UserClasses matchingClass = findMatchingClassWithMinGrade(completedCourses, dept, courseNumStr, minGrade);
                            if (matchingClass != null) {
                                satisfiedPartialRequired.add(code);
                            } else {
                                // Check enrolled/in-progress courses
                                boolean foundInProgress = false;
                                if (allUserClasses != null) {
                                    for (UserClasses uc : allUserClasses) {
                                        if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) &&
                                            uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr) &&
                                            (uc.getGrade() == null || uc.getGrade().trim().isEmpty())) {
                                            // In-progress course found, count as satisfied
                                            satisfiedPartialRequired.add(code);
                                            foundInProgress = true;
                                            break;
                                        }
                                    }
                                }
                                if (!foundInProgress && enrolledRosters != null) {
                                    for (ClassRoster roster : enrolledRosters) {
                                        ClassEntity classEntity = roster.getClassEntity();
                                        if (classEntity != null && classEntity.getDepartment() != null && 
                                            classEntity.getDepartment().equalsIgnoreCase(dept) &&
                                            classEntity.getCourseNum() != null && 
                                            classEntity.getCourseNum().toString().equals(courseNumStr) &&
                                            "ENROLLED".equals(roster.getStatus())) {
                                            // Enrolled course found, count as satisfied
                                            satisfiedPartialRequired.add(code);
                                            foundInProgress = true;
                                            break;
                                        }
                                    }
                                }
                                // Also check planned courses (assuming they will be completed with adequate grades)
                                if (!foundInProgress) {
                                    boolean foundInPlan = false;
                                    if (plannedCourses != null) {
                                    for (UserCoursePlan plan : plannedCourses) {
                                            if (plan != null && plan.getDepartment() != null && plan.getDepartment().equals(dept) && 
                                                plan.getCourseNum() != null && plan.getCourseNum().toString().equals(courseNumStr)) {
                                            satisfiedPartialRequired.add(code);
                                            foundInPlan = true;
                                            break;
                                            }
                                        }
                                    }
                                    if (!foundInPlan) {
                                    missingPartialRequired.add(code);
                                    }
                                }
                            }
                        }
                    }
                    
                    // Check if requirement is met
                    if (totalPartialRequired == 2) {
                        // Need both courses
                        partialRequiredSatisfied = satisfiedPartialRequired.size() == 2;
                    } else {
                        // Need 2 out of 3+ courses
                        partialRequiredSatisfied = satisfiedPartialRequired.size() >= 2;
                    }
                } else {
                    // No partial requirements, so this check passes
                    partialRequiredSatisfied = true;
                }
                
                // Check optional_major_courses_partial (can replace one required_major_courses_partial)
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> optionalMajorCoursesPartial = (List<Map<String, Object>>) sbcReq.get("optional_major_courses_partial");
                List<String> satisfiedOptionalPartial = new ArrayList<>();
                
                if (optionalMajorCoursesPartial != null && !optionalMajorCoursesPartial.isEmpty()) {
                    for (Map<String, Object> courseReq : optionalMajorCoursesPartial) {
                        String code = (String) courseReq.get("code");
                        if (code == null) continue;
                        
                        // Get min_grade from course requirement, default to "C" if not specified
                        String minGrade = (String) courseReq.get("min_grade");
                        if (minGrade == null || minGrade.trim().isEmpty()) {
                            minGrade = "C";
                        }
                        
                        // Check if course is completed with sufficient grade
                        String[] parts = parseCourseCode(code);
                        String dept = parts[0];
                        String courseNumStr = parts[1];
                        
                        // First check if this course is already satisfied in required_major_courses or required_major_courses_partial
                        // If it is, it should also count for optional partial requirements
                        boolean alreadySatisfied = satisfiedRequiredCourses.contains(code) || satisfiedPartialRequired.contains(code);
                        
                        if (alreadySatisfied) {
                            // Course is already satisfied, so it also satisfies optional partial
                            satisfiedOptionalPartial.add(code);
                        } else {
                            UserClasses matchingClass = findMatchingClassWithMinGrade(completedCourses, dept, courseNumStr, minGrade);
                            if (matchingClass != null) {
                                satisfiedOptionalPartial.add(code);
                            } else {
                                // Check enrolled/in-progress courses (grade: null in user_classes)
                                boolean foundInProgress = false;
                                if (allUserClasses != null) {
                                    for (UserClasses uc : allUserClasses) {
                                        if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) &&
                                            uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr) &&
                                            (uc.getGrade() == null || uc.getGrade().trim().isEmpty())) {
                                            // Found enrolled/in-progress course
                                            satisfiedOptionalPartial.add(code);
                                            foundInProgress = true;
                                            break;
                                        }
                                    }
                                }
                                // Check enrolled courses (from ClassRoster)
                                if (!foundInProgress && enrolledRosters != null) {
                                    for (ClassRoster roster : enrolledRosters) {
                                        ClassEntity classEntity = roster.getClassEntity();
                                        if (classEntity != null && classEntity.getDepartment() != null && 
                                            classEntity.getDepartment().equalsIgnoreCase(dept) &&
                                            classEntity.getCourseNum() != null && 
                                            classEntity.getCourseNum().toString().equals(courseNumStr) &&
                                            "ENROLLED".equals(roster.getStatus())) {
                                            // Found enrolled course
                                            satisfiedOptionalPartial.add(code);
                                            foundInProgress = true;
                                            break;
                                        }
                                    }
                                }
                                // Also check planned courses (assuming they will be completed with adequate grades)
                                if (!foundInProgress && plannedCourses != null) {
                                for (UserCoursePlan plan : plannedCourses) {
                                        if (plan != null && plan.getDepartment() != null && plan.getDepartment().equals(dept) && 
                                            plan.getCourseNum() != null && plan.getCourseNum().toString().equals(courseNumStr)) {
                                        satisfiedOptionalPartial.add(code);
                                        break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Determine if SBC is met
                // Logic:
                // 1. If no course requirements exist, SBC cannot be satisfied by major courses (met = false)
                // 2. required_major_courses: one of these satisfies (or optional_major_courses_partial can replace one)
                // 3. required_major_courses_partial: 
                //    - If 2 courses: need both
                //    - If 3+ courses: need 2 of them
                // 4. If both exist, both conditions must be met
                
                boolean sbcMet = false;
                
                // Check if SBC has any course requirements
                boolean hasRequiredCourses = (requiredMajorCourses != null && !requiredMajorCourses.isEmpty());
                boolean hasPartialCourses = (requiredMajorCoursesPartial != null && !requiredMajorCoursesPartial.isEmpty());
                boolean hasOptionalPartial = (optionalMajorCoursesPartial != null && !optionalMajorCoursesPartial.isEmpty());
                boolean hasAnyCourseRequirements = hasRequiredCourses || hasPartialCourses || hasOptionalPartial;
                
                System.out.println("[DEBUG checkSbcRequirements] Category: " + category + 
                    ", hasAnyCourseRequirements: " + hasAnyCourseRequirements + 
                    ", hasRequiredCourses: " + hasRequiredCourses + 
                    ", hasPartialCourses: " + hasPartialCourses + 
                    ", hasOptionalPartial: " + hasOptionalPartial);
                
                // Always check if any planned/taken/enrolled course satisfies this SBC category
                // This is needed to show which courses satisfy the SBC, regardless of whether specific courses are required
                if (!hasAnyCourseRequirements) {
                    // If no specific courses required, check all planned/taken/enrolled courses
                    // Check completed courses first
                    // For ARTS, GLO, HUM, LANG etc., check each course's sbcs_satisfied field from scrapped courses
                    if (completedCourses != null) {
                        System.out.println("[DEBUG checkSbcRequirements] Checking " + completedCourses.size() + " completed courses for category: " + category);
                    for (UserClasses userClass : completedCourses) {
                            String courseCode = userClass.getDepartment() + " " + userClass.getCourseNum();
                            System.out.println("[DEBUG checkSbcRequirements] Looking up course in repository: " + courseCode);
                        Optional<Course> courseOpt = resolveCourse(
                            userClass.getDepartment(), userClass.getCourseNum().toString());
                        if (courseOpt.isPresent()) {
                            Course course = courseOpt.get();
                            String sbcsSatisfied = course.getSbcsSatisfied();
                                System.out.println("[DEBUG checkSbcRequirements] Course found - " + courseCode + ", sbcs_satisfied: " + sbcsSatisfied);
                            if (sbcsSatisfied != null && !sbcsSatisfied.equals("unknown") && 
                                hasSbcCategory(sbcsSatisfied, category)) {
                                    System.out.println("[DEBUG checkSbcRequirements] MATCH! " + courseCode + " satisfies " + category);
                                sbcMet = true;
                                    satisfiedRequiredCourses.add(courseCode);
                                break; // Only need one
                                } else {
                                    System.out.println("[DEBUG checkSbcRequirements] No match - " + courseCode + " does not satisfy " + category);
                                }
                            } else {
                                System.out.println("[DEBUG checkSbcRequirements] Course NOT found in repository: " + courseCode);
                            }
                        }
                    }
                    
                    // Check enrolled courses (in-progress)
                    // For ARTS, GLO, HUM, LANG etc., check each enrolled course's sbcs_satisfied field from scrapped courses
                    if (!sbcMet && enrolledRosters != null) {
                        System.out.println("[DEBUG checkSbcRequirements] Checking " + enrolledRosters.size() + " enrolled courses for category: " + category);
                        for (ClassRoster roster : enrolledRosters) {
                            ClassEntity classEntity = roster.getClassEntity();
                            if (classEntity != null && classEntity.getDepartment() != null && classEntity.getCourseNum() != null) {
                                String courseCode = classEntity.getDepartment() + " " + classEntity.getCourseNum();
                                System.out.println("[DEBUG checkSbcRequirements] Looking up enrolled course in repository: " + courseCode);
                                Optional<Course> courseOpt = resolveCourse(
                                    classEntity.getDepartment(), classEntity.getCourseNum().toString());
                                if (courseOpt.isPresent()) {
                                    Course course = courseOpt.get();
                                    String sbcsSatisfied = course.getSbcsSatisfied();
                                    System.out.println("[DEBUG checkSbcRequirements] Enrolled course found - " + courseCode + ", sbcs_satisfied: " + sbcsSatisfied);
                                    if (sbcsSatisfied != null && !sbcsSatisfied.equals("unknown") && 
                                        hasSbcCategory(sbcsSatisfied, category)) {
                                        System.out.println("[DEBUG checkSbcRequirements] MATCH! " + courseCode + " satisfies " + category);
                                        sbcMet = true;
                                        satisfiedRequiredCourses.add(courseCode);
                                        break; // Only need one
                                    } else {
                                        System.out.println("[DEBUG checkSbcRequirements] No match - " + courseCode + " does not satisfy " + category);
                                    }
                                } else {
                                    System.out.println("[DEBUG checkSbcRequirements] Enrolled course NOT found in repository: " + courseCode);
                                }
                            }
                        }
                    }
                    
                    // Check in-progress courses from user_classes (grade: null)
                    if (!sbcMet && allUserClasses != null) {
                        System.out.println("[DEBUG checkSbcRequirements] Checking in-progress courses (grade: null) for category: " + category);
                        for (UserClasses userClass : allUserClasses) {
                            // Only check courses with grade: null (in-progress)
                            if (userClass.getGrade() == null || userClass.getGrade().trim().isEmpty()) {
                                String courseCode = userClass.getDepartment() + " " + userClass.getCourseNum();
                                System.out.println("[DEBUG checkSbcRequirements] Looking up in-progress course in repository: " + courseCode);
                                Optional<Course> courseOpt = resolveCourse(
                                    userClass.getDepartment(), userClass.getCourseNum().toString());
                                if (courseOpt.isPresent()) {
                                    Course course = courseOpt.get();
                                    String sbcsSatisfied = course.getSbcsSatisfied();
                                    System.out.println("[DEBUG checkSbcRequirements] In-progress course found - " + courseCode + ", sbcs_satisfied: " + sbcsSatisfied);
                                    if (sbcsSatisfied != null && !sbcsSatisfied.equals("unknown") && 
                                        hasSbcCategory(sbcsSatisfied, category)) {
                                        System.out.println("[DEBUG checkSbcRequirements] MATCH! " + courseCode + " satisfies " + category);
                                        sbcMet = true;
                                        satisfiedRequiredCourses.add(courseCode);
                                        break; // Only need one
                                    } else {
                                        System.out.println("[DEBUG checkSbcRequirements] No match - " + courseCode + " does not satisfy " + category);
                                    }
                                } else {
                                    System.out.println("[DEBUG checkSbcRequirements] In-progress course NOT found in repository: " + courseCode);
                                }
                            }
                        }
                    }
                    
                    // Check planned courses
                    // For ARTS, GLO, HUM, LANG etc., check each planned course's sbcs_satisfied field from scrapped courses
                    System.out.println("[DEBUG checkSbcRequirements] hasAnyCourseRequirements=false, checking planned courses. sbcMet=" + sbcMet + ", plannedCourses size=" + (plannedCourses != null ? plannedCourses.size() : "null"));
                    if (!sbcMet && plannedCourses != null && !plannedCourses.isEmpty()) {
                        System.out.println("[DEBUG checkSbcRequirements] Checking " + plannedCourses.size() + " planned courses for category: " + category);
                        for (UserCoursePlan plan : plannedCourses) {
                            if (plan != null && plan.getDepartment() != null && plan.getCourseNum() != null) {
                                String courseCode = plan.getDepartment() + " " + plan.getCourseNum();
                                System.out.println("[DEBUG checkSbcRequirements] Looking up planned course in repository: " + courseCode);
                            Optional<Course> courseOpt = resolveCourse(
                                plan.getDepartment(), plan.getCourseNum().toString());
                            if (courseOpt.isPresent()) {
                                Course course = courseOpt.get();
                                String sbcsSatisfied = course.getSbcsSatisfied();
                                    System.out.println("[DEBUG checkSbcRequirements] Planned course found - " + courseCode + ", sbcs_satisfied: " + sbcsSatisfied);
                                if (sbcsSatisfied != null && !sbcsSatisfied.equals("unknown") && 
                                    hasSbcCategory(sbcsSatisfied, category)) {
                                        System.out.println("[DEBUG checkSbcRequirements] MATCH! " + courseCode + " satisfies " + category);
                                    sbcMet = true;
                                        satisfiedRequiredCourses.add(courseCode);
                                    break; // Only need one
                                    } else {
                                        System.out.println("[DEBUG checkSbcRequirements] No match - " + courseCode + " does not satisfy " + category);
                                }
                            } else {
                                    System.out.println("[DEBUG checkSbcRequirements] Planned course NOT found in repository: " + courseCode);
                            }
                        }
                        }
                    } else if (plannedCourses == null || plannedCourses.isEmpty()) {
                        System.out.println("[DEBUG checkSbcRequirements] No planned courses to check for category: " + category);
                    }
                } else {
                    // Even if specific courses are required, also check if any planned/taken/enrolled course satisfies the SBC
                    // This is for cases where a planned/enrolled course satisfies the SBC but is not in the required list
                    // Check completed courses
                    boolean completedCoursesSatisfySbc = false;
                    if (completedCourses != null) {
                    for (UserClasses userClass : completedCourses) {
                        Optional<Course> courseOpt = resolveCourse(
                            userClass.getDepartment(), userClass.getCourseNum().toString());
                        if (courseOpt.isPresent()) {
                            Course course = courseOpt.get();
                            String sbcsSatisfied = course.getSbcsSatisfied();
                            if (sbcsSatisfied != null && !sbcsSatisfied.equals("unknown") && 
                                hasSbcCategory(sbcsSatisfied, category)) {
                                String courseCode = userClass.getDepartment() + " " + userClass.getCourseNum();
                                if (!satisfiedRequiredCourses.contains(courseCode)) {
                                    satisfiedRequiredCourses.add(courseCode);
                                    }
                                completedCoursesSatisfySbc = true;
                                }
                            }
                        }
                    }
                    
                    // Check enrolled courses (in-progress)
                    boolean enrolledCoursesSatisfySbc = false;
                    if (enrolledRosters != null) {
                        for (ClassRoster roster : enrolledRosters) {
                            ClassEntity classEntity = roster.getClassEntity();
                            if (classEntity != null && classEntity.getDepartment() != null && classEntity.getCourseNum() != null) {
                                Optional<Course> courseOpt = resolveCourse(
                                    classEntity.getDepartment(), classEntity.getCourseNum().toString());
                                if (courseOpt.isPresent()) {
                                    Course course = courseOpt.get();
                                    String sbcsSatisfied = course.getSbcsSatisfied();
                                    if (sbcsSatisfied != null && !sbcsSatisfied.equals("unknown") && 
                                        hasSbcCategory(sbcsSatisfied, category)) {
                                        String courseCode = classEntity.getDepartment() + " " + classEntity.getCourseNum();
                                        if (!satisfiedRequiredCourses.contains(courseCode)) {
                                            satisfiedRequiredCourses.add(courseCode);
                                        }
                                        enrolledCoursesSatisfySbc = true;
                                    }
                                }
                            }
                        }
                    }
                    
                    // Check in-progress courses from user_classes (grade: null)
                    boolean inProgressCoursesSatisfySbc = false;
                    if (allUserClasses != null) {
                        for (UserClasses userClass : allUserClasses) {
                            // Only check courses with grade: null (in-progress)
                            if (userClass.getGrade() == null || userClass.getGrade().trim().isEmpty()) {
                                Optional<Course> courseOpt = resolveCourse(
                                    userClass.getDepartment(), userClass.getCourseNum().toString());
                                if (courseOpt.isPresent()) {
                                    Course course = courseOpt.get();
                                    String sbcsSatisfied = course.getSbcsSatisfied();
                                    if (sbcsSatisfied != null && !sbcsSatisfied.equals("unknown") && 
                                        hasSbcCategory(sbcsSatisfied, category)) {
                                        String courseCode = userClass.getDepartment() + " " + userClass.getCourseNum();
                                        if (!satisfiedRequiredCourses.contains(courseCode)) {
                                            satisfiedRequiredCourses.add(courseCode);
                                        }
                                        inProgressCoursesSatisfySbc = true;
                                    }
                                }
                            }
                        }
                    }
                    
                    // Check planned courses
                    boolean plannedCoursesSatisfySbc = false;
                    System.out.println("[DEBUG checkSbcRequirements] hasAnyCourseRequirements=true, checking planned courses. plannedCourses size=" + (plannedCourses != null ? plannedCourses.size() : "null"));
                    if (plannedCourses != null) {
                        for (UserCoursePlan plan : plannedCourses) {
                            if (plan != null && plan.getDepartment() != null && plan.getCourseNum() != null) {
                            Optional<Course> courseOpt = resolveCourse(
                                plan.getDepartment(), plan.getCourseNum().toString());
                            if (courseOpt.isPresent()) {
                                Course course = courseOpt.get();
                                String sbcsSatisfied = course.getSbcsSatisfied();
                                System.out.println("[DEBUG checkSbcRequirements] Checking planned course (hasAnyCourseRequirements=true): " + 
                                    plan.getDepartment() + " " + plan.getCourseNum() + 
                                    ", sbcsSatisfied: " + sbcsSatisfied + ", category: " + category);
                                if (sbcsSatisfied != null && !sbcsSatisfied.equals("unknown") && 
                                    hasSbcCategory(sbcsSatisfied, category)) {
                                    System.out.println("[DEBUG checkSbcRequirements] Found SBC match for planned course: " + 
                                        plan.getDepartment() + " " + plan.getCourseNum());
                                    String courseCode = plan.getDepartment() + " " + plan.getCourseNum();
                                    if (!satisfiedRequiredCourses.contains(courseCode)) {
                                        satisfiedRequiredCourses.add(courseCode);
                                    }
                                    plannedCoursesSatisfySbc = true;
                                }
                            } else {
                                System.out.println("[DEBUG checkSbcRequirements] Course not found in repository: " + 
                                    plan.getDepartment() + " " + plan.getCourseNum());
                                }
                            }
                        }
                    }
                    // Check required_major_courses condition
                    boolean requiredConditionMet = true;
                    if (hasRequiredCourses) {
                        // Need at least one required_major_courses OR one optional_major_courses_partial
                        requiredConditionMet = requiredSatisfied || !satisfiedOptionalPartial.isEmpty();
                    }
                    
                    // Check required_major_courses_partial condition
                    boolean partialConditionMet = true;
                    if (hasPartialCourses) {
                        // If 2 courses, need both. If 3+, need 2.
                        partialConditionMet = partialRequiredSatisfied;
                    }
                    
                    // SBC is met if:
                    // 1. Required/partial conditions are met (if they exist), OR
                    // 2. Any completed/enrolled/in-progress/planned course satisfies the SBC
                    boolean anyCourseSatisfiesSbc = completedCoursesSatisfySbc || enrolledCoursesSatisfySbc || 
                                                    inProgressCoursesSatisfySbc || plannedCoursesSatisfySbc;
                    sbcMet = (requiredConditionMet && partialConditionMet) || anyCourseSatisfiesSbc;
                }
                
                status.put("met", sbcMet);
                status.put("satisfiedRequiredCourses", satisfiedRequiredCourses);
                status.put("satisfiedPartialRequired", satisfiedPartialRequired);
                status.put("missingPartialRequired", missingPartialRequired);
                status.put("satisfiedOptionalPartial", satisfiedOptionalPartial);
                
                sbcStatus.add(status);
            }
            
            result.put("sbcCategories", sbcStatus);
            System.out.println("[DEBUG checkSbcRequirements] Processed " + sbcStatus.size() + " SBC categories");
            
        } catch (Exception e) {
            System.err.println("[DEBUG checkSbcRequirements] Error parsing SBC requirements: " + e.getMessage());
            e.printStackTrace();
            result.put("status", "error");
            result.put("error", "Failed to parse SBC requirements: " + e.getMessage());
        }

        return result;
    }

    /**
     * Calculate GPA from completed courses.
     */
    private double calculateGPA(List<UserClasses> completedCourses) {
        double totalPoints = 0.0;
        int totalCredits = 0;
        
        for (UserClasses uc : completedCourses) {
            if (uc.getGrade() != null && uc.getCredits() != null) {
                double gradePoints = getGradePoints(uc.getGrade());
                totalPoints += gradePoints * uc.getCredits();
                totalCredits += uc.getCredits();
            }
        }
        
        if (totalCredits == 0) return 0.0;
        return totalPoints / totalCredits;
    }

    /**
     * Convert letter grade to grade points.
     */
    private double getGradePoints(String grade) {
        if (grade == null) return 0.0;
        
        grade = grade.trim().toUpperCase();
        switch (grade) {
            case "A+": case "A": return 4.0;
            case "A-": return 3.7;
            case "B+": return 3.3;
            case "B": return 3.0;
            case "B-": return 2.7;
            case "C+": return 2.3;
            case "C": return 2.0;
            case "C-": return 1.7;
            case "D+": return 1.3;
            case "D": return 1.0;
            case "D-": return 0.7;
            case "F": return 0.0;
            default: return 0.0;
        }
    }

    // Helper methods
    /**
     * Check if a course was completed BEFORE a given semester/year
     */
    private boolean hasCompletedCourse(List<UserClasses> completedCourses, String department, String courseNum, 
                                        String beforeSemester, Integer beforeYear) {
        for (UserClasses uc : completedCourses) {
            if (uc.getDepartment().equals(department) && 
                uc.getCourseNum().toString().equals(courseNum)) {
                // Check if this course was completed BEFORE the course being checked
                if (isCourseBefore(uc, beforeSemester, beforeYear)) {
                return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if a course was completed BEFORE or IN THE SAME semester/year (for corequisites)
     */
    private boolean hasCompletedCourseBeforeOrSame(List<UserClasses> completedCourses, String department, String courseNum,
                                                    String semester, Integer year) {
        for (UserClasses uc : completedCourses) {
            if (uc.getDepartment().equals(department) && 
                uc.getCourseNum().toString().equals(courseNum)) {
                // Check if this course was completed before or in the same semester
                if (uc.getYear() == null || year == null) {
                    continue;
                }
                if (uc.getYear() < year) {
                    return true; // Completed in earlier year
                }
                if (uc.getYear().equals(year)) {
                    // Same year - check semester
                    int ucSemesterOrder = getSemesterOrder(uc.getSemester());
                    int semesterOrder = getSemesterOrder(semester);
                    if (ucSemesterOrder <= semesterOrder) {
                        return true; // Completed in same or earlier semester
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if a course was completed with sufficient grade BEFORE a given semester/year
     */
    private boolean hasCompletedCourseWithGrade(List<UserClasses> completedCourses, String department, String courseNum,
                                                 String beforeSemester, Integer beforeYear) {
        for (UserClasses uc : completedCourses) {
            if (uc.getDepartment().equals(department) && 
                uc.getCourseNum().toString().equals(courseNum)) {
                String grade = uc.getGrade();
                if (grade != null && isGradeSufficient(grade)) {
                    // Check if this course was completed BEFORE the course being checked
                    if (isCourseBefore(uc, beforeSemester, beforeYear)) {
                    return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Check if a course was taken BEFORE a given semester/year
     * Returns true if the course was completed in an earlier semester/year
     */
    private boolean isCourseBefore(UserClasses course, String beforeSemester, Integer beforeYear) {
        if (course.getYear() == null || beforeYear == null) {
            return false; // Can't compare if year is null
        }
        
        // Compare years first
        if (course.getYear() < beforeYear) {
            return true; // Course was taken in an earlier year
        }
        if (course.getYear() > beforeYear) {
            return false; // Course was taken in a later year
        }
        
        // Same year - compare semesters
        // Order: Spring (1), Summer (2), Fall (3)
        int courseSemesterOrder = getSemesterOrder(course.getSemester());
        int beforeSemesterOrder = getSemesterOrder(beforeSemester);
        
        return courseSemesterOrder < beforeSemesterOrder;
    }
    
    /**
     * Get semester order for comparison (Spring=1, Summer=2, Fall=3)
     */
    private int getSemesterOrder(String semester) {
        if (semester == null) return 0;
        String lower = semester.toLowerCase();
        if (lower.contains("spring")) return 1;
        if (lower.contains("summer")) return 2;
        if (lower.contains("fall")) return 3;
        return 0; // Unknown semester
    }
    
    /**
     * Check if SBC string contains a specific SBC category.
     * Handles comma/semicolon-separated SBC codes and "Partially fulfills:" prefix.
     * Also handles simple format like "ARTS" or "SBC: ARTS".
     */
    private boolean hasSbcCategory(String sbcsSatisfied, String category) {
        if (sbcsSatisfied == null || category == null) {
            return false;
        }
        
        // Clean up the SBC string
        String cleaned = sbcsSatisfied.replace("Partially fulfills:", "")
                .replace("Partially fulfills", "")
                .replace("SBC:", "")
                .trim();
        
        // Check if the cleaned string directly matches the category (for simple cases like "ARTS")
        if (cleaned.toUpperCase().equals(category.toUpperCase())) {
            return true;
        }
        
        // Split by comma or semicolon
        String[] parts = cleaned.split("[,;]");
        for (String part : parts) {
            String token = part.trim().toUpperCase();
            if (token.equals(category.toUpperCase())) {
                return true;
            }
        }
        
        // Also check if category is contained in the string (for cases like "ARTS, GLO" or "ARTS; GLO")
        if (cleaned.toUpperCase().contains(category.toUpperCase())) {
            // Make sure it's a whole word match, not part of another word
            String upperCleaned = cleaned.toUpperCase();
            String upperCategory = category.toUpperCase();
            int index = upperCleaned.indexOf(upperCategory);
            if (index >= 0) {
                // Check if it's at the start or after a separator
                if (index == 0 || ",; ".indexOf(upperCleaned.charAt(index - 1)) >= 0) {
                    // Check if it's at the end or before a separator
                    int endIndex = index + upperCategory.length();
                    if (endIndex >= upperCleaned.length() || ",; ".indexOf(upperCleaned.charAt(endIndex)) >= 0) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get courses completed before a given semester/year
     */
    private List<UserClasses> getCoursesBeforeSemester(List<UserClasses> completedCourses, String semester, Integer year) {
        List<UserClasses> before = new ArrayList<>();
        for (UserClasses uc : completedCourses) {
            if (isCourseBefore(uc, semester, year)) {
                before.add(uc);
            }
        }
        return before;
    }
    
    /**
     * Check if a planned course is before a given semester/year
     */
    private boolean isPlannedCourseBefore(UserCoursePlan plan, String beforeSemester, Integer beforeYear) {
        if (plan.getYear() == null || beforeYear == null) {
            return false;
        }
        
        // Compare years first
        if (plan.getYear() < beforeYear) {
            return true;
        }
        if (plan.getYear() > beforeYear) {
            return false;
        }
        
        // Same year - compare semesters
        int planSemesterOrder = getSemesterOrder(plan.getSemester());
        int beforeSemesterOrder = getSemesterOrder(beforeSemester);
        
        return planSemesterOrder < beforeSemesterOrder;
    }

    private boolean isGradeSufficient(String grade) {
        if (grade == null) return false;
        grade = grade.trim().toUpperCase();
        return grade.equals("A") || grade.equals("A+") || grade.equals("A-") ||
               grade.equals("B") || grade.equals("B+") || grade.equals("B-") ||
               grade.equals("C") || grade.equals("C+");
    }

    private boolean checkStandingRequirement(String group, int totalCredits) {
        String lowerGroup = group.toLowerCase();
        String standing = calculateStanding(totalCredits);
        
        if (lowerGroup.contains("u4") || lowerGroup.contains("senior")) {
            return standing.equals("U4");
        } else if (lowerGroup.contains("u3") || lowerGroup.contains("junior")) {
            return standing.equals("U3") || standing.equals("U4");
        } else if (lowerGroup.contains("u2") || lowerGroup.contains("sophomore")) {
            return standing.equals("U2") || standing.equals("U3") || standing.equals("U4");
        } else if (lowerGroup.contains("u1") || lowerGroup.contains("freshman")) {
            return true;
        }
        return true;
    }

    private String calculateStanding(int credits) {
        if (credits < 24) return "U1";
        if (credits < 57) return "U2";
        if (credits < 85) return "U3";
        return "U4";
    }

    
    /**
     * Parse declared majors from User entity (JSON array stored as string).
     */
    private List<Map<String, Object>> parseDeclaredMajors(User student) {
        List<Map<String, Object>> declaredMajors = new ArrayList<>();
        
        if (student.getMajors() == null || student.getMajors().trim().isEmpty()) {
            System.out.println("[DEBUG parseDeclaredMajors] No majors JSON found for user " + student.getUserId());
            return declaredMajors;
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            List<String> majorsList = (List<String>) mapper.readValue(student.getMajors(), List.class);
            String degreesStr = student.getDegrees();
            @SuppressWarnings("unchecked")
            List<String> degreesList = degreesStr != null && !degreesStr.trim().isEmpty() 
                ? (List<String>) mapper.readValue(degreesStr, List.class) 
                : new ArrayList<>();
            
            System.out.println("[DEBUG parseDeclaredMajors] Parsed majors: " + majorsList);
            System.out.println("[DEBUG parseDeclaredMajors] Parsed degrees: " + degreesList);
            
            for (int i = 0; i < majorsList.size(); i++) {
                Map<String, Object> majorInfo = new HashMap<>();
                majorInfo.put("subjectCode", majorsList.get(i));
                // Default to BS if degrees list is shorter
                String degreeType = i < degreesList.size() ? degreesList.get(i) : "BS";
                majorInfo.put("degreeType", degreeType);
                declaredMajors.add(majorInfo);
                System.out.println("[DEBUG parseDeclaredMajors] Added major: " + majorsList.get(i) + " " + degreeType);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse declared majors for user " + student.getUserId() + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        return declaredMajors;
    }
    
    /**
     * Parse declared minors from User entity (JSON array stored as string).
     */
    private List<Map<String, Object>> parseDeclaredMinors(User student) {
        List<Map<String, Object>> declaredMinors = new ArrayList<>();
        
        if (student.getMinors() == null || student.getMinors().trim().isEmpty()) {
            return declaredMinors;
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            List<String> minorsList = (List<String>) mapper.readValue(student.getMinors(), List.class);
            
            for (String minorCode : minorsList) {
                Map<String, Object> minorInfo = new HashMap<>();
                minorInfo.put("subjectCode", minorCode);
                minorInfo.put("degreeType", "MINOR");
                declaredMinors.add(minorInfo);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse declared minors for user " + student.getUserId() + ": " + e.getMessage());
        }
        
        return declaredMinors;
    }
    
    /**
     * Parse planned minors from User entity (JSON array stored as string).
     */
    private List<Map<String, Object>> parsePlannedMinors(User student) {
        List<Map<String, Object>> plannedMinors = new ArrayList<>();
        
        if (student.getPlannedMinors() == null || student.getPlannedMinors().trim().isEmpty()) {
            return plannedMinors;
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            List<String> minorsList = (List<String>) mapper.readValue(student.getPlannedMinors(), List.class);
            
            for (String minorCode : minorsList) {
                Map<String, Object> minorInfo = new HashMap<>();
                minorInfo.put("subjectCode", minorCode);
                minorInfo.put("degreeType", "MINOR");
                plannedMinors.add(minorInfo);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse planned minors for user " + student.getUserId() + ": " + e.getMessage());
        }
        
        return plannedMinors;
    }
    
    /**
     * Calculate requirement status for a major/minor.
     */
    private Map<String, Object> calculateRequirementStatus(String subjectCode, 
                                                          DegreeType degreeType,
                                                          DegreeRequirement requirement,
                                                          Set<String> allCourses,
                                                          List<UserClasses> completedCourses,
                                                          List<UserClasses> allUserClasses,
                                                          List<UserCoursePlan> plannedCourses,
                                                          int totalCredits,
                                                          List<ClassRoster> enrolledRosters) {
        Map<String, Object> status = new HashMap<>();
        status.put("subjectCode", subjectCode);
        status.put("degreeType", degreeType.toString());
        status.put("requirementName", subjectCode + " " + degreeType);

        // Debug: Log planned courses received in calculateRequirementStatus
        System.out.println("[DEBUG calculateRequirementStatus] ===== START for " + subjectCode + " " + degreeType + " =====");
        System.out.println("[DEBUG calculateRequirementStatus] Received plannedCourses size: " + (plannedCourses != null ? plannedCourses.size() : "null"));
        if (plannedCourses != null && !plannedCourses.isEmpty()) {
            for (UserCoursePlan plan : plannedCourses) {
                System.out.println("[DEBUG calculateRequirementStatus] Planned course: " + 
                    plan.getDepartment() + " " + plan.getCourseNum() + " (" + plan.getSemester() + " " + plan.getYear() + ")");
            }
        }

        // Parse and check course requirements first to get satisfied courses
        // Pass enrolled courses info - they should be treated as in-progress
        // Pass allUserClasses so in-progress courses (grade: null) can be detected
        Map<String, Object> courseRequirementsStatus = checkCourseRequirements(
            requirement.getCourseRequirements(), allCourses, completedCourses, allUserClasses, new ArrayList<>(), enrolledRosters);
        status.put("courseRequirements", courseRequirementsStatus);
        
        // Calculate credits specific to this major/minor
        int specificCredits = calculateSpecificCredits(
            subjectCode, degreeType, courseRequirementsStatus, completedCourses, allUserClasses, enrolledRosters);
        
        // For minors, use specific credits; for majors, use total credits (as they need overall graduation credits)
        int creditsToUse = (degreeType == DegreeType.MINOR) ? specificCredits : totalCredits;
        
        // Check total credits requirement
        boolean totalCreditsMet = creditsToUse >= requirement.getTotalCreditsRequired();
        status.put("totalCreditsRequired", requirement.getTotalCreditsRequired());
        status.put("totalCreditsEarned", creditsToUse);
        status.put("totalCreditsMet", totalCreditsMet);

        // Parse and check SBC requirements
        // Note: For SBC checking, we include planned courses (assuming they will be completed with adequate grades)
        System.out.println("[DEBUG calculateRequirementStatus] Checking SBC for " + subjectCode + " " + degreeType);
        System.out.println("[DEBUG calculateRequirementStatus] SBC requirements JSON: " + (requirement.getSbcRequirements() != null ? requirement.getSbcRequirements().substring(0, Math.min(200, requirement.getSbcRequirements().length())) : "null"));
        System.out.println("[DEBUG calculateRequirementStatus] plannedCourses size before checkSbcRequirements: " + (plannedCourses != null ? plannedCourses.size() : "null"));
        System.out.println("[DEBUG calculateRequirementStatus] About to call checkSbcRequirements");
        Map<String, Object> sbcRequirementsStatus = checkSbcRequirements(
            requirement.getSbcRequirements(), allCourses, completedCourses, allUserClasses, plannedCourses, enrolledRosters);
        System.out.println("[DEBUG calculateRequirementStatus] checkSbcRequirements returned");
        status.put("sbcRequirements", sbcRequirementsStatus);
        System.out.println("[DEBUG] SBC status result: " + sbcRequirementsStatus);

        // Check GPA requirement (if applicable)
        if (requirement.getGpaRequirement() != null) {
            double gpa = calculateGPA(completedCourses);
            status.put("gpaRequirement", requirement.getGpaRequirement());
            status.put("currentGPA", gpa);
            status.put("gpaMet", gpa >= requirement.getGpaRequirement());
        }

        return status;
    }
    
    /**
     * Calculate credits specific to a major/minor based on satisfied courses.
     */
    @SuppressWarnings("unchecked")
    private int calculateSpecificCredits(String subjectCode,
                                         DegreeType degreeType,
                                         Map<String, Object> courseRequirementsStatus,
                                         List<UserClasses> completedCourses,
                                         List<UserClasses> allUserClasses,
                                         List<ClassRoster> enrolledRosters) {
        int credits = 0;
        Set<String> satisfiedCourseCodes = new HashSet<>();
        Set<String> processedCourses = new HashSet<>(); // To avoid counting the same course twice
        
        // Collect all satisfied courses from course requirements
        if (courseRequirementsStatus.containsKey("groups")) {
            List<Map<String, Object>> groups = (List<Map<String, Object>>) courseRequirementsStatus.get("groups");
            for (Map<String, Object> group : groups) {
                List<String> satisfiedCourses = (List<String>) group.get("satisfiedCourses");
                if (satisfiedCourses != null) {
                    satisfiedCourseCodes.addAll(satisfiedCourses);
                }
                List<String> pendingCourses = (List<String>) group.get("pendingCourses");
                if (pendingCourses != null) {
                    satisfiedCourseCodes.addAll(pendingCourses);
                }
            }
        }
        
        // Calculate credits from satisfied courses (avoid double counting)
        for (String courseCode : satisfiedCourseCodes) {
            if (processedCourses.contains(courseCode)) {
                continue; // Already processed this course
            }
            
            String[] parts = parseCourseCode(courseCode);
            String dept = parts[0];
            String courseNumStr = parts[1];
            boolean found = false;
            
            // Check completed courses
            for (UserClasses uc : completedCourses) {
                if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) &&
                    uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr) &&
                    uc.getCredits() != null) {
                    credits += uc.getCredits();
                    processedCourses.add(courseCode);
                    found = true;
                    break;
                }
            }
            
            if (found) continue;
            
            // Check in-progress courses (grade: null)
            for (UserClasses uc : allUserClasses) {
                if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) &&
                    uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr) &&
                    uc.getCredits() != null && (uc.getGrade() == null || uc.getGrade().trim().isEmpty())) {
                    credits += uc.getCredits();
                    processedCourses.add(courseCode);
                    found = true;
                    break;
                }
            }
            
            if (found) continue;
            
            // Check enrolled courses
            for (ClassRoster roster : enrolledRosters) {
                ClassEntity classEntity = roster.getClassEntity();
                if (classEntity != null &&
                    classEntity.getDepartment() != null && classEntity.getDepartment().equalsIgnoreCase(dept) &&
                    classEntity.getCourseNum() != null && classEntity.getCourseNum().toString().equals(courseNumStr) &&
                    classEntity.getMinCredits() != null) {
                    credits += classEntity.getMinCredits();
                    processedCourses.add(courseCode);
                    break;
                }
            }
        }
        
        return credits;
    }
    
    /**
     * Identify non-required classes in the plan.
     * Non-required classes are those that do not contribute to:
     * - Satisfying prerequisites for other courses (planned or taken)
     * - Satisfying degree requirements (except minimum credits for graduation)
     * 
     * These classes only contribute to the minimum required credits for graduation.
     * Checks both planned courses and taken courses (UserClasses).
     */
    private List<Map<String, Object>> identifyNonRequiredClasses(Long userId, 
                                                                  List<UserCoursePlan> plannedCourses) {
        List<Map<String, Object>> nonRequired = new ArrayList<>();
        
        System.out.println("[DEBUG identifyNonRequiredClasses] ===== START =====");
        System.out.println("[DEBUG identifyNonRequiredClasses] User ID: " + userId);
        
        // Get all UserClasses (taken courses)
        List<UserClasses> allUserClasses = userClassesRepository.findByUserId(userId);
        System.out.println("[DEBUG identifyNonRequiredClasses] allUserClasses size: " + (allUserClasses != null ? allUserClasses.size() : "null"));
        
        // Get enrolled courses (from ClassRoster)
        User student = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Student not found: " + userId));
        List<ClassRoster> enrolledRosters = classRosterRepository.findActiveEnrollmentsByUser(student);
        System.out.println("[DEBUG identifyNonRequiredClasses] enrolledRosters size: " + (enrolledRosters != null ? enrolledRosters.size() : "null"));
        System.out.println("[DEBUG identifyNonRequiredClasses] plannedCourses size: " + (plannedCourses != null ? plannedCourses.size() : "null"));
        
        if ((plannedCourses == null || plannedCourses.isEmpty()) && 
            (allUserClasses == null || allUserClasses.isEmpty()) && 
            (enrolledRosters == null || enrolledRosters.isEmpty())) {
            System.out.println("[DEBUG identifyNonRequiredClasses] All course lists are empty, returning empty list");
            return nonRequired;
        }
        
        // Get all courses that are needed for prerequisites (from both planned and taken courses)
        Set<String> coursesNeededForPrerequisites = getCoursesNeededForPrerequisites(userId, plannedCourses, allUserClasses);
        System.out.println("[DEBUG identifyNonRequiredClasses] coursesNeededForPrerequisites: " + coursesNeededForPrerequisites);
        
        // Get all courses that satisfy degree requirements
        Set<String> coursesNeededForDegreeRequirements = getCoursesNeededForDegreeRequirements(userId, plannedCourses);
        System.out.println("[DEBUG identifyNonRequiredClasses] coursesNeededForDegreeRequirements: " + coursesNeededForDegreeRequirements);
        
        // Check each planned course
        if (plannedCourses != null && !plannedCourses.isEmpty()) {
            System.out.println("[DEBUG identifyNonRequiredClasses] Checking " + plannedCourses.size() + " planned courses");
        for (UserCoursePlan plan : plannedCourses) {
                if (plan == null || plan.getDepartment() == null || plan.getCourseNum() == null) {
                    continue;
                }
            String courseCode = plan.getDepartment() + " " + plan.getCourseNum();
                System.out.println("[DEBUG identifyNonRequiredClasses] Checking planned course: " + courseCode);
            
            // Check if this course is needed for prerequisites or degree requirements
            boolean neededForPrerequisites = coursesNeededForPrerequisites.contains(courseCode);
            boolean neededForDegreeRequirements = coursesNeededForDegreeRequirements.contains(courseCode);
                System.out.println("[DEBUG identifyNonRequiredClasses] " + courseCode + " - neededForPrerequisites: " + neededForPrerequisites + ", neededForDegreeRequirements: " + neededForDegreeRequirements);
            
            // If not needed for either, it's a non-required class
            if (!neededForPrerequisites && !neededForDegreeRequirements) {
                    System.out.println("[DEBUG identifyNonRequiredClasses] " + courseCode + " is NON-REQUIRED (planned)");
                Map<String, Object> nonRequiredClass = new HashMap<>();
                nonRequiredClass.put("course", courseCode);
                
                // Get course name if available
                Optional<Course> courseOpt = resolveCourse(
                    plan.getDepartment(), plan.getCourseNum().toString());
                if (courseOpt.isPresent()) {
                    nonRequiredClass.put("courseName", courseOpt.get().getCourseName());
                }
                
                nonRequiredClass.put("semester", plan.getSemester());
                nonRequiredClass.put("year", plan.getYear());
                nonRequiredClass.put("credits", plan.getCredits());
                nonRequiredClass.put("reason", "This course does not contribute to satisfying prerequisites or degree requirements (only counts toward minimum graduation credits)");
                
                nonRequired.add(nonRequiredClass);
                }
            }
        }
        
        // Check enrolled courses (from ClassRoster)
        if (enrolledRosters != null && !enrolledRosters.isEmpty()) {
            System.out.println("[DEBUG identifyNonRequiredClasses] Checking " + enrolledRosters.size() + " enrolled courses");
            for (ClassRoster roster : enrolledRosters) {
                ClassEntity classEntity = roster.getClassEntity();
                if (classEntity == null || classEntity.getDepartment() == null || classEntity.getCourseNum() == null) {
                    continue;
                }
                String courseCode = classEntity.getDepartment() + " " + classEntity.getCourseNum();
                System.out.println("[DEBUG identifyNonRequiredClasses] Checking enrolled course: " + courseCode);
                
                // Check if this course is needed for prerequisites or degree requirements
                boolean neededForPrerequisites = coursesNeededForPrerequisites.contains(courseCode);
                boolean neededForDegreeRequirements = coursesNeededForDegreeRequirements.contains(courseCode);
                System.out.println("[DEBUG identifyNonRequiredClasses] " + courseCode + " - neededForPrerequisites: " + neededForPrerequisites + ", neededForDegreeRequirements: " + neededForDegreeRequirements);
                
                // If not needed for either, it's a non-required class
                if (!neededForPrerequisites && !neededForDegreeRequirements) {
                    System.out.println("[DEBUG identifyNonRequiredClasses] " + courseCode + " is NON-REQUIRED (enrolled)");
                    Map<String, Object> nonRequiredClass = new HashMap<>();
                    nonRequiredClass.put("course", courseCode);
                    
                    // Get course name if available
                    Optional<Course> courseOpt = resolveCourse(
                        classEntity.getDepartment(), classEntity.getCourseNum().toString());
                    if (courseOpt.isPresent()) {
                        nonRequiredClass.put("courseName", courseOpt.get().getCourseName());
                    }
                    
                    nonRequiredClass.put("status", "enrolled");
                    nonRequiredClass.put("reason", "This course does not contribute to satisfying prerequisites or degree requirements (only counts toward minimum graduation credits)");
                    
                    nonRequired.add(nonRequiredClass);
                }
            }
        }
        
        // Also check in-progress courses (UserClasses with grade null) - NOT completed courses
        // Completed courses have already contributed to degree requirements, so we don't mark them as non-required
        List<UserClasses> inProgressClasses = allUserClasses != null ? 
            allUserClasses.stream()
                .filter(uc -> uc.getGrade() == null || uc.getGrade().trim().isEmpty() || uc.getGrade().equals("I"))
                .collect(java.util.stream.Collectors.toList()) : new ArrayList<>();
        
        if (!inProgressClasses.isEmpty()) {
            System.out.println("[DEBUG identifyNonRequiredClasses] Checking " + inProgressClasses.size() + " in-progress courses");
            for (UserClasses userClass : inProgressClasses) {
                String courseCode = userClass.getDepartment() + " " + userClass.getCourseNum();
                System.out.println("[DEBUG identifyNonRequiredClasses] Checking in-progress course: " + courseCode);
            
                // Check if this course is needed for prerequisites or degree requirements
                boolean neededForPrerequisites = coursesNeededForPrerequisites.contains(courseCode);
                boolean neededForDegreeRequirements = coursesNeededForDegreeRequirements.contains(courseCode);
                System.out.println("[DEBUG identifyNonRequiredClasses] " + courseCode + " - neededForPrerequisites: " + neededForPrerequisites + ", neededForDegreeRequirements: " + neededForDegreeRequirements);
            
                // If not needed for either, it's a non-required class
                if (!neededForPrerequisites && !neededForDegreeRequirements) {
                    System.out.println("[DEBUG identifyNonRequiredClasses] " + courseCode + " is NON-REQUIRED (in-progress)");
                    Map<String, Object> nonRequiredClass = new HashMap<>();
                    nonRequiredClass.put("course", courseCode);
                
                    // Get course name if available
                    Optional<Course> courseOpt = resolveCourse(
                        userClass.getDepartment(), userClass.getCourseNum().toString());
                    if (courseOpt.isPresent()) {
                        nonRequiredClass.put("courseName", courseOpt.get().getCourseName());
                    }
                
                    nonRequiredClass.put("semester", userClass.getSemester());
                    nonRequiredClass.put("year", userClass.getYear());
                    nonRequiredClass.put("credits", userClass.getCredits());
                    nonRequiredClass.put("grade", userClass.getGrade());
                    nonRequiredClass.put("status", "in-progress");
                    nonRequiredClass.put("reason", "This course does not contribute to satisfying prerequisites or degree requirements (only counts toward minimum graduation credits)");
                
                    nonRequired.add(nonRequiredClass);
                }
            }
        } else {
            System.out.println("[DEBUG identifyNonRequiredClasses] No in-progress courses to check");
        }
        
        System.out.println("[DEBUG identifyNonRequiredClasses] Total non-required classes found: " + nonRequired.size());
        System.out.println("[DEBUG identifyNonRequiredClasses] ===== END =====");
        
        return nonRequired;
    }
    
    /**
     * Get all courses that are needed as prerequisites for other courses (planned or taken).
     */
    private Set<String> getCoursesNeededForPrerequisites(Long userId, List<UserCoursePlan> plannedCourses, List<UserClasses> allUserClasses) {
        Set<String> neededCourses = new HashSet<>();
        
        // Get completed courses
        List<UserClasses> completedCourses = userClassesRepository.findCompletedClassesByUserId(userId);
        Set<String> availableCourses = new HashSet<>();
        for (UserClasses uc : completedCourses) {
            availableCourses.add(uc.getDepartment() + " " + uc.getCourseNum());
        }
        
        // For each planned course, check what prerequisites it needs
        for (UserCoursePlan plan : plannedCourses) {
            Optional<Course> courseOpt = resolveCourse(
                plan.getDepartment(), plan.getCourseNum().toString());
            
            if (courseOpt.isEmpty()) {
                continue;
            }
            
            Course course = courseOpt.get();
            String prerequisites = course.getPrerequisites();
            String corequisites = course.getCorequisites();
            
            if (prerequisites != null && !prerequisites.trim().isEmpty()) {
                // Extract course codes from prerequisites
                Matcher matcher = COURSE_PATTERN.matcher(prerequisites);
                while (matcher.find()) {
                    String dept = matcher.group(1);
                    String courseNum = matcher.group(2);
                    String prereqCode = dept + " " + courseNum;
                    
                    // If prerequisite is not already completed, it's needed from planned courses
                    if (!availableCourses.contains(prereqCode)) {
                        neededCourses.add(prereqCode);
                    }
                }
            }
            
            if (corequisites != null && !corequisites.trim().isEmpty()) {
                // Extract course codes from corequisites
                Matcher matcher = COURSE_PATTERN.matcher(corequisites);
                while (matcher.find()) {
                    String dept = matcher.group(1);
                    String courseNum = matcher.group(2);
                    String coreqCode = dept + " " + courseNum;
                    
                    // If corequisite is not already completed, it's needed from planned courses
                    if (!availableCourses.contains(coreqCode)) {
                        neededCourses.add(coreqCode);
                    }
                }
            }
            
            // Add current course to available courses for next iteration
            availableCourses.add(plan.getDepartment() + " " + plan.getCourseNum());
        }
        
        // Also check taken courses (UserClasses) - they might be prerequisites for other courses
        for (UserClasses userClass : allUserClasses) {
                    Optional<Course> courseOpt = resolveCourse(
                userClass.getDepartment(), userClass.getCourseNum().toString());
            
            if (courseOpt.isEmpty()) {
                continue;
            }
            
            Course course = courseOpt.get();
            String prerequisites = course.getPrerequisites();
            String corequisites = course.getCorequisites();
            
            if (prerequisites != null && !prerequisites.trim().isEmpty()) {
                // Extract course codes from prerequisites
                Matcher matcher = COURSE_PATTERN.matcher(prerequisites);
                while (matcher.find()) {
                    String dept = matcher.group(1);
                    String courseNum = matcher.group(2);
                    String prereqCode = dept + " " + courseNum;
                    
                    // If prerequisite is not already completed, it's needed
                    if (!availableCourses.contains(prereqCode)) {
                        neededCourses.add(prereqCode);
                    }
                }
            }
            
            if (corequisites != null && !corequisites.trim().isEmpty()) {
                // Extract course codes from corequisites
                Matcher matcher = COURSE_PATTERN.matcher(corequisites);
                while (matcher.find()) {
                    String dept = matcher.group(1);
                    String courseNum = matcher.group(2);
                    String coreqCode = dept + " " + courseNum;
                    
                    // If corequisite is not already completed, it's needed
                    if (!availableCourses.contains(coreqCode)) {
                        neededCourses.add(coreqCode);
                    }
                }
            }
            
            // Add current course to available courses for next iteration
            availableCourses.add(userClass.getDepartment() + " " + userClass.getCourseNum());
        }
        
        return neededCourses;
    }
    
    /**
     * Get all courses that satisfy degree requirements.
     * This checks if a course is part of any degree requirement group.
     */
    private Set<String> getCoursesNeededForDegreeRequirements(Long userId, List<UserCoursePlan> plannedCourses) {
        Set<String> neededCourses = new HashSet<>();
        
        // Get student
        User student = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Student not found: " + userId));
        
        // Get declared and planned majors
        List<PlannedMajor> plannedMajors = plannedMajorRepository.findActiveByStudentId(userId);
        List<Map<String, Object>> declaredMajors = parseDeclaredMajors(student);
        
        // Get completed courses
        List<UserClasses> completedCourses = userClassesRepository.findCompletedClassesByUserId(userId);
        // Get enrolled courses
        List<ClassRoster> enrolledRosters = classRosterRepository.findActiveEnrollmentsByUser(student);
        Set<String> allCourses = new HashSet<>();
        for (UserClasses uc : completedCourses) {
            allCourses.add(uc.getDepartment() + " " + uc.getCourseNum());
        }
        // Add enrolled courses to allCourses
        for (ClassRoster roster : enrolledRosters) {
            ClassEntity classEntity = roster.getClassEntity();
            if (classEntity != null && classEntity.getDepartment() != null && classEntity.getCourseNum() != null) {
                allCourses.add(classEntity.getDepartment() + " " + classEntity.getCourseNum());
            }
        }
        for (UserCoursePlan plan : plannedCourses) {
            if (plan != null && plan.getDepartment() != null && plan.getCourseNum() != null) {
            allCourses.add(plan.getDepartment() + " " + plan.getCourseNum());
            }
        }
        
        // Check each major's requirements
        List<Map<String, Object>> allMajors = new ArrayList<>();
        allMajors.addAll(declaredMajors);
        for (PlannedMajor pm : plannedMajors) {
            Map<String, Object> majorInfo = new HashMap<>();
            majorInfo.put("subjectCode", pm.getSubjectCode());
            majorInfo.put("degreeType", pm.getDegreeType().toString());
            allMajors.add(majorInfo);
        }
        
        for (Map<String, Object> majorInfo : allMajors) {
            String subjectCode = (String) majorInfo.get("subjectCode");
            String degreeTypeStr = (String) majorInfo.get("degreeType");
            
            DegreeType degreeType;
            try {
                degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                continue;
            }
            
            Optional<DegreeRequirement> requirementOpt = degreeRequirementRepository
                .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);
            
            if (requirementOpt.isEmpty()) {
                continue;
            }
            
            DegreeRequirement requirement = requirementOpt.get();
            String courseRequirementsJson = requirement.getCourseRequirements();
            
            if (courseRequirementsJson == null || courseRequirementsJson.trim().isEmpty()) {
                continue;
            }
            
            try {
                ObjectMapper mapper = new ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, Object> requirements = (Map<String, Object>) mapper.readValue(courseRequirementsJson, Map.class);
                
                // Check if using new groups format
                if (requirements.containsKey("groups")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> groups = (List<Map<String, Object>>) requirements.get("groups");
                    
                    for (Map<String, Object> group : groups) {
                        extractCourseCodesFromGroup(group, neededCourses);
                        
                        // For electives, also check if planned courses match the elective criteria
                        String groupType = (String) group.get("type");
                        if ("electives".equals(groupType)) {
                            extractElectiveCoursesFromGroup(group, plannedCourses, neededCourses);
                        }
                    }
                } else {
                    // Old format: check core_courses, math_requirements, etc.
                    extractCourseCodesFromOldFormat(requirements, neededCourses);
                }
            } catch (Exception e) {
                // Skip if parsing fails
            }
            
            // Also check SBC requirements
            String sbcRequirementsJson = requirement.getSbcRequirements();
            if (sbcRequirementsJson != null && !sbcRequirementsJson.trim().isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> sbcReqs = (List<Map<String, Object>>) mapper.readValue(sbcRequirementsJson, List.class);
                    
                    for (Map<String, Object> sbcReq : sbcReqs) {
                        String category = (String) sbcReq.get("sbc");
                        if (category == null) {
                            category = (String) sbcReq.get("category");
                        }
                        if (category == null) continue;
                        
                        // Extract courses from SBC requirements
                        // 1. required_major_courses
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> requiredMajorCourses = (List<Map<String, Object>>) sbcReq.get("required_major_courses");
                        if (requiredMajorCourses != null) {
                            for (Map<String, Object> courseReq : requiredMajorCourses) {
                                String code = (String) courseReq.get("code");
                                if (code != null) {
                                    neededCourses.add(code);
                                }
                            }
                        }
                        
                        // 2. required_major_courses_partial
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> requiredMajorCoursesPartial = (List<Map<String, Object>>) sbcReq.get("required_major_courses_partial");
                        if (requiredMajorCoursesPartial != null) {
                            for (Map<String, Object> courseReq : requiredMajorCoursesPartial) {
                                String code = (String) courseReq.get("code");
                                if (code != null) {
                                    neededCourses.add(code);
                                }
                            }
                        }
                        
                        // 3. optional_major_courses_partial
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> optionalMajorCoursesPartial = (List<Map<String, Object>>) sbcReq.get("optional_major_courses_partial");
                        if (optionalMajorCoursesPartial != null) {
                            for (Map<String, Object> courseReq : optionalMajorCoursesPartial) {
                                String code = (String) courseReq.get("code");
                                if (code != null) {
                                    neededCourses.add(code);
                                }
                            }
                        }
                        
                        // 4. Find all courses (planned and taken) that satisfy this SBC category
                        // This is needed regardless of whether specific courses are required,
                        // because any course that satisfies the SBC is considered "needed" for degree requirements
                        
                        // Check planned courses
                        if (plannedCourses != null) {
                        for (UserCoursePlan plan : plannedCourses) {
                                if (plan != null && plan.getDepartment() != null && plan.getCourseNum() != null) {
                            Optional<Course> courseOpt = resolveCourse(
                                plan.getDepartment(), plan.getCourseNum().toString());
                            if (courseOpt.isPresent()) {
                                Course course = courseOpt.get();
                                String sbcsSatisfied = course.getSbcsSatisfied();
                                if (sbcsSatisfied != null && !sbcsSatisfied.equals("unknown") && 
                                    hasSbcCategory(sbcsSatisfied, category)) {
                                    neededCourses.add(plan.getDepartment() + " " + plan.getCourseNum());
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Check completed courses
                        if (completedCourses != null) {
                        for (UserClasses userClass : completedCourses) {
                            Optional<Course> courseOpt = resolveCourse(
                                userClass.getDepartment(), userClass.getCourseNum().toString());
                            if (courseOpt.isPresent()) {
                                Course course = courseOpt.get();
                                String sbcsSatisfied = course.getSbcsSatisfied();
                                if (sbcsSatisfied != null && !sbcsSatisfied.equals("unknown") && 
                                    hasSbcCategory(sbcsSatisfied, category)) {
                                    neededCourses.add(userClass.getDepartment() + " " + userClass.getCourseNum());
                                    }
                                }
                            }
                        }
                        
                        // Check enrolled courses (from ClassRoster)
                        if (enrolledRosters != null) {
                            for (ClassRoster roster : enrolledRosters) {
                                ClassEntity classEntity = roster.getClassEntity();
                                if (classEntity != null && classEntity.getDepartment() != null && classEntity.getCourseNum() != null) {
                                    Optional<Course> courseOpt = resolveCourse(
                                        classEntity.getDepartment(), classEntity.getCourseNum().toString());
                                    if (courseOpt.isPresent()) {
                                        Course course = courseOpt.get();
                                        String sbcsSatisfied = course.getSbcsSatisfied();
                                        if (sbcsSatisfied != null && !sbcsSatisfied.equals("unknown") && 
                                            hasSbcCategory(sbcsSatisfied, category)) {
                                            neededCourses.add(classEntity.getDepartment() + " " + classEntity.getCourseNum());
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Also check in-progress courses (UserClasses with grade null)
                        List<UserClasses> allUserClasses = userClassesRepository.findByUserId(userId);
                        if (allUserClasses != null) {
                        for (UserClasses userClass : allUserClasses) {
                            if (userClass.getGrade() == null) { // In-progress
                                Optional<Course> courseOpt = resolveCourse(
                                    userClass.getDepartment(), userClass.getCourseNum().toString());
                                if (courseOpt.isPresent()) {
                                    Course course = courseOpt.get();
                                    String sbcsSatisfied = course.getSbcsSatisfied();
                                    if (sbcsSatisfied != null && !sbcsSatisfied.equals("unknown") && 
                                        hasSbcCategory(sbcsSatisfied, category)) {
                                        neededCourses.add(userClass.getDepartment() + " " + userClass.getCourseNum());
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Skip if parsing fails
                }
            }
        }
        
        return neededCourses;
    }
    
    /**
     * Extract course codes from a requirement group.
     */
    @SuppressWarnings("unchecked")
    private void extractCourseCodesFromGroup(Map<String, Object> group, Set<String> courseCodes) {
        String type = (String) group.get("type");
        
        if ("all_of".equals(type) || "one_of".equals(type) || "two_of".equals(type)) {
            List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
            if (courses != null) {
                for (Map<String, Object> courseReq : courses) {
                    String code = (String) courseReq.get("code");
                    if (code != null) {
                        courseCodes.add(code);
                    }
                }
            }
        } else if ("one_of_sequences".equals(type)) {
            List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
            if (sequences != null) {
                for (List<Map<String, Object>> sequence : sequences) {
                    for (Map<String, Object> courseReq : sequence) {
                        String code = (String) courseReq.get("code");
                        if (code != null) {
                            courseCodes.add(code);
                        }
                    }
                }
            }
        } else if ("electives".equals(type)) {
            // For electives, extract courses from sequences and courses lists
            // Sequences
            List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
            if (sequences != null) {
                for (List<Map<String, Object>> sequence : sequences) {
                    for (Map<String, Object> courseReq : sequence) {
                        String code = (String) courseReq.get("code");
                        if (code != null) {
                            courseCodes.add(code);
                        }
                    }
                }
            }
            
            // Courses (including patterns like "AMS 3xx+")
            List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
            if (courses != null) {
                for (Map<String, Object> courseReq : courses) {
                    String code = (String) courseReq.get("code");
                    if (code != null) {
                        courseCodes.add(code);
                    }
                }
            }
            
            // Additional courses (patterns)
            List<Map<String, Object>> additionalCourses = (List<Map<String, Object>>) group.get("additional_courses");
            if (additionalCourses != null) {
                for (Map<String, Object> courseReq : additionalCourses) {
                    String code = (String) courseReq.get("code");
                    if (code != null) {
                        courseCodes.add(code);
                    }
                }
            }
        }
    }
    
    /**
     * Extract elective courses from planned courses that match the elective group criteria.
     */
    @SuppressWarnings("unchecked")
    private void extractElectiveCoursesFromGroup(Map<String, Object> group, List<UserCoursePlan> plannedCourses, Set<String> neededCourses) {
        if (plannedCourses == null || plannedCourses.isEmpty()) {
            return;
        }
        
        // Get excluded courses
        List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
        
        // Check sequences
        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
        if (sequences != null) {
            for (List<Map<String, Object>> sequence : sequences) {
                for (Map<String, Object> courseReq : sequence) {
                    String code = (String) courseReq.get("code");
                    if (code != null) {
                        String[] parts = parseCourseCode(code);
                        String dept = parts[0];
                        String courseNumStr = parts[1];
                        
                        // Check if any planned course matches
                        for (UserCoursePlan plan : plannedCourses) {
                            if (plan != null && plan.getDepartment() != null && plan.getDepartment().equalsIgnoreCase(dept) &&
                                plan.getCourseNum() != null && plan.getCourseNum().toString().equals(courseNumStr)) {
                                String courseCode = plan.getDepartment() + " " + plan.getCourseNum();
                                if (excludedCourses == null || !isExcluded(courseCode, excludedCourses)) {
                                    neededCourses.add(courseCode);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Check courses (including patterns like "AMS 3xx+")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses != null) {
            for (Map<String, Object> courseReq : courses) {
                String code = (String) courseReq.get("code");
                if (code == null) continue;
                
                // Check for pattern like "AMS 3xx+" or "CSE 3xx"
                if (code.contains("xx+") || code.contains("xx")) {
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    int minLevel = 300; // Default for 3xx+
                    
                    if (code.contains("2xx+")) {
                        minLevel = 200;
                    } else if (code.contains("4xx+")) {
                        minLevel = 400;
                    } else if (code.contains("3xx+") || code.contains("3xx")) {
                        minLevel = 300;
                    }
                    
                    // Check if any planned course matches the pattern
                    for (UserCoursePlan plan : plannedCourses) {
                        if (plan != null && plan.getDepartment() != null && plan.getDepartment().equalsIgnoreCase(dept) &&
                            plan.getCourseNum() != null && plan.getCourseNum() >= minLevel) {
                            String courseCode = plan.getDepartment() + " " + plan.getCourseNum();
                            if (excludedCourses == null || !isExcluded(courseCode, excludedCourses)) {
                                neededCourses.add(courseCode);
                            }
                        }
                    }
                } else {
                    // Regular course code
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    String courseNumStr = parts[1];
                    
                    // Check if any planned course matches
                    for (UserCoursePlan plan : plannedCourses) {
                        if (plan != null && plan.getDepartment() != null && plan.getDepartment().equalsIgnoreCase(dept) &&
                            plan.getCourseNum() != null && plan.getCourseNum().toString().equals(courseNumStr)) {
                            String courseCode = plan.getDepartment() + " " + plan.getCourseNum();
                            if (excludedCourses == null || !isExcluded(courseCode, excludedCourses)) {
                                neededCourses.add(courseCode);
                            }
                        }
                    }
                }
            }
        }
        
        // Check additional_courses (patterns)
        List<Map<String, Object>> additionalCourses = (List<Map<String, Object>>) group.get("additional_courses");
        if (additionalCourses != null) {
            for (Map<String, Object> courseReq : additionalCourses) {
                String code = (String) courseReq.get("code");
                if (code == null) continue;
                
                // Check for pattern
                if (code.contains("xx+") || code.contains("xx")) {
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    int minLevel = 300; // Default for 3xx+
                    
                    if (code.contains("2xx+")) {
                        minLevel = 200;
                    } else if (code.contains("4xx+")) {
                        minLevel = 400;
                    } else if (code.contains("3xx+") || code.contains("3xx")) {
                        minLevel = 300;
                    }
                    
                    // Check if any planned course matches the pattern
                    for (UserCoursePlan plan : plannedCourses) {
                        if (plan != null && plan.getDepartment() != null && plan.getDepartment().equalsIgnoreCase(dept) &&
                            plan.getCourseNum() != null && plan.getCourseNum() >= minLevel) {
                            String courseCode = plan.getDepartment() + " " + plan.getCourseNum();
                            if (excludedCourses == null || !isExcluded(courseCode, excludedCourses)) {
                                neededCourses.add(courseCode);
                            }
                        }
                    }
                } else {
                    // Regular course code
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    String courseNumStr = parts[1];
                    
                    // Check if any planned course matches
                    for (UserCoursePlan plan : plannedCourses) {
                        if (plan != null && plan.getDepartment() != null && plan.getDepartment().equalsIgnoreCase(dept) &&
                            plan.getCourseNum() != null && plan.getCourseNum().toString().equals(courseNumStr)) {
                            String courseCode = plan.getDepartment() + " " + plan.getCourseNum();
                            if (excludedCourses == null || !isExcluded(courseCode, excludedCourses)) {
                                neededCourses.add(courseCode);
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Extract course codes from old format requirements.
     */
    @SuppressWarnings("unchecked")
    private void extractCourseCodesFromOldFormat(Map<String, Object> requirements, Set<String> courseCodes) {
        // Check core_courses
        if (requirements.containsKey("core_courses")) {
            List<String> coreCourses = (List<String>) requirements.get("core_courses");
            if (coreCourses != null) {
                courseCodes.addAll(coreCourses);
            }
        }
        
        // Check math_requirements
        if (requirements.containsKey("math_requirements")) {
            List<String> mathCourses = (List<String>) requirements.get("math_requirements");
            if (mathCourses != null) {
                courseCodes.addAll(mathCourses);
            }
        }
        
        // Check science_requirements
        if (requirements.containsKey("science_requirements")) {
            List<String> scienceCourses = (List<String>) requirements.get("science_requirements");
            if (scienceCourses != null) {
                courseCodes.addAll(scienceCourses);
            }
        }
    }
    
    /**
     * Check a single requirement group and return its status.
     * Assumes planned courses will be completed with adequate grades.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> checkGroupRequirementForStatus(Map<String, Object> group,
                                                                List<UserClasses> completedCourses,
                                                                List<UserClasses> allUserClasses,
                                                                List<UserCoursePlan> plannedCourses,
                                                                Set<String> allCourses,
                                                                List<ClassRoster> enrolledRosters,
                                                                Set<String> usedClassIds) {
        Map<String, Object> groupStatus = new HashMap<>();
        String groupId = (String) group.get("id");
        String type = (String) group.get("type");
        
        groupStatus.put("groupId", groupId);
        groupStatus.put("groupType", type);
        
        Set<String> satisfiedCourses = new HashSet<>();
        Set<String> pendingCourses = new HashSet<>();
        Set<String> missingCourses = new HashSet<>();
        
        try {
            switch (type) {
                case "all_of":
                    checkAllOfGroupForStatus(group, completedCourses, allUserClasses, plannedCourses, allCourses, 
                                            satisfiedCourses, pendingCourses, missingCourses, enrolledRosters, usedClassIds);
                    break;
                case "one_of":
                    checkOneOfGroupForStatus(group, completedCourses, allUserClasses, plannedCourses, allCourses,
                                           satisfiedCourses, pendingCourses, missingCourses, enrolledRosters, usedClassIds);
                    break;
                case "one_of_sequences":
                    checkOneOfSequencesGroupForStatus(group, completedCourses, allUserClasses, plannedCourses, allCourses,
                                                     satisfiedCourses, pendingCourses, missingCourses, enrolledRosters, usedClassIds);
                    break;
                case "two_of":
                    checkTwoOfGroupForStatus(group, completedCourses, allUserClasses, plannedCourses, allCourses,
                                            satisfiedCourses, pendingCourses, missingCourses, enrolledRosters, usedClassIds);
                    break;
                case "electives":
                    checkElectivesGroupForStatus(group, completedCourses, allUserClasses, plannedCourses, allCourses,
                                                satisfiedCourses, pendingCourses, missingCourses, enrolledRosters, usedClassIds);
                    break;
                default:
                    groupStatus.put("status", "UNKNOWN_TYPE");
                    return groupStatus;
            }
            
            // Determine group status based on new format:
            // 1. "Completed" - all requirements met
            // 2. "still remaining X course" - some courses missing, no in-progress
            // 3. "in progress" - in-progress courses, no missing
            // 4. "in progress, still remaining X course" - in-progress + missing courses
            String status;
            
            // For electives, must have satisfiedCourses and meet credits_required
            if ("electives".equals(type)) {
                Integer creditsRequired = (Integer) group.get("credits_required");
                if (creditsRequired != null && creditsRequired > 0) {
                    // Calculate total credits from satisfied courses
                    int totalCredits = 0;
                    for (String code : satisfiedCourses) {
                        // Try to get credits from completed courses
                        String[] parts = parseCourseCode(code);
                        String dept = parts[0];
                        String courseNumStr = parts[1];
                        for (UserClasses uc : completedCourses) {
                            if (uc.getDepartment().equals(dept) && 
                                uc.getCourseNum().toString().equals(courseNumStr) &&
                                uc.getCredits() != null) {
                                totalCredits += uc.getCredits();
                                break;
                            }
                        }
                    }
                    // Add credits from pending courses (in-progress courses with null grade and enrolled courses)
                    for (String code : pendingCourses) {
                        String[] parts = parseCourseCode(code);
                        String dept = parts[0];
                        String courseNumStr = parts[1];
                        boolean found = false;
                        // Check in-progress courses (completedCourses with null grade)
                        for (UserClasses uc : completedCourses) {
                            if (uc.getDepartment().equals(dept) && 
                                uc.getCourseNum().toString().equals(courseNumStr) &&
                                uc.getCredits() != null && uc.getGrade() == null) {
                                totalCredits += uc.getCredits();
                                found = true;
                                break;
                            }
                        }
                        // If not found in completedCourses, check enrolled courses
                        if (!found) {
                            for (ClassRoster roster : enrolledRosters) {
                                ClassEntity classEntity = roster.getClassEntity();
                                if (classEntity != null &&
                                    classEntity.getDepartment() != null && classEntity.getDepartment().equalsIgnoreCase(dept) &&
                                    classEntity.getCourseNum() != null && classEntity.getCourseNum().toString().equals(courseNumStr)) {
                                    if (classEntity.getMinCredits() != null) {
                                        totalCredits += classEntity.getMinCredits();
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Calculate how many credits are still needed
                    int remainingCredits = Math.max(0, creditsRequired - totalCredits);
                    
                    // Estimate course count (assuming average 3 credits per course)
                    // Round up to get minimum number of courses needed
                    int estimatedCourses = (int) Math.ceil(remainingCredits / 3.0);
                    
                    if (remainingCredits == 0 && !satisfiedCourses.isEmpty()) {
                        status = "Completed";
            } else if (!pendingCourses.isEmpty()) {
                        if (remainingCredits > 0) {
                            status = "in progress, still remaining " + estimatedCourses + " course" + 
                                    (estimatedCourses > 1 ? "s" : "");
            } else {
                            status = "in progress";
                        }
                    } else {
                        status = "still remaining " + estimatedCourses + " course" + 
                                (estimatedCourses > 1 ? "s" : "");
                    }
                } else {
                    // No credits required or invalid - check if any courses satisfied
                    if (missingCourses.isEmpty() && pendingCourses.isEmpty() && !satisfiedCourses.isEmpty()) {
                        status = "Completed";
                    } else if (!pendingCourses.isEmpty()) {
                        if (!missingCourses.isEmpty()) {
                            status = "in progress, still remaining " + missingCourses.size() + " course" + 
                                    (missingCourses.size() > 1 ? "s" : "");
                        } else {
                            status = "in progress";
                        }
                    } else {
                        status = "still remaining " + missingCourses.size() + " course" + 
                                (missingCourses.size() > 1 ? "s" : "");
                    }
                }
            } else {
                // For non-electives groups (all_of, one_of, one_of_sequences, two_of)
                // Calculate how many courses are still needed
                int remainingCount = missingCourses.size();
                boolean hasInProgress = !pendingCourses.isEmpty();
                
                if (remainingCount == 0 && !hasInProgress && !satisfiedCourses.isEmpty()) {
                    // All requirements met, no in-progress courses
                    status = "Completed";
                } else if (hasInProgress) {
                    // Has in-progress courses
                    if (remainingCount > 0) {
                        status = "in progress, still remaining " + remainingCount + " course" + 
                                (remainingCount > 1 ? "s" : "");
                    } else {
                        status = "in progress";
                    }
                } else {
                    // No in-progress, but still missing courses
                    status = "still remaining " + remainingCount + " course" + 
                            (remainingCount > 1 ? "s" : "");
                }
            }
            
            groupStatus.put("status", status);
            groupStatus.put("satisfiedCourses", new ArrayList<>(satisfiedCourses));
            if (!pendingCourses.isEmpty()) {
                groupStatus.put("pendingCourses", new ArrayList<>(pendingCourses));
            }
            if (!missingCourses.isEmpty()) {
                groupStatus.put("missingCourses", new ArrayList<>(missingCourses));
            }
            
        } catch (Exception e) {
            groupStatus.put("status", "ERROR");
            groupStatus.put("error", e.getMessage());
        }
        
        return groupStatus;
    }
    
    /**
     * Check all_of group for status calculation.
     */
    @SuppressWarnings("unchecked")
    private void checkAllOfGroupForStatus(Map<String, Object> group,
                                          List<UserClasses> completedCourses,
                                          List<UserClasses> allUserClasses,
                                          List<UserCoursePlan> plannedCourses,
                                          Set<String> allCourses,
                                          Set<String> satisfiedCourses,
                                          Set<String> pendingCourses,
                                          Set<String> missingCourses,
                                          List<ClassRoster> enrolledRosters,
                                          Set<String> usedClassIds) {
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return;
        }
        
        for (Map<String, Object> courseReq : courses) {
            String code = (String) courseReq.get("code");
            if (code == null) continue;
            
            String minGrade = (String) courseReq.get("min_grade");
            if (minGrade == null || minGrade.trim().isEmpty()) {
                minGrade = "C";
            }
            
            String[] parts = parseCourseCode(code);
            String dept = parts[0];
            String courseNumStr = parts[1];
            
            // Check if completed with sufficient grade
            boolean completed = hasCompletedCourseWithMinGrade(completedCourses, dept, courseNumStr, minGrade);
            // Check if course exists in completedCourses but with low grade (unsatisfied)
            boolean hasLowGrade = hasCompletedCourseWithLowGrade(completedCourses, dept, courseNumStr, minGrade);
            // Check if in progress (in allUserClasses but grade is null)
            boolean inProgress = isCourseInProgress(allUserClasses, dept, courseNumStr);
            // Check if enrolled (from ClassRoster)
            boolean enrolled = isCourseEnrolled(enrolledRosters, dept, courseNumStr);
            // Check if planned (from UserCoursePlan)
            boolean planned = isCoursePlanned(plannedCourses, dept, courseNumStr);
            
            if (completed) {
                satisfiedCourses.add(code);
                // Add to usedClassIds to prevent reuse in electives
                for (UserClasses uc : completedCourses) {
                    if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) && 
                        uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr) &&
                        uc.getClassId() != null) {
                        usedClassIds.add(uc.getClassId().toString());
                        break;
                    }
                }
            } else if (hasLowGrade) {
                // Course is completed but grade is too low - this is unsatisfied, add to missing
                missingCourses.add(code);
            } else if (inProgress || enrolled || planned) {
                // Add to pending if grade is null (in progress), enrolled, or planned
                pendingCourses.add(code);
            } else {
                missingCourses.add(code);
            }
        }
    }
    
    /**
     * Check one_of group for status calculation.
     */
    @SuppressWarnings("unchecked")
    private void checkOneOfGroupForStatus(Map<String, Object> group,
                                          List<UserClasses> completedCourses,
                                          List<UserClasses> allUserClasses,
                                          List<UserCoursePlan> plannedCourses,
                                          Set<String> allCourses,
                                          Set<String> satisfiedCourses,
                                          Set<String> pendingCourses,
                                          Set<String> missingCourses,
                                          List<ClassRoster> enrolledRosters,
                                          Set<String> usedClassIds) {
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return;
        }
        
        boolean foundSatisfied = false;
        
        for (Map<String, Object> courseReq : courses) {
            String code = (String) courseReq.get("code");
            if (code == null) continue;
            
            String minGrade = (String) courseReq.get("min_grade");
            if (minGrade == null || minGrade.trim().isEmpty()) {
                minGrade = "C";
            }
            
            String[] parts = parseCourseCode(code);
            String dept = parts[0];
            String courseNumStr = parts[1];
            
            boolean completed = hasCompletedCourseWithMinGrade(completedCourses, dept, courseNumStr, minGrade);
            // Check if course exists in completedCourses but with low grade (unsatisfied)
            boolean hasLowGrade = hasCompletedCourseWithLowGrade(completedCourses, dept, courseNumStr, minGrade);
            // Check if in progress (in completedCourses but grade is null)
            boolean inProgress = isCourseInProgress(allUserClasses, dept, courseNumStr);
            // Check if enrolled (from ClassRoster)
            boolean enrolled = isCourseEnrolled(enrolledRosters, dept, courseNumStr);
            // Check if planned (from UserCoursePlan)
            boolean planned = isCoursePlanned(plannedCourses, dept, courseNumStr);
            
            if (completed) {
                satisfiedCourses.add(code);
                foundSatisfied = true;
                // Add to usedClassIds to prevent reuse in electives
                for (UserClasses uc : completedCourses) {
                    if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) && 
                        uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr) &&
                        uc.getClassId() != null) {
                        usedClassIds.add(uc.getClassId().toString());
                        break;
                    }
                }
            } else if (hasLowGrade) {
                // Course is completed but grade is too low - this is unsatisfied, add to missing
                missingCourses.add(code);
            } else if (inProgress || enrolled || planned) {
                // Add to pending if grade is null (in progress), enrolled, or planned
                pendingCourses.add(code);
            } else {
                missingCourses.add(code);
            }
        }
        
        // If satisfied, clear pending and missing
        if (foundSatisfied) {
            pendingCourses.clear();
            missingCourses.clear();
        }
    }
    
    /**
     * Check one_of_sequences group for status calculation.
     */
    @SuppressWarnings("unchecked")
    private void checkOneOfSequencesGroupForStatus(Map<String, Object> group,
                                                    List<UserClasses> completedCourses,
                                                    List<UserClasses> allUserClasses,
                                                    List<UserCoursePlan> plannedCourses,
                                                    Set<String> allCourses,
                                                    Set<String> satisfiedCourses,
                                                    Set<String> pendingCourses,
                                                    Set<String> missingCourses,
                                                    List<ClassRoster> enrolledRosters,
                                                    Set<String> usedClassIds) {
        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
        if (sequences == null || sequences.isEmpty()) {
            return;
        }
        
        boolean foundCompleteSequence = false;
        
        for (List<Map<String, Object>> sequence : sequences) {
            boolean sequenceComplete = true;
            List<String> sequenceCourses = new ArrayList<>();
            
            for (Map<String, Object> courseReq : sequence) {
                String code = (String) courseReq.get("code");
                if (code == null) continue;
                
                String minGrade = (String) courseReq.get("min_grade");
                if (minGrade == null || minGrade.trim().isEmpty()) {
                    minGrade = "C";
                }
                
                String[] parts = parseCourseCode(code);
                String dept = parts[0];
                String courseNumStr = parts[1];
                
                boolean completed = hasCompletedCourseWithMinGrade(completedCourses, dept, courseNumStr, minGrade);
                // Check if course exists in completedCourses but with low grade (unsatisfied)
                boolean hasLowGrade = hasCompletedCourseWithLowGrade(completedCourses, dept, courseNumStr, minGrade);
                // Check if in progress (in completedCourses but grade is null)
                boolean inProgress = isCourseInProgress(allUserClasses, dept, courseNumStr);
                // Check if enrolled (from ClassRoster)
                boolean enrolled = isCourseEnrolled(enrolledRosters, dept, courseNumStr);
                // Check if planned (from UserCoursePlan)
                boolean planned = isCoursePlanned(plannedCourses, dept, courseNumStr);
                
                sequenceCourses.add(code);
                
                if (completed) {
                    satisfiedCourses.add(code);
                    // Add to usedClassIds to prevent reuse in electives
                    for (UserClasses uc : completedCourses) {
                        if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) && 
                            uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr) &&
                            uc.getClassId() != null) {
                            usedClassIds.add(uc.getClassId().toString());
                            break;
                        }
                    }
                } else if (hasLowGrade) {
                    // Course is completed but grade is too low - this is unsatisfied
                    missingCourses.add(code);
                    sequenceComplete = false;
                } else if (inProgress || enrolled || planned) {
                    // Add to pending if grade is null (in progress), enrolled, or planned
                    pendingCourses.add(code);
                    sequenceComplete = false;
                } else {
                    missingCourses.add(code);
                    sequenceComplete = false;
                }
            }
            
            if (sequenceComplete) {
                foundCompleteSequence = true;
                break;
            }
        }
        
        // If found complete sequence, clear pending and missing for this group
        if (foundCompleteSequence) {
            pendingCourses.clear();
            missingCourses.clear();
        }
    }
    
    /**
     * Check two_of group for status calculation.
     */
    @SuppressWarnings("unchecked")
    private void checkTwoOfGroupForStatus(Map<String, Object> group,
                                          List<UserClasses> completedCourses,
                                          List<UserClasses> allUserClasses,
                                          List<UserCoursePlan> plannedCourses,
                                          Set<String> allCourses,
                                          Set<String> satisfiedCourses,
                                          Set<String> pendingCourses,
                                          Set<String> missingCourses,
                                          List<ClassRoster> enrolledRosters,
                                          Set<String> usedClassIds) {
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return;
        }
        
        int satisfiedCount = 0;
        
        for (Map<String, Object> courseReq : courses) {
            String code = (String) courseReq.get("code");
            if (code == null) continue;
            
            String minGrade = (String) courseReq.get("min_grade");
            if (minGrade == null || minGrade.trim().isEmpty()) {
                minGrade = "C";
            }
            
            String[] parts = parseCourseCode(code);
            String dept = parts[0];
            String courseNumStr = parts[1];
            
            boolean completed = hasCompletedCourseWithMinGrade(completedCourses, dept, courseNumStr, minGrade);
            // Check if course exists in completedCourses but with low grade (unsatisfied)
            boolean hasLowGrade = hasCompletedCourseWithLowGrade(completedCourses, dept, courseNumStr, minGrade);
            // Check if in progress (in completedCourses but grade is null)
            boolean inProgress = isCourseInProgress(allUserClasses, dept, courseNumStr);
            // Check if enrolled (from ClassRoster)
            boolean enrolled = isCourseEnrolled(enrolledRosters, dept, courseNumStr);
            // Check if planned (from UserCoursePlan)
            boolean planned = isCoursePlanned(plannedCourses, dept, courseNumStr);
            
            if (completed) {
                satisfiedCourses.add(code);
                satisfiedCount++;
                // Add to usedClassIds to prevent reuse in electives
                for (UserClasses uc : completedCourses) {
                    if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) && 
                        uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr) &&
                        uc.getClassId() != null) {
                        usedClassIds.add(uc.getClassId().toString());
                        break;
                    }
                }
            } else if (hasLowGrade) {
                // Course is completed but grade is too low - this is unsatisfied, add to missing
                missingCourses.add(code);
            } else if (inProgress || enrolled || planned) {
                // Add to pending if grade is null (in progress), enrolled, or planned
                pendingCourses.add(code);
            } else {
                missingCourses.add(code);
            }
        }
        
        // If we have 2+ satisfied, clear pending and missing
        if (satisfiedCount >= 2) {
            pendingCourses.clear();
            missingCourses.clear();
        }
    }
    
    /**
     * Check electives group for status calculation.
     * Must satisfy credits_required from courses/sequences/additional_courses.
     * If no group-level min_grade is specified, all courses must be C or above.
     */
    @SuppressWarnings("unchecked")
    private void checkElectivesGroupForStatus(Map<String, Object> group,
                                               List<UserClasses> completedCourses,
                                               List<UserClasses> allUserClasses,
                                               List<UserCoursePlan> plannedCourses,
                                               Set<String> allCourses,
                                               Set<String> satisfiedCourses,
                                               Set<String> pendingCourses,
                                               Set<String> missingCourses,
                                               List<ClassRoster> enrolledRosters,
                                               Set<String> usedClassIds) {
        Integer creditsRequired = (Integer) group.get("credits_required");
        if (creditsRequired == null || creditsRequired <= 0) {
            // If no credits required, mark as missing to indicate requirement exists but not satisfied
            return;
        }
        
        // Get group-level min_grade if specified
        String groupMinGrade = (String) group.get("min_grade");
        // If no group-level min_grade, default to "C" for all courses
        if (groupMinGrade == null || groupMinGrade.trim().isEmpty()) {
            groupMinGrade = "C";
        }
        
        int totalCredits = 0;
        Set<String> usedCourseCodes = new HashSet<>();
        
        // Check sequences first
        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
        if (sequences != null && !sequences.isEmpty()) {
            for (List<Map<String, Object>> sequence : sequences) {
                boolean sequenceComplete = true;
                List<String> sequenceCourseCodes = new ArrayList<>();
                
                for (Map<String, Object> courseReq : sequence) {
                    String code = (String) courseReq.get("code");
                    if (code == null) continue;
                    
                    // Get min_grade from course requirement, use group-level min_grade if not specified
                    String minGrade = (String) courseReq.get("min_grade");
                    if (minGrade == null || minGrade.trim().isEmpty()) {
                        minGrade = groupMinGrade;
                    }
                    
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    String courseNumStr = parts[1];
                    
                    // Check if completed with sufficient grade
                    boolean completed = hasCompletedCourseWithMinGrade(completedCourses, dept, courseNumStr, minGrade);
                    // Check if course exists in completedCourses but with low grade (unsatisfied)
                    boolean hasLowGrade = hasCompletedCourseWithLowGrade(completedCourses, dept, courseNumStr, minGrade);
                    // Check if in progress (in completedCourses but grade is null)
                    boolean inProgress = isCourseInProgress(allUserClasses, dept, courseNumStr);
                    // Check if enrolled (from ClassRoster)
                    boolean enrolled = isCourseEnrolled(enrolledRosters, dept, courseNumStr);
                    // Check if planned (from UserCoursePlan)
                    boolean planned = isCoursePlanned(plannedCourses, dept, courseNumStr);
                    
                    // Find the UserClasses object to check if it's already used in another group
                    UserClasses matchingClass = null;
                    for (UserClasses uc : completedCourses) {
                        if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) && 
                            uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr)) {
                            matchingClass = uc;
                            break;
                        }
                    }
                    
                    // Check if this course is already used in another group
                    boolean isUsed = false;
                    if (matchingClass != null && matchingClass.getClassId() != null) {
                        isUsed = usedClassIds.contains(matchingClass.getClassId().toString());
                    }
                    
                    if (completed && !usedCourseCodes.contains(code) && !isUsed) {
                        // Get credits from completed course
                        if (matchingClass != null && matchingClass.getCredits() != null) {
                            totalCredits += matchingClass.getCredits();
                            satisfiedCourses.add(code);
                            usedCourseCodes.add(code);
                            sequenceCourseCodes.add(code);
                            // Add to usedClassIds to prevent reuse
                            if (matchingClass.getClassId() != null) {
                                usedClassIds.add(matchingClass.getClassId().toString());
                            }
                        }
                    } else if (hasLowGrade) {
                        // Course is completed but grade is too low - this is unsatisfied, not pending
                        sequenceComplete = false;
                        break;
                    } else if (inProgress || enrolled || planned) {
                        // Add to pending if grade is null (in progress) or if enrolled
                        if (inProgress) {
                            for (UserClasses uc : completedCourses) {
                                if (uc.getDepartment().equals(dept) && 
                                    uc.getCourseNum().toString().equals(courseNumStr) &&
                                    uc.getCredits() != null && uc.getGrade() == null) {
                                    totalCredits += uc.getCredits();
                                    pendingCourses.add(code);
                                    sequenceCourseCodes.add(code);
                                    break;
                                }
                            }
                        } else if (enrolled) {
                            // Get credits from enrolled course
                            for (ClassRoster roster : enrolledRosters) {
                                ClassEntity classEntity = roster.getClassEntity();
                                if (classEntity != null &&
                                    classEntity.getDepartment() != null && classEntity.getDepartment().equalsIgnoreCase(dept) &&
                                    classEntity.getCourseNum() != null && classEntity.getCourseNum().toString().equals(courseNumStr)) {
                                    if (classEntity.getMinCredits() != null) {
                                        totalCredits += classEntity.getMinCredits();
                                    }
                                    pendingCourses.add(code);
                                    sequenceCourseCodes.add(code);
                                    break;
                                }
                            }
                        } else if (planned) {
                            // Get credits from planned course
                            for (UserCoursePlan plan : plannedCourses) {
                                if (plan != null && plan.getDepartment() != null && plan.getDepartment().equalsIgnoreCase(dept) &&
                                    plan.getCourseNum() != null && plan.getCourseNum().toString().equals(courseNumStr)) {
                                    if (plan.getCredits() != null) {
                                        totalCredits += plan.getCredits();
                                    }
                                    pendingCourses.add(code);
                                    sequenceCourseCodes.add(code);
                                    break;
                                }
                            }
                        }
                        sequenceComplete = false;
                    } else {
                        sequenceComplete = false;
                        break;
                    }
                }
                
                if (sequenceComplete && !sequenceCourseCodes.isEmpty()) {
                    // Check excluded courses
                    List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                    if (excludedCourses != null) {
                        for (String code : sequenceCourseCodes) {
                            if (isExcluded(code, excludedCourses)) {
                                // Remove from satisfied/pending and subtract credits
                                satisfiedCourses.remove(code);
                                pendingCourses.remove(code);
                                usedCourseCodes.remove(code);
                                // Note: We don't track credits per course, so this is approximate
                            }
                        }
                    }
                }
            }
        }
        
        // Check additional_courses
        List<Map<String, Object>> additionalCourses = (List<Map<String, Object>>) group.get("additional_courses");
        if (additionalCourses != null && !additionalCourses.isEmpty()) {
            for (Map<String, Object> courseReq : additionalCourses) {
                if (totalCredits >= creditsRequired) break;
                
                String code = (String) courseReq.get("code");
                if (code == null) continue;
                
                // Get min_grade from course requirement, use group-level min_grade if not specified
                String minGrade = (String) courseReq.get("min_grade");
                if (minGrade == null || minGrade.trim().isEmpty()) {
                    minGrade = groupMinGrade;
                }
                
                String[] parts = parseCourseCode(code);
                String dept = parts[0];
                String courseNumStr = parts[1];
                
                // Check excluded courses
                List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                if (excludedCourses != null && isExcluded(code, excludedCourses)) {
                    continue;
                }
                
                // Find the UserClasses object to check if it's already used in another group
                UserClasses matchingClass = null;
                for (UserClasses uc : completedCourses) {
                    if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) && 
                        uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr)) {
                        matchingClass = uc;
                        break;
                    }
                }
                
                // Check if this course is already used in another group
                boolean isUsed = false;
                if (matchingClass != null && matchingClass.getClassId() != null) {
                    isUsed = usedClassIds.contains(matchingClass.getClassId().toString());
                }
                
                // Check if course is completed with sufficient grade
                boolean completed = hasCompletedCourseWithMinGrade(completedCourses, dept, courseNumStr, minGrade);
                // Check if course exists in completedCourses but with low grade (unsatisfied)
                boolean hasLowGrade = hasCompletedCourseWithLowGrade(completedCourses, dept, courseNumStr, minGrade);
                // Check if in progress (in completedCourses but grade is null)
                boolean inProgress = isCourseInProgress(allUserClasses, dept, courseNumStr);
                // Check if enrolled (from ClassRoster)
                boolean enrolled = isCourseEnrolled(enrolledRosters, dept, courseNumStr);
                // Check if planned (from UserCoursePlan)
                boolean planned = isCoursePlanned(plannedCourses, dept, courseNumStr);
                
                if (completed && !usedCourseCodes.contains(code) && !isUsed) {
                    if (matchingClass != null && matchingClass.getCredits() != null) {
                        totalCredits += matchingClass.getCredits();
                        satisfiedCourses.add(code);
                        usedCourseCodes.add(code);
                        // Add to usedClassIds to prevent reuse
                        if (matchingClass.getClassId() != null) {
                            usedClassIds.add(matchingClass.getClassId().toString());
                        }
                    }
                } else if (hasLowGrade) {
                    // Course is completed but grade is too low - this is unsatisfied, not pending
                    // Don't add to any list, just skip (it's already unsatisfied)
                } else if ((inProgress || enrolled || planned) && !usedCourseCodes.contains(code)) {
                    // Add to pending if grade is null (in progress), enrolled, or planned
                    if (inProgress) {
                        for (UserClasses uc : completedCourses) {
                            if (uc.getDepartment().equals(dept) && 
                                uc.getCourseNum().toString().equals(courseNumStr) &&
                                uc.getCredits() != null && uc.getGrade() == null) {
                                totalCredits += uc.getCredits();
                                pendingCourses.add(code);
                                usedCourseCodes.add(code);
                                break;
                            }
                        }
                    } else if (enrolled) {
                        // Get credits from enrolled course
                        for (ClassRoster roster : enrolledRosters) {
                            ClassEntity classEntity = roster.getClassEntity();
                            if (classEntity != null &&
                                classEntity.getDepartment() != null && classEntity.getDepartment().equalsIgnoreCase(dept) &&
                                classEntity.getCourseNum() != null && classEntity.getCourseNum().toString().equals(courseNumStr)) {
                                if (classEntity.getMinCredits() != null) {
                                    totalCredits += classEntity.getMinCredits();
                                }
                                pendingCourses.add(code);
                                usedCourseCodes.add(code);
                                break;
                            }
                        }
                    } else if (planned) {
                        // Get credits from planned course
                        for (UserCoursePlan plan : plannedCourses) {
                            if (plan != null && plan.getDepartment() != null && plan.getDepartment().equalsIgnoreCase(dept) &&
                                plan.getCourseNum() != null && plan.getCourseNum().toString().equals(courseNumStr)) {
                                if (plan.getCredits() != null) {
                                    totalCredits += plan.getCredits();
                                }
                                pendingCourses.add(code);
                                usedCourseCodes.add(code);
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        // Check regular courses list (including patterns like "CSE 3xx+")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses != null && !courses.isEmpty()) {
            for (Map<String, Object> courseReq : courses) {
                if (totalCredits >= creditsRequired) break;
                
                String code = (String) courseReq.get("code");
                if (code == null) continue;
                
                // Get min_grade from course requirement, use group-level min_grade if not specified
                String minGrade = (String) courseReq.get("min_grade");
                if (minGrade == null || minGrade.trim().isEmpty()) {
                    minGrade = groupMinGrade;
                }
                
                // Check for pattern like "CSE 3xx+"
                if (code.contains("xx+") || code.contains("xx")) {
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    int minLevel = 300; // Default for 3xx+
                    
                    if (code.contains("2xx+")) {
                        minLevel = 200;
                    } else if (code.contains("4xx+")) {
                        minLevel = 400;
                    }
                    
                    // Check excluded courses
                    List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                    
                    // Find matching upper-division courses
                    for (UserClasses uc : completedCourses) {
                        if (totalCredits >= creditsRequired) break;
                        String courseCode = uc.getDepartment() + " " + uc.getCourseNum();
                        
                        // Check if this course is already used in another group
                        boolean isUsed = false;
                        if (uc.getClassId() != null) {
                            isUsed = usedClassIds.contains(uc.getClassId().toString());
                        }
                        
                        if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) &&
                            uc.getCourseNum() != null && uc.getCourseNum() >= minLevel &&
                            !usedCourseCodes.contains(courseCode) && !isUsed &&
                            uc.getGrade() != null && isGradeSufficientForRequirement(uc.getGrade(), minGrade)) {
                            
                            if (excludedCourses == null || !isExcluded(courseCode, excludedCourses)) {
                                if (uc.getCredits() != null) {
                                    totalCredits += uc.getCredits();
                                    satisfiedCourses.add(courseCode);
                                    usedCourseCodes.add(courseCode);
                                    // Add to usedClassIds to prevent reuse
                                    if (uc.getClassId() != null) {
                                        usedClassIds.add(uc.getClassId().toString());
                                    }
                                }
                            }
                        }
                    }
                    
                    // Check in-progress courses (allUserClasses with null grade), enrolled courses, and planned courses
                    for (UserClasses uc : allUserClasses) {
                        if (totalCredits >= creditsRequired) break;
                        String courseCode = uc.getDepartment() + " " + uc.getCourseNum();
                        
                        if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) &&
                            uc.getCourseNum() != null && uc.getCourseNum() >= minLevel &&
                            !usedCourseCodes.contains(courseCode) &&
                            (uc.getGrade() == null || uc.getGrade().trim().isEmpty())) { // Only in-progress courses
                            
                            if (excludedCourses == null || !isExcluded(courseCode, excludedCourses)) {
                                if (uc.getCredits() != null) {
                                    totalCredits += uc.getCredits();
                                    pendingCourses.add(courseCode);
                                    usedCourseCodes.add(courseCode);
                                }
                            }
                        }
                    }
                    // Check enrolled courses for pattern matching
                    for (ClassRoster roster : enrolledRosters) {
                        if (totalCredits >= creditsRequired) break;
                        ClassEntity classEntity = roster.getClassEntity();
                        if (classEntity != null) {
                            String courseCode = classEntity.getDepartment() + " " + classEntity.getCourseNum();
                            
                            if (classEntity.getDepartment() != null && classEntity.getDepartment().equalsIgnoreCase(dept) &&
                                classEntity.getCourseNum() != null && classEntity.getCourseNum() >= minLevel &&
                                !usedCourseCodes.contains(courseCode) &&
                                "ENROLLED".equals(roster.getStatus())) {
                                
                                if (excludedCourses == null || !isExcluded(courseCode, excludedCourses)) {
                                    if (classEntity.getMinCredits() != null) {
                                        totalCredits += classEntity.getMinCredits();
                                        pendingCourses.add(courseCode);
                                        usedCourseCodes.add(courseCode);
                                    }
                                }
                            }
                        }
                    }
                    // Check planned courses for pattern matching
                    for (UserCoursePlan plan : plannedCourses) {
                        if (totalCredits >= creditsRequired) break;
                        if (plan == null || plan.getDepartment() == null || plan.getCourseNum() == null) continue;
                        String courseCode = plan.getDepartment() + " " + plan.getCourseNum();
                        
                        if (plan.getDepartment().equalsIgnoreCase(dept) &&
                            plan.getCourseNum() >= minLevel &&
                            !usedCourseCodes.contains(courseCode)) {
                            
                            if (excludedCourses == null || !isExcluded(courseCode, excludedCourses)) {
                                if (plan.getCredits() != null) {
                                    totalCredits += plan.getCredits();
                                    pendingCourses.add(courseCode);
                                    usedCourseCodes.add(courseCode);
                                }
                            }
                        }
                    }
                } else {
                    System.out.println("[DEBUG checkSbcRequirements] hasAnyCourseRequirements=true branch");
                    // Regular course code
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    String courseNumStr = parts[1];
                    
                    // Check excluded courses
                    List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                    if (excludedCourses != null && isExcluded(code, excludedCourses)) {
                        continue;
                    }
                    
                    // Find the UserClasses object to check if it's already used in another group
                    UserClasses matchingClass = null;
                    for (UserClasses uc : completedCourses) {
                        if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) && 
                            uc.getCourseNum() != null && uc.getCourseNum().toString().equals(courseNumStr)) {
                            matchingClass = uc;
                            break;
                        }
                    }
                    
                    // Check if this course is already used in another group
                    boolean isUsed = false;
                    if (matchingClass != null && matchingClass.getClassId() != null) {
                        isUsed = usedClassIds.contains(matchingClass.getClassId().toString());
                    }
                    
                    // Check if course is completed with sufficient grade
                    boolean completed = hasCompletedCourseWithMinGrade(completedCourses, dept, courseNumStr, minGrade);
                    // Check if course exists in completedCourses but with low grade (unsatisfied)
                    boolean hasLowGrade = hasCompletedCourseWithLowGrade(completedCourses, dept, courseNumStr, minGrade);
                    // Check if in progress (in completedCourses but grade is null)
                    boolean inProgress = isCourseInProgress(allUserClasses, dept, courseNumStr);
                    // Check if enrolled (from ClassRoster)
                    boolean enrolled = isCourseEnrolled(enrolledRosters, dept, courseNumStr);
                    // Check if planned (from UserCoursePlan)
                    boolean planned = isCoursePlanned(plannedCourses, dept, courseNumStr);
                    
                    if (completed && !usedCourseCodes.contains(code) && !isUsed) {
                        if (matchingClass != null && matchingClass.getCredits() != null) {
                            totalCredits += matchingClass.getCredits();
                            satisfiedCourses.add(code);
                            usedCourseCodes.add(code);
                            // Add to usedClassIds to prevent reuse
                            if (matchingClass.getClassId() != null) {
                                usedClassIds.add(matchingClass.getClassId().toString());
                            }
                        }
                    } else if (hasLowGrade) {
                        // Course is completed but grade is too low - this is unsatisfied, not pending
                        // Don't add to any list, just skip (it's already unsatisfied)
                    } else if ((inProgress || enrolled || planned) && !usedCourseCodes.contains(code)) {
                        // Add to pending if grade is null (in progress), enrolled, or planned
                        if (inProgress) {
                            for (UserClasses uc : completedCourses) {
                                if (uc.getDepartment().equals(dept) && 
                                    uc.getCourseNum().toString().equals(courseNumStr) &&
                                    uc.getCredits() != null && uc.getGrade() == null) {
                                    totalCredits += uc.getCredits();
                                    pendingCourses.add(code);
                                    usedCourseCodes.add(code);
                                    break;
                                }
                            }
                        } else if (enrolled) {
                            // Get credits from enrolled course
                            for (ClassRoster roster : enrolledRosters) {
                                ClassEntity classEntity = roster.getClassEntity();
                                if (classEntity != null &&
                                    classEntity.getDepartment() != null && classEntity.getDepartment().equalsIgnoreCase(dept) &&
                                    classEntity.getCourseNum() != null && classEntity.getCourseNum().toString().equals(courseNumStr)) {
                                    if (classEntity.getMinCredits() != null) {
                                        totalCredits += classEntity.getMinCredits();
                                    }
                                    pendingCourses.add(code);
                                    usedCourseCodes.add(code);
                                    break;
                                }
                            }
                        } else if (planned) {
                            // Get credits from planned course
                            for (UserCoursePlan plan : plannedCourses) {
                                if (plan != null && plan.getDepartment() != null && plan.getDepartment().equalsIgnoreCase(dept) &&
                                    plan.getCourseNum() != null && plan.getCourseNum().toString().equals(courseNumStr)) {
                                    if (plan.getCredits() != null) {
                                        totalCredits += plan.getCredits();
                                    }
                                    pendingCourses.add(code);
                                    usedCourseCodes.add(code);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // If credits not met, add to missing (but only if no satisfied or pending courses)
        if (totalCredits < creditsRequired && satisfiedCourses.isEmpty() && pendingCourses.isEmpty()) {
            // Add a placeholder to indicate requirement exists
            missingCourses.add("Need " + creditsRequired + " credits");
        }
    }
    
    /**
     * Check if a course code is in the excluded courses list.
     */
    @SuppressWarnings("unchecked")
    private boolean isExcluded(String courseCode, List<Map<String, Object>> excludedCourses) {
        if (excludedCourses == null || courseCode == null) {
            return false;
        }
        
        for (Map<String, Object> excluded : excludedCourses) {
            String code = (String) excluded.get("code");
            if (code != null && code.equals(courseCode)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Parse course code into department and course number.
     */
    private String[] parseCourseCode(String code) {
        if (code == null) {
            return new String[]{"", ""};
        }
        code = code.trim();
        String[] parts = code.split("\\s+", 2);
        if (parts.length < 2) {
            return new String[]{parts[0], ""};
        }
        return new String[]{parts[0], parts[1]};
    }
    
    /**
     * Check if course is completed with min_grade or higher.
     */
    private boolean hasCompletedCourseWithMinGrade(List<UserClasses> completedCourses, 
                                                     String department, String courseNum, String minGrade) {
        if (department == null || courseNum == null || courseNum.isEmpty()) {
            return false;
        }
        
        Integer courseNumInt;
        try {
            courseNumInt = Integer.parseInt(courseNum);
        } catch (NumberFormatException e) {
            return false;
        }
        
        // Default to "C" if minGrade is not specified
        String requiredGrade = (minGrade == null || minGrade.trim().isEmpty()) ? "C" : minGrade.trim();
        
        for (UserClasses uc : completedCourses) {
            if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(department) && 
                uc.getCourseNum() != null && uc.getCourseNum().equals(courseNumInt)) {
                String grade = uc.getGrade();
                if (grade != null && isGradeSufficientForRequirement(grade, requiredGrade)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Find matching UserClasses with minimum grade requirement.
     * If minGrade is specified, only returns classes with grade >= minGrade.
     * If minGrade is null or empty, defaults to "C".
     */
    private UserClasses findMatchingClassWithMinGrade(List<UserClasses> classes, String department, 
                                                       String courseNumStr, String minGrade) {
        if (department == null || courseNumStr == null || courseNumStr.isEmpty()) {
            return null;
        }
        
        Integer courseNum;
        try {
            courseNum = Integer.parseInt(courseNumStr);
        } catch (NumberFormatException e) {
            return null; // Can't parse course number
        }
        
        // Default to "C" if minGrade is not specified
        String requiredGrade = (minGrade == null || minGrade.trim().isEmpty()) ? "C" : minGrade.trim();
        
        return classes.stream()
                .filter(uc -> {
                    if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(department) &&
                        uc.getCourseNum() != null && uc.getCourseNum().equals(courseNum) &&
                        uc.getGrade() != null) {
                        boolean isSufficient = isGradeSufficientForRequirement(uc.getGrade(), requiredGrade);
                        // Debug: Log grade check for CSE 114
                        if (department.equalsIgnoreCase("CSE") && courseNum == 114) {
                            System.out.println("[DEBUG findMatchingClassWithMinGrade] CSE 114: actualGrade='" + uc.getGrade() + 
                                             "', requiredGrade='" + requiredGrade + 
                                             "', isSufficient=" + isSufficient);
                        }
                        return isSufficient;
                    }
                    return false;
                })
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Check if course is in the plan.
     */
    private boolean isCoursePlanned(List<UserCoursePlan> plannedCourses, String department, String courseNum) {
        for (UserCoursePlan plan : plannedCourses) {
            if (plan.getDepartment().equals(department) && 
                plan.getCourseNum().toString().equals(courseNum)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if course is in progress (completed but grade is null).
     */
    private boolean isCourseInProgress(List<UserClasses> completedCourses, String department, String courseNum) {
        if (department == null || courseNum == null || courseNum.isEmpty()) {
            return false;
        }
        
        Integer courseNumInt;
        try {
            courseNumInt = Integer.parseInt(courseNum);
        } catch (NumberFormatException e) {
            return false;
        }
        
        for (UserClasses uc : completedCourses) {
            if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(department) && 
                uc.getCourseNum() != null && uc.getCourseNum().equals(courseNumInt)) {
                // If grade is null, it's in progress
                if (uc.getGrade() == null || uc.getGrade().trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Check if a course was taken before another course (not same semester).
     * Prerequisites must be completed BEFORE the course, not in the same semester.
     */
    private boolean isCourseBefore(UserClasses course1, UserClasses course2) {
        if (course1.getYear() == null || course2.getYear() == null) {
            return false;
        }
        
        // If course1 is in an earlier year, it's before course2
        if (course1.getYear() < course2.getYear()) {
            return true;
        }
        
        // If same year, compare semesters
        if (course1.getYear().equals(course2.getYear())) {
            if (course1.getSemester() == null || course2.getSemester() == null) {
                return false;
            }
            int order1 = getSemesterOrder(course1.getSemester());
            int order2 = getSemesterOrder(course2.getSemester());
            // Must be earlier semester (not same semester)
            return order1 < order2;
        }
        
        return false;
    }
    
    /**
     * Check if a ClassEntity is from a semester before the given semester/year.
     * Used to check if enrolled courses from earlier semesters can satisfy prerequisites.
     */
    private boolean isClassEntityBefore(ClassEntity classEntity, String semester, Integer year) {
        if (classEntity.getYear() == null || year == null) {
            return false;
        }
        
        // If classEntity is in an earlier year, it's before
        if (classEntity.getYear() < year) {
            return true;
        }
        
        // If same year, compare semesters
        if (classEntity.getYear().equals(year)) {
            if (classEntity.getSemester() == null || semester == null) {
                return false;
            }
            int order1 = getSemesterOrder(classEntity.getSemester());
            int order2 = getSemesterOrder(semester);
            // Must be earlier semester (not same semester)
            return order1 < order2;
        }
        
        return false;
    }
    
    /**
     * Check if course is enrolled (from ClassRoster with status = "ENROLLED").
     */
    private boolean isCourseEnrolled(List<ClassRoster> enrolledRosters, String department, String courseNum) {
        if (department == null || courseNum == null || courseNum.isEmpty() || enrolledRosters == null) {
            return false;
        }
        
        Integer courseNumInt;
        try {
            courseNumInt = Integer.parseInt(courseNum);
        } catch (NumberFormatException e) {
            return false;
        }
        
        for (ClassRoster roster : enrolledRosters) {
            ClassEntity classEntity = roster.getClassEntity();
            if (classEntity != null && 
                classEntity.getDepartment() != null && classEntity.getDepartment().equalsIgnoreCase(department) &&
                classEntity.getCourseNum() != null && classEntity.getCourseNum().equals(courseNumInt) &&
                "ENROLLED".equals(roster.getStatus())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if course is completed but with grade below minimum requirement.
     * 
     * Status definitions:
     * - SATISFIED: Grade >= min_grade (or C if no min_grade specified)
     * - UNSATISFIED: Grade < min_grade (e.g., C- when C is required, D, F)
     * - PENDING: Grade is null (enrolled/in-progress)
     * 
     * This method returns true if course is UNSATISFIED (grade too low).
     */
    private boolean hasCompletedCourseWithLowGrade(List<UserClasses> completedCourses, 
                                                   String department, String courseNum, String minGrade) {
        if (department == null || courseNum == null || courseNum.isEmpty()) {
            return false;
        }
        
        Integer courseNumInt;
        try {
            courseNumInt = Integer.parseInt(courseNum);
        } catch (NumberFormatException e) {
            return false;
        }
        
        // Default to "C" if minGrade is not specified
        String requiredGrade = (minGrade == null || minGrade.trim().isEmpty()) ? "C" : minGrade.trim();
        
        for (UserClasses uc : completedCourses) {
            if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(department) && 
                uc.getCourseNum() != null && uc.getCourseNum().equals(courseNumInt)) {
                String grade = uc.getGrade();
                // If grade is null or empty, it's not completed yet (should be pending, not low grade)
                if (grade == null || grade.trim().isEmpty()) {
                    return false;
                }
                // If grade exists but doesn't meet minimum, it's low grade (unsatisfied)
                if (!isGradeSufficientForRequirement(grade, requiredGrade)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Check if grade meets minimum requirement.
     */
    private boolean isGradeSufficientForRequirement(String actualGrade, String requiredGrade) {
        if (actualGrade == null || requiredGrade == null) {
            return false;
        }
        
        actualGrade = actualGrade.trim().toUpperCase();
        requiredGrade = requiredGrade.trim().toUpperCase();
        
        Map<String, Double> gradePoints = new HashMap<>();
        gradePoints.put("A", 4.0);
        gradePoints.put("A-", 3.7);
        gradePoints.put("B+", 3.3);
        gradePoints.put("B", 3.0);
        gradePoints.put("B-", 2.7);
        gradePoints.put("C+", 2.3);
        gradePoints.put("C", 2.0);
        gradePoints.put("C-", 1.7);
        gradePoints.put("D+", 1.3);
        gradePoints.put("D", 1.0);
        gradePoints.put("D-", 0.7);
        gradePoints.put("F", 0.0);
        
        Double actualPoints = gradePoints.get(actualGrade);
        Double requiredPoints = gradePoints.get(requiredGrade);
        
        if (actualPoints != null && requiredPoints != null) {
            return actualPoints >= requiredPoints;
        }
        
        try {
            double actualNumeric = Double.parseDouble(actualGrade);
            double requiredNumeric = Double.parseDouble(requiredGrade);
            return actualNumeric >= requiredNumeric;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}


package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.UserClasses;
import edu.sbu.cse416.sam_system.entity.UserTransferCourses;
import edu.sbu.cse416.sam_system.repository.UserClassesRepository;
import edu.sbu.cse416.sam_system.repository.UserTransferCoursesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TP: Used cursor to implement service for prerequisites validation.
 */
@Service
public class PrerequisitesValidationService {

    // Common pattern for matching course codes (e.g., "CSE 216", "BIO 351")
    private static final Pattern COURSE_PATTERN = Pattern.compile("\\b([A-Z]{2,4})\\s+(\\d{3,4})\\b");

    @Autowired
    private UserClassesRepository userClassesRepository;
    
    @Autowired
    private UserTransferCoursesRepository userTransferCoursesRepository;

    /**
     * Validate if user meets prerequisites for a course
     * @param userId User ID
     * @param prerequisites Prerequisites string (e.g., "BIO 351; C or higher in BIO 205 or BIO 207")
     * @return true if prerequisites are met, false otherwise
     */
    public boolean validatePrerequisites(Long userId, String prerequisites) {
        if (prerequisites == null || prerequisites.trim().isEmpty()) {
            return true; // No prerequisites
        }

        System.out.println("    [PREREQ VALIDATION] User ID: " + userId);
        System.out.println("    [PREREQ VALIDATION] Prerequisites: " + prerequisites);

        // Get user's completed classes
        List<UserClasses> userClasses = userClassesRepository.findCompletedClassesByUserId(userId);
        
        // Get user's current semester classes (for counting in-progress credits)
        List<UserClasses> allUserClasses = userClassesRepository.findByUserId(userId);
        
        // Get user's transfer courses with SBU equivalents (for prerequisite checking)
        List<UserTransferCourses> transferCourses = userTransferCoursesRepository.findWithSbuequivalentsByUserId(userId);
        
        System.out.println("    [PREREQ VALIDATION] User has " + userClasses.size() + " completed classes");
        System.out.println("    [PREREQ VALIDATION] User has " + allUserClasses.size() + " total classes (including in-progress)");
        System.out.println("    [PREREQ VALIDATION] User has " + transferCourses.size() + " transfer courses with SBU equivalents");

        // Parse prerequisites string
        // Format: "BIO 351; C or higher in BIO 205 or BIO 207"
        // Each semicolon-separated part must be satisfied
        String[] prerequisiteGroups = prerequisites.split(";");
        
        for (String group : prerequisiteGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;
            
            System.out.println("    [PREREQ VALIDATION] Checking group: " + group);
            
            if (!checkPrerequisiteGroup(userClasses, transferCourses, group, allUserClasses, userId)) {
                System.out.println("    [PREREQ VALIDATION] FAILED - Group not satisfied: " + group);
                return false;
            }
        }

        System.out.println("    [PREREQ VALIDATION] SUCCESS - All prerequisites met");
        return true;
    }

    /**
     * Validate if user can take a course (check antirequisites)
     * @param userId User ID
     * @param antirequisites Antirequisites string (e.g., "CSE 114")
     * @return true if user can take the course, false if antirequisites conflict
     */
    public boolean validateAntirequisites(Long userId, String antirequisites) {
        if (antirequisites == null || antirequisites.trim().isEmpty()) {
            return true; // No antirequisites
        }

        System.out.println("    [ANTIREQ VALIDATION] User ID: " + userId);
        System.out.println("    [ANTIREQ VALIDATION] Antirequisites: " + antirequisites);

        // Get user's completed classes
        List<UserClasses> userClasses = userClassesRepository.findCompletedClassesByUserId(userId);
        
        // Get user's transfer courses with SBU equivalents (for antirequisite checking)
        List<UserTransferCourses> transferCourses = userTransferCoursesRepository.findWithSbuequivalentsByUserId(userId);
        
        System.out.println("    [ANTIREQ VALIDATION] User has " + userClasses.size() + " completed classes");
        System.out.println("    [ANTIREQ VALIDATION] User has " + transferCourses.size() + " transfer courses with SBU equivalents");

        // Parse antirequisites string
        // Format: "CSE 114; CSE 216" - if user has taken ANY of these, cannot take the course
        String[] antirequisiteGroups = antirequisites.split(";");
        
        for (String group : antirequisiteGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;
            
            System.out.println("    [ANTIREQ VALIDATION] Checking antirequisite: " + group);
            
            if (checkAntirequisiteConflict(userClasses, transferCourses, group)) {
                System.out.println("    [ANTIREQ VALIDATION] CONFLICT - User has taken antirequisite: " + group);
                return false;
            }
        }

        System.out.println("    [ANTIREQ VALIDATION] SUCCESS - No antirequisite conflicts");
        return true;
    }

    /**
     * Validate if user meets corequisites for a course
     * @param userId User ID
     * @param corequisites Corequisites string (e.g., "CSE 114")
     * @param prerequisites Prerequisites string (to check for "Pre- or corequisite" overlap)
     * @param currentSemester Current semester
     * @param currentYear Current year
     * @return true if corequisites are met, false otherwise
     */
    public boolean validateCorequisites(Long userId, String corequisites, String prerequisites, String currentSemester, Integer currentYear) {
        if (corequisites == null || corequisites.trim().isEmpty()) {
            return true; // No corequisites
        }

        System.out.println("    [COREQ VALIDATION] User ID: " + userId);
        System.out.println("    [COREQ VALIDATION] Corequisites: " + corequisites);
        System.out.println("    [COREQ VALIDATION] Prerequisites: " + prerequisites);
        System.out.println("    [COREQ VALIDATION] Current semester: " + currentSemester + " " + currentYear);

        // Get user's completed classes (past)
        List<UserClasses> userClasses = userClassesRepository.findCompletedClassesByUserId(userId);
        
        // Get user's transfer courses with SBU equivalents (for corequisite checking - completed courses)
        List<UserTransferCourses> transferCourses = userTransferCoursesRepository.findWithSbuequivalentsByUserId(userId);
        
        System.out.println("    [COREQ VALIDATION] User has " + userClasses.size() + " completed classes");
        System.out.println("    [COREQ VALIDATION] User has " + transferCourses.size() + " transfer courses with SBU equivalents");

        // Get user's current semester classes
        List<UserClasses> currentSemesterClasses = userClassesRepository.findByUserIdAndSemesterAndYear(userId, currentSemester, currentYear);
        System.out.println("    [COREQ VALIDATION] User has " + currentSemesterClasses.size() + " current semester classes");

        // Parse corequisites string
        // Format: "CSE 114; CSE 216" - user must be taking these courses in the same term
        String[] corequisiteGroups = corequisites.split(";");
        
        for (String group : corequisiteGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;
            
            System.out.println("    [COREQ VALIDATION] Checking corequisite: " + group);
            
            if (!checkCorequisiteRequirement(userClasses, transferCourses, currentSemesterClasses, group, prerequisites)) {
                System.out.println("    [COREQ VALIDATION] FAILED - Corequisite not met: " + group);
                return false;
            }
        }

        System.out.println("    [COREQ VALIDATION] SUCCESS - All corequisites met");
        return true;
    }

    /**
     * Check if a single prerequisite group is satisfied
     * @param userClasses User's completed classes
     * @param transferCourses User's transfer courses with SBU equivalents
     * @param group Prerequisite group (e.g., "BIO 351" or "C or higher in BIO 205 or BIO 207")
     * @param allUserClasses All user's classes (including in-progress) for standing calculation
     * @param userId User ID for transfer course credit calculation
     * @return true if group is satisfied
     */
    private boolean checkPrerequisiteGroup(List<UserClasses> userClasses, List<UserTransferCourses> transferCourses, String group, List<UserClasses> allUserClasses, Long userId) {
        // Handle class standing requirements like "U3 standing", "Junior standing"
        if (group.toLowerCase().contains("standing")) {
            return checkStandingRequirement(allUserClasses, userId, group);
        }
        
        // Handle grade requirements like "C or higher in BIO 205 or BIO 207" or "MAT 125 or higher"
        // Check for patterns: "or higher in", "c or higher", or "X or higher" (where X is a course code)
        String lowerGroup = group.toLowerCase();
        if (lowerGroup.contains("or higher in") || lowerGroup.contains("c or higher")) {
            return checkGradeRequirement(userClasses, transferCourses, group);
        }
        
        // Check for "X or higher" pattern where X is a course code (e.g., "MAT 125 or higher")
        // This pattern means the course must be taken with a passing grade (C or higher)
        if (lowerGroup.matches(".*\\b([a-z]{2,4})\\s+\\d{3,4}\\s+or\\s+higher\\b.*")) {
            return checkGradeRequirement(userClasses, transferCourses, group);
        }
        
        // Handle simple course requirements like "BIO 351"
        return checkSimpleCourseRequirement(userClasses, transferCourses, group);
    }

    /**
     * Check grade requirements like "C or higher in BIO 205 or BIO 207"
     */
    private boolean checkGradeRequirement(List<UserClasses> userClasses, List<UserTransferCourses> transferCourses, String group) {
        System.out.println("    [PREREQ VALIDATION] Checking grade requirement: " + group);
        
        // Extract courses from "C or higher in BIO 205 or BIO 207"
        Matcher matcher = COURSE_PATTERN.matcher(group);
        
        // If no course code found, ignore this requirement
        if (!matcher.find()) {
            System.out.println("    [PREREQ VALIDATION] No course code found in grade requirement, ignoring: " + group);
            return true; // Ignore non-course requirements
        }
        
        // Reset matcher to check all course codes in the group
        matcher = COURSE_PATTERN.matcher(group);
        
        // Check if user has taken any of the courses with C or higher grade
        while (matcher.find()) {
            String department = matcher.group(1);
            String courseNum = matcher.group(2);
            
            System.out.println("    [PREREQ VALIDATION] Looking for course: " + department + " " + courseNum);
            
            // Check if user has taken this course with C or higher grade (from regular classes or transfer courses)
            if (hasTakenCourseWithGrade(userClasses, transferCourses, department, courseNum)) {
                System.out.println("    [PREREQ VALIDATION] Found valid grade in " + department + " " + courseNum);
                return true;
            }
        }
        
        System.out.println("    [PREREQ VALIDATION] Grade requirement not met: " + group);
        return false;
    }
    
    /**
     * Check class standing requirement (e.g., "U3 standing", "Junior standing")
     * Class standing is calculated from ALL credits (including in-progress courses and transfer courses)
     * U1: 0-23 credits, U2: 24-56, U3: 57-84, U4: 85+
     */
    private boolean checkStandingRequirement(List<UserClasses> allUserClasses, Long userId, String group) {
        System.out.println("    [PREREQ VALIDATION] Checking standing requirement: " + group);
        
        // Calculate total credits (including in-progress courses)
        int totalCredits = 0;
        for (UserClasses userClass : allUserClasses) {
            if (userClass.getCredits() != null) {
                totalCredits += userClass.getCredits();
            }
        }
        
        // Also include transfer course credits
        if (userId != null) {
            List<UserTransferCourses> allTransferCourses = userTransferCoursesRepository.findByUserId(userId);
            if (allTransferCourses != null) {
                for (UserTransferCourses transferCourse : allTransferCourses) {
                    if (transferCourse.getCredits() != null) {
                        totalCredits += transferCourse.getCredits();
                    }
                }
            }
        }
        
        // Determine required standing
        String standing = calculateStanding(totalCredits);
        System.out.println("    [PREREQ VALIDATION] User has " + totalCredits + " credits (including in-progress) = " + standing);
        
        // Check for standing keywords in the requirement
        String lowerGroup = group.toLowerCase();
        
        // Check for U1, U2, U3, U4
        if (lowerGroup.contains("u4") || lowerGroup.contains("senior")) {
            System.out.println("    [PREREQ VALIDATION] Required standing: U4 (Senior)");
            return standing.equals("U4");
        } else if (lowerGroup.contains("u3") || lowerGroup.contains("junior")) {
            System.out.println("    [PREREQ VALIDATION] Required standing: U3 (Junior)");
            return standing.equals("U3") || standing.equals("U4");
        } else if (lowerGroup.contains("u2") || lowerGroup.contains("sophomore")) {
            System.out.println("    [PREREQ VALIDATION] Required standing: U2 (Sophomore)");
            return standing.equals("U2") || standing.equals("U3") || standing.equals("U4");
        } else if (lowerGroup.contains("u1") || lowerGroup.contains("freshman")) {
            System.out.println("    [PREREQ VALIDATION] Required standing: U1 (Freshman)");
            return true; // U1 is the minimum, everyone qualifies
        }
        
        // Default: if just says "standing" without specific level, assume satisfied
        System.out.println("    [PREREQ VALIDATION] Standing requirement not specific, assuming satisfied");
        return true;
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
     * Check simple course requirements like "BIO 351" or "BIO 205 or BIO 207"
     * 
     * IMPORTANT: "or" in prerequisite strings means "OR" (one of them is enough)
     * However, if the group contains multiple courses without explicit "or", 
     * we check if ALL are required (AND) or ANY is enough (OR).
     * 
     * Current logic: If "or" keyword is present, treat as OR (any one is enough).
     * Otherwise, if multiple courses are listed, check if ALL are required.
     */
    private boolean checkSimpleCourseRequirement(List<UserClasses> userClasses, List<UserTransferCourses> transferCourses, String group) {
        System.out.println("    [PREREQ VALIDATION] Checking simple course requirement: " + group);
        
        // Extract all course codes from group
        Matcher matcher = COURSE_PATTERN.matcher(group);
        List<String> courseCodes = new ArrayList<>();
        while (matcher.find()) {
            String department = matcher.group(1);
            String courseNum = matcher.group(2);
            courseCodes.add(department + " " + courseNum);
        }
        
        // If no course code found, ignore this requirement
        if (courseCodes.isEmpty()) {
            System.out.println("    [PREREQ VALIDATION] No course code found, ignoring requirement: " + group);
            return true; // Ignore non-course requirements
        }
        
        // Check if "or" keyword is present (case-insensitive)
        // If "or" is present, treat as OR (any one is enough)
        // If "or" is NOT present but multiple courses exist, treat as AND (all required)
        boolean hasOrKeyword = group.toLowerCase().matches(".*\\bor\\b.*");
        
        if (hasOrKeyword) {
            // OR logic: any one course is enough
            System.out.println("    [PREREQ VALIDATION] Detected OR logic (any one is enough)");
            for (String courseCode : courseCodes) {
                String[] parts = courseCode.split("\\s+");
                if (parts.length >= 2) {
                    String department = parts[0];
                    String courseNum = parts[1];
                    System.out.println("    [PREREQ VALIDATION] Looking for course: " + department + " " + courseNum);
                    
                    if (hasTakenCourse(userClasses, transferCourses, department, courseNum)) {
                        System.out.println("    [PREREQ VALIDATION] Found course: " + department + " " + courseNum);
                        return true;
                    }
                }
            }
            System.out.println("    [PREREQ VALIDATION] OR requirement not met: none of " + courseCodes + " found");
            return false;
        } else {
            // AND logic: all courses must be present
            // (This handles cases where multiple courses are listed without "or")
            System.out.println("    [PREREQ VALIDATION] Detected AND logic (all required)");
            for (String courseCode : courseCodes) {
                String[] parts = courseCode.split("\\s+");
                if (parts.length >= 2) {
                    String department = parts[0];
                    String courseNum = parts[1];
                    System.out.println("    [PREREQ VALIDATION] Looking for course: " + department + " " + courseNum);
                    
                    if (!hasTakenCourse(userClasses, transferCourses, department, courseNum)) {
                        System.out.println("    [PREREQ VALIDATION] Missing required course: " + department + " " + courseNum);
                        return false;
                    }
                }
            }
            System.out.println("    [PREREQ VALIDATION] All required courses found: " + courseCodes);
            return true;
        }
    }
    
    /**
     * Check if user has taken an antirequisite course
     * @param userClasses User's completed classes
     * @param transferCourses User's transfer courses with SBU equivalents
     * @param group Antirequisite group (e.g., "CSE 114")
     * @return true if conflict exists (user has taken the course), false otherwise
     */
    private boolean checkAntirequisiteConflict(List<UserClasses> userClasses, List<UserTransferCourses> transferCourses, String group) {
        System.out.println("    [ANTIREQ VALIDATION] Checking antirequisite conflict: " + group);
        
        // Extract course code from group
        Matcher matcher = COURSE_PATTERN.matcher(group);
        
        // If no course code found, ignore this requirement (no conflict)
        if (!matcher.find()) {
            System.out.println("    [ANTIREQ VALIDATION] No course code found, ignoring antirequisite: " + group);
            return false; // No conflict for non-course requirements
        }
        
        // Reset matcher to check all course codes in the group
        matcher = COURSE_PATTERN.matcher(group);
        
        // Check if user has taken any of the antirequisite courses
        while (matcher.find()) {
            String department = matcher.group(1);
            String courseNum = matcher.group(2);
            
            System.out.println("    [ANTIREQ VALIDATION] Looking for antirequisite: " + department + " " + courseNum);
            
            if (hasTakenCourse(userClasses, transferCourses, department, courseNum)) {
                System.out.println("    [ANTIREQ VALIDATION] CONFLICT FOUND - User has taken: " + department + " " + courseNum);
                return true; // Conflict found
            }
        }
        
        return false; // No conflict
    }
    
    /**
     * Check if user meets a corequisite requirement
     * @param userClasses User's completed classes (past)
     * @param transferCourses User's transfer courses with SBU equivalents
     * @param currentSemesterClasses User's current semester classes
     * @param group Corequisite group (e.g., "CSE 114")
     * @param prerequisites Prerequisites string to check for overlap
     * @return true if corequisite is met, false otherwise
     */
    private boolean checkCorequisiteRequirement(List<UserClasses> userClasses, List<UserTransferCourses> transferCourses, List<UserClasses> currentSemesterClasses, String group, String prerequisites) {
        System.out.println("    [COREQ VALIDATION] Checking corequisite requirement: " + group);
        
        // Check for range patterns like "MUS 161 through 187"
        Pattern rangePattern = Pattern.compile("\\b([A-Z]{2,4})\\s+(\\d{3,4})\\s+through\\s+(\\d{3,4})\\b", Pattern.CASE_INSENSITIVE);
        Matcher rangeMatcher = rangePattern.matcher(group);
        
        if (rangeMatcher.find()) {
            String department = rangeMatcher.group(1);
            int startCourse = Integer.parseInt(rangeMatcher.group(2));
            int endCourse = Integer.parseInt(rangeMatcher.group(3));
            
            System.out.println("    [COREQ VALIDATION] Looking for corequisite range: " + department + " " + startCourse + " through " + endCourse);
            
            // Check if user has taken any course in this range (current semester)
            for (UserClasses userClass : currentSemesterClasses) {
                if (userClass.getDepartment().equals(department)) {
                    int courseNum = userClass.getCourseNum();
                    if (courseNum >= startCourse && courseNum <= endCourse) {
                        System.out.println("    [COREQ VALIDATION] Found corequisite in range (current semester): " + department + " " + courseNum);
                        return true; // Found a course in the range
                    }
                }
            }
            
            // Also check if this course is in prerequisites and already taken (from regular classes or transfer courses)
            if (prerequisites != null && prerequisites.toLowerCase().contains(department.toLowerCase())) {
                for (UserClasses userClass : userClasses) {
                    if (userClass.getDepartment().equals(department)) {
                        int courseNum = userClass.getCourseNum();
                        if (courseNum >= startCourse && courseNum <= endCourse) {
                            System.out.println("    [COREQ VALIDATION] Found prerequisite in range (past): " + department + " " + courseNum);
                            return true; // Found a course in the range as prerequisite
                        }
                    }
                }
                // Check transfer courses
                for (UserTransferCourses transferCourse : transferCourses) {
                    if (transferCourse.getDepartment() != null && transferCourse.getDepartment().equals(department) &&
                        transferCourse.getCourseNum() != null) {
                        int courseNum = transferCourse.getCourseNum();
                        if (courseNum >= startCourse && courseNum <= endCourse) {
                            System.out.println("    [COREQ VALIDATION] Found prerequisite in range (transfer course): " + department + " " + courseNum);
                            return true; // Found a transfer course in the range as prerequisite
                        }
                    }
                }
            }
            
            System.out.println("    [COREQ VALIDATION] No course found in range " + department + " " + startCourse + " through " + endCourse);
            return false; // No course found in range
        }
        
        // Extract course code from group (for single courses)
        Matcher matcher = COURSE_PATTERN.matcher(group);
        
        // If no course code found, ignore this requirement
        if (!matcher.find()) {
            System.out.println("    [COREQ VALIDATION] No course code found, ignoring corequisite: " + group);
            return true; // Ignore non-course requirements
        }
        
        // Reset matcher to check all course codes in the group
        matcher = COURSE_PATTERN.matcher(group);
        
        // Check each course code in the group
        while (matcher.find()) {
            String department = matcher.group(1);
            String courseNum = matcher.group(2);
            String courseCode = department + " " + courseNum;
            
            System.out.println("    [COREQ VALIDATION] Looking for corequisite: " + courseCode);
            
            // Check if this course is also in prerequisites (Pre- or corequisite case)
            if (prerequisites != null && prerequisites.toLowerCase().contains(courseCode.toLowerCase())) {
                System.out.println("    [COREQ VALIDATION] Course " + courseCode + " is also in prerequisites - checking if already taken");
                
                // Check if user has already taken this course (any grade) from regular classes or transfer courses
                if (hasTakenCourse(userClasses, transferCourses, department, courseNum)) {
                    System.out.println("    [COREQ VALIDATION] Found prerequisite completion: " + courseCode);
                    return true; // Already taken as prerequisite
                }
                
                System.out.println("    [COREQ VALIDATION] Course " + courseCode + " not taken as prerequisite - corequisite required");
                return false; // Must take as corequisite
            } else {
                System.out.println("    [COREQ VALIDATION] Course " + courseCode + " is pure corequisite - checking if taken in current semester");
                
                // Check if user has taken this course in current semester (transfer courses are not current semester)
                if (hasTakenCourse(currentSemesterClasses, new ArrayList<>(), department, courseNum)) {
                    System.out.println("    [COREQ VALIDATION] Found corequisite completion (current semester): " + courseCode);
                    return true; // Already taken in current semester
                }
            }
        }
        
        System.out.println("    [COREQ VALIDATION] Corequisite requirement not met: " + group);
        return false; // Must take as corequisite
    }
    
    /**
     * Helper method to check if user has taken a specific course (from regular classes or transfer courses)
     * @param userClasses List of user's classes to check
     * @param transferCourses List of user's transfer courses with SBU equivalents
     * @param department Course department
     * @param courseNum Course number
     * @return true if user has taken the course, false otherwise
     */
    private boolean hasTakenCourse(List<UserClasses> userClasses, List<UserTransferCourses> transferCourses, String department, String courseNum) {
        // Check regular classes
        for (UserClasses userClass : userClasses) {
            if (userClass.getDepartment() != null && userClass.getDepartment().equals(department) && 
                userClass.getCourseNum() != null && userClass.getCourseNum().toString().equals(courseNum)) {
                return true;
            }
        }
        
        // Check transfer courses with SBU equivalents
        for (UserTransferCourses transferCourse : transferCourses) {
            if (transferCourse.getDepartment() != null && transferCourse.getDepartment().equals(department) &&
                transferCourse.getCourseNum() != null && transferCourse.getCourseNum().toString().equals(courseNum)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Helper method to check if user has taken a specific course with C or higher grade (from regular classes or transfer courses)
     * @param userClasses List of user's classes to check
     * @param transferCourses List of user's transfer courses with SBU equivalents
     * @param department Course department
     * @param courseNum Course number
     * @return true if user has taken the course with sufficient grade, false otherwise
     */
    private boolean hasTakenCourseWithGrade(List<UserClasses> userClasses, List<UserTransferCourses> transferCourses, String department, String courseNum) {
        // Check regular classes
        for (UserClasses userClass : userClasses) {
            if (userClass.getDepartment() != null && userClass.getDepartment().equals(department) && 
                userClass.getCourseNum() != null && userClass.getCourseNum().toString().equals(courseNum)) {
                String grade = userClass.getGrade();
                if (grade != null && isGradeSufficient(grade)) {
                    return true;
                }
            }
        }
        
        // Check transfer courses with SBU equivalents
        for (UserTransferCourses transferCourse : transferCourses) {
            if (transferCourse.getDepartment() != null && transferCourse.getDepartment().equals(department) &&
                transferCourse.getCourseNum() != null && transferCourse.getCourseNum().toString().equals(courseNum)) {
                String grade = transferCourse.getGrade();
                if (grade != null) {
                    boolean sufficient = isGradeSufficient(grade);
                    System.out.println("    [PREREQ VALIDATION] Transfer course grade check: " + department + " " + courseNum + 
                                     " grade=" + grade + " sufficient=" + sufficient);
                    if (sufficient) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * Check if grade is sufficient (C or higher)
     * Also handles special cases:
     * - 'T' = Transfer credit (credit granted, counts for prerequisites)
     * - Numeric grades (e.g., "9") = Placement exam levels (counts for prerequisites, credits = 0)
     */
    private boolean isGradeSufficient(String grade) {
        if (grade == null) return false;
        
        grade = grade.trim().toUpperCase();
        
        // Handle transfer credit - 'T' means credit granted, counts for prerequisites
        if (grade.equals("T")) {
            return true;
        }
        
        // Handle letter grades - C or higher
        if (grade.equals("A") || grade.equals("A+") || grade.equals("A-") ||
            grade.equals("B") || grade.equals("B+") || grade.equals("B-") ||
            grade.equals("C") || grade.equals("C+")) {
            return true;
        }
        
        // C- is not sufficient (below C)
        if (grade.equals("C-")) {
            return false;
        }
        
        // Handle numeric grades - could be:
        // 1. Placement exam levels (e.g., "9" for Math Proctored Placement Level 9) - counts for prerequisites
        // 2. Numeric grade on 4.0 scale
        try {
            double numericGrade = Double.parseDouble(grade);
            // If it's a single digit (1-9), it's likely a placement exam level
            // Placement exams count for prerequisites even though credits = 0
            if (numericGrade >= 1 && numericGrade <= 9 && grade.length() == 1) {
                return true; // Placement exam level - counts for prerequisites
            }
            // Otherwise, treat as numeric grade on 4.0 scale
            return numericGrade >= 2.0; // C or higher (2.0 = C)
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

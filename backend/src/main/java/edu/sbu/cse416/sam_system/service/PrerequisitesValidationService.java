package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.UserClasses;
import edu.sbu.cse416.sam_system.repository.UserClassesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TP: Used cursor to implement service for prerequisites validation.
 */
@Service
public class PrerequisitesValidationService {

    @Autowired
    private UserClassesRepository userClassesRepository;

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
        
        System.out.println("    [PREREQ VALIDATION] User has " + userClasses.size() + " completed classes");
        System.out.println("    [PREREQ VALIDATION] User has " + allUserClasses.size() + " total classes (including in-progress)");

        // Parse prerequisites string
        // Format: "BIO 351; C or higher in BIO 205 or BIO 207"
        // Each semicolon-separated part must be satisfied
        String[] prerequisiteGroups = prerequisites.split(";");
        
        for (String group : prerequisiteGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;
            
            System.out.println("    [PREREQ VALIDATION] Checking group: " + group);
            
            if (!checkPrerequisiteGroup(userClasses, group, allUserClasses)) {
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
        System.out.println("    [ANTIREQ VALIDATION] User has " + userClasses.size() + " completed classes");

        // Parse antirequisites string
        // Format: "CSE 114; CSE 216" - if user has taken ANY of these, cannot take the course
        String[] antirequisiteGroups = antirequisites.split(";");
        
        for (String group : antirequisiteGroups) {
            group = group.trim();
            if (group.isEmpty()) continue;
            
            System.out.println("    [ANTIREQ VALIDATION] Checking antirequisite: " + group);
            
            if (checkAntirequisiteConflict(userClasses, group)) {
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
        System.out.println("    [COREQ VALIDATION] User has " + userClasses.size() + " completed classes");

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
            
            if (!checkCorequisiteRequirement(userClasses, currentSemesterClasses, group, prerequisites)) {
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
     * @param group Prerequisite group (e.g., "BIO 351" or "C or higher in BIO 205 or BIO 207")
     * @param allUserClasses All user's classes (including in-progress) for standing calculation
     * @return true if group is satisfied
     */
    private boolean checkPrerequisiteGroup(List<UserClasses> userClasses, String group, List<UserClasses> allUserClasses) {
        // Handle class standing requirements like "U3 standing", "Junior standing"
        if (group.toLowerCase().contains("standing")) {
            return checkStandingRequirement(allUserClasses, group);
        }
        
        // Handle grade requirements like "C or higher in BIO 205 or BIO 207"
        if (group.toLowerCase().contains("or higher in") || group.toLowerCase().contains("c or higher")) {
            return checkGradeRequirement(userClasses, group);
        }
        
        // Handle simple course requirements like "BIO 351"
        return checkSimpleCourseRequirement(userClasses, group);
    }

    /**
     * Check grade requirements like "C or higher in BIO 205 or BIO 207"
     */
    private boolean checkGradeRequirement(List<UserClasses> userClasses, String group) {
        System.out.println("    [PREREQ VALIDATION] Checking grade requirement: " + group);
        
        // Skip placement exams and similar non-course requirements
        if (isNonCourseRequirement(group)) {
            System.out.println("    [PREREQ VALIDATION] Skipping non-course requirement: " + group);
            return true; // Consider as satisfied
        }
        
        // Extract courses from "C or higher in BIO 205 or BIO 207"
        Pattern coursePattern = Pattern.compile("\\b([A-Z]{2,4})\\s+(\\d{3,4})\\b");
        Matcher matcher = coursePattern.matcher(group);
        
        boolean foundValidCourse = false;
        while (matcher.find()) {
            String department = matcher.group(1);
            String courseNum = matcher.group(2);
            
            System.out.println("    [PREREQ VALIDATION] Looking for course: " + department + " " + courseNum);
            
            // Check if user has taken this course with C or higher
            for (UserClasses userClass : userClasses) {
                if (userClass.getDepartment().equals(department) && 
                    userClass.getCourseNum().toString().equals(courseNum)) {
                    
                    String grade = userClass.getGrade();
                    if (grade != null && isGradeSufficient(grade)) {
                        System.out.println("    [PREREQ VALIDATION] Found valid grade: " + grade + " in " + department + " " + courseNum);
                        foundValidCourse = true;
                        break;
                    }
                }
            }
            
            if (foundValidCourse) break;
        }
        
        return foundValidCourse;
    }
    
    /**
     * Check class standing requirement (e.g., "U3 standing", "Junior standing")
     * Class standing is calculated from ALL credits (including in-progress courses)
     * U1: 0-23 credits, U2: 24-56, U3: 57-84, U4: 85+
     */
    private boolean checkStandingRequirement(List<UserClasses> allUserClasses, String group) {
        System.out.println("    [PREREQ VALIDATION] Checking standing requirement: " + group);
        
        // Calculate total credits (including in-progress courses)
        int totalCredits = 0;
        for (UserClasses userClass : allUserClasses) {
            if (userClass.getCredits() != null) {
                totalCredits += userClass.getCredits();
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
     * Check if a requirement is a non-course requirement (like placement exams)
     * Note: "standing" is now handled separately, not here
     */
    private boolean isNonCourseRequirement(String group) {
        String lowerGroup = group.toLowerCase();
        
        // Check for placement exams, etc. (standing is now handled separately)
        return lowerGroup.contains("placement exam") ||
               lowerGroup.contains("major") ||
               lowerGroup.contains("dec") ||
               lowerGroup.contains("sbc") ||
               lowerGroup.contains("permission") ||
               lowerGroup.contains("instructor") ||
               lowerGroup.contains("advisory") ||
               lowerGroup.contains("audition required") ||
               lowerGroup.contains("audition") ||
               lowerGroup.contains("interview") ||
               lowerGroup.contains("portfolio");
    }

    /**
     * Check simple course requirements like "BIO 351"
     */
    private boolean checkSimpleCourseRequirement(List<UserClasses> userClasses, String group) {
        System.out.println("    [PREREQ VALIDATION] Checking simple course requirement: " + group);
        
        // Skip placement exams and similar non-course requirements
        if (isNonCourseRequirement(group)) {
            System.out.println("    [PREREQ VALIDATION] Skipping non-course requirement: " + group);
            return true; // Consider as satisfied
        }
        
        // Extract course code from group
        Pattern coursePattern = Pattern.compile("\\b([A-Z]{2,4})\\s+(\\d{3,4})\\b");
        Matcher matcher = coursePattern.matcher(group);
        
        if (matcher.find()) {
            String department = matcher.group(1);
            String courseNum = matcher.group(2);
            
            System.out.println("    [PREREQ VALIDATION] Looking for course: " + department + " " + courseNum);
            
            // Check if user has taken this course
            for (UserClasses userClass : userClasses) {
                if (userClass.getDepartment().equals(department) && 
                    userClass.getCourseNum().toString().equals(courseNum)) {
                    System.out.println("    [PREREQ VALIDATION] Found course: " + department + " " + courseNum);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if user has taken an antirequisite course
     * @param userClasses User's completed classes
     * @param group Antirequisite group (e.g., "CSE 114")
     * @return true if conflict exists (user has taken the course), false otherwise
     */
    private boolean checkAntirequisiteConflict(List<UserClasses> userClasses, String group) {
        System.out.println("    [ANTIREQ VALIDATION] Checking antirequisite conflict: " + group);
        
        // Skip non-course requirements
        if (isNonCourseRequirement(group)) {
            System.out.println("    [ANTIREQ VALIDATION] Skipping non-course antirequisite: " + group);
            return false; // No conflict for non-course requirements
        }
        
        // Extract course code from group
        Pattern coursePattern = Pattern.compile("\\b([A-Z]{2,4})\\s+(\\d{3,4})\\b");
        Matcher matcher = coursePattern.matcher(group);
        
        if (matcher.find()) {
            String department = matcher.group(1);
            String courseNum = matcher.group(2);
            
            System.out.println("    [ANTIREQ VALIDATION] Looking for antirequisite: " + department + " " + courseNum);
            
            // Check if user has taken this course (any grade)
            for (UserClasses userClass : userClasses) {
                if (userClass.getDepartment().equals(department) && 
                    userClass.getCourseNum().toString().equals(courseNum)) {
                    System.out.println("    [ANTIREQ VALIDATION] CONFLICT FOUND - User has taken: " + department + " " + courseNum);
                    return true; // Conflict found
                }
            }
        }
        
        return false; // No conflict
    }
    
    /**
     * Check if user meets a corequisite requirement
     * @param userClasses User's completed classes (past)
     * @param currentSemesterClasses User's current semester classes
     * @param group Corequisite group (e.g., "CSE 114")
     * @param prerequisites Prerequisites string to check for overlap
     * @return true if corequisite is met, false otherwise
     */
    private boolean checkCorequisiteRequirement(List<UserClasses> userClasses, List<UserClasses> currentSemesterClasses, String group, String prerequisites) {
        System.out.println("    [COREQ VALIDATION] Checking corequisite requirement: " + group);
        
        // Skip non-course requirements
        if (isNonCourseRequirement(group)) {
            System.out.println("    [COREQ VALIDATION] Skipping non-course corequisite: " + group);
            return true; // Consider as satisfied
        }
        
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
            
            // Also check if this course is in prerequisites and already taken
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
            }
            
            System.out.println("    [COREQ VALIDATION] No course found in range " + department + " " + startCourse + " through " + endCourse);
            return false; // No course found in range
        }
        
        // Extract course code from group (for single courses)
        Pattern coursePattern = Pattern.compile("\\b([A-Z]{2,4})\\s+(\\d{3,4})\\b");
        Matcher matcher = coursePattern.matcher(group);
        
        if (matcher.find()) {
            String department = matcher.group(1);
            String courseNum = matcher.group(2);
            String courseCode = department + " " + courseNum;
            
            System.out.println("    [COREQ VALIDATION] Looking for corequisite: " + courseCode);
            
            // Check if this course is also in prerequisites (Pre- or corequisite case)
            if (prerequisites != null && prerequisites.toLowerCase().contains(courseCode.toLowerCase())) {
                System.out.println("    [COREQ VALIDATION] Course " + courseCode + " is also in prerequisites - checking if already taken");
                
                // Check if user has already taken this course (any grade)
                for (UserClasses userClass : userClasses) {
                    if (userClass.getDepartment().equals(department) && 
                        userClass.getCourseNum().toString().equals(courseNum)) {
                        System.out.println("    [COREQ VALIDATION] Found prerequisite completion: " + courseCode);
                        return true; // Already taken as prerequisite
                    }
                }
                
                System.out.println("    [COREQ VALIDATION] Course " + courseCode + " not taken as prerequisite - corequisite required");
                return false; // Must take as corequisite
            } else {
                System.out.println("    [COREQ VALIDATION] Course " + courseCode + " is pure corequisite - checking if taken in current semester");
                
                // Check if user has taken this course in current semester
                for (UserClasses userClass : currentSemesterClasses) {
                    if (userClass.getDepartment().equals(department) && 
                        userClass.getCourseNum().toString().equals(courseNum)) {
                        System.out.println("    [COREQ VALIDATION] Found corequisite completion (current semester): " + courseCode);
                        return true; // Already taken in current semester
                    }
                }
                
                System.out.println("    [COREQ VALIDATION] Course " + courseCode + " not taken in current semester - corequisite required");
                return false; // Must take as corequisite
            }
        }
        
        return false; // No valid course found
    }

    /**
     * Check if grade is sufficient (C or higher)
     */
    private boolean isGradeSufficient(String grade) {
        if (grade == null) return false;
        
        grade = grade.trim().toUpperCase();
        
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
        
        // Handle numeric grades (assuming 4.0 scale)
        try {
            double numericGrade = Double.parseDouble(grade);
            return numericGrade >= 2.0; // C or higher (2.0 = C)
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

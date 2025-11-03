package edu.sbu.cse416.sam_system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sbu.cse416.sam_system.entity.*;
import edu.sbu.cse416.sam_system.repository.DegreeRequirementRepository;
import edu.sbu.cse416.sam_system.repository.UserClassesRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for checking student's degree requirement status.
 * Evaluates which requirements are satisfied for declared/planned majors and minors.
 * 
 * TP: Used Cursor to implement degree requirement status checking system
 */
@Service
@Transactional
public class DegreeRequirementStatusService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserClassesRepository userClassesRepository;
    
    @Autowired
    private DegreeRequirementRepository degreeRequirementRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Get degree requirement status for a student
     * @param userId Student user ID
     * @return Map containing status for each declared/planned major and minor
     */
    public Map<String, Object> getDegreeRequirementsStatus(Long userId) {
        Map<String, Object> result = new HashMap<>();
        
        User student = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + userId));
        
        // Get student's completed and in-progress classes
        List<UserClasses> allUserClasses = userClassesRepository.findByUserId(userId);
        List<UserClasses> completedClasses = allUserClasses.stream()
                .filter(uc -> uc.getGrade() != null && !uc.getGrade().trim().isEmpty() && !uc.getGrade().equals("I"))
                .collect(Collectors.toList());
        List<UserClasses> inProgressClasses = allUserClasses.stream()
                .filter(uc -> uc.getGrade() == null || uc.getGrade().trim().isEmpty() || uc.getGrade().equals("I"))
                .collect(Collectors.toList());
        
        // Parse declared majors and minors
        List<Map<String, Object>> declaredMajors = parseMajorsOrMinors(student.getMajors(), student.getDegrees(), student.getMajorRequirementVersions());
        List<Map<String, Object>> declaredMinors = parseMajorsOrMinors(student.getMinors(), "MINOR", student.getMinorRequirementVersions());
        List<Map<String, Object>> plannedMajors = parseMajorsOrMinors(student.getPlannedMajors(), student.getPlannedDegrees(), student.getPlannedMajorRequirementVersions());
        List<Map<String, Object>> plannedMinors = parseMajorsOrMinors(student.getPlannedMinors(), "MINOR", student.getPlannedMinorRequirementVersions());
        
        // Check requirements for each declared major
        List<Map<String, Object>> declaredMajorStatuses = new ArrayList<>();
        for (Map<String, Object> major : declaredMajors) {
            Map<String, Object> status = checkRequirementStatus(major, completedClasses, inProgressClasses, allUserClasses);
            declaredMajorStatuses.add(status);
        }
        
        // Check requirements for each declared minor
        List<Map<String, Object>> declaredMinorStatuses = new ArrayList<>();
        for (Map<String, Object> minor : declaredMinors) {
            Map<String, Object> status = checkRequirementStatus(minor, completedClasses, inProgressClasses, allUserClasses);
            declaredMinorStatuses.add(status);
        }
        
        // Check requirements for each planned major
        List<Map<String, Object>> plannedMajorStatuses = new ArrayList<>();
        for (Map<String, Object> major : plannedMajors) {
            Map<String, Object> status = checkRequirementStatus(major, completedClasses, inProgressClasses, allUserClasses);
            plannedMajorStatuses.add(status);
        }
        
        // Check requirements for each planned minor
        List<Map<String, Object>> plannedMinorStatuses = new ArrayList<>();
        for (Map<String, Object> minor : plannedMinors) {
            Map<String, Object> status = checkRequirementStatus(minor, completedClasses, inProgressClasses, allUserClasses);
            plannedMinorStatuses.add(status);
        }
        
        result.put("declaredMajors", declaredMajorStatuses);
        result.put("declaredMinors", declaredMinorStatuses);
        result.put("plannedMajors", plannedMajorStatuses);
        result.put("plannedMinors", plannedMinorStatuses);
        result.put("studentId", userId);
        result.put("studentName", student.getFirstName() + " " + student.getLastName());
        
        return result;
    }
    
    /**
     * Parse majors or minors JSON arrays
     * @param subjectsJson JSON array of subject codes
     * @param degreesJson JSON array of degree types, or string "MINOR" for all minors, or null
     * @param versionsJson JSON array of requirement versions
     */
    private List<Map<String, Object>> parseMajorsOrMinors(String subjectsJson, String degreesJson, String versionsJson) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        if (subjectsJson == null || subjectsJson.trim().isEmpty()) {
            return result;
        }
        
        try {
            List<?> subjects = objectMapper.readValue(subjectsJson, List.class);
            List<?> degrees = null;
            
            // Handle case where degreesJson is "MINOR" string (for minors)
            if ("MINOR".equals(degreesJson)) {
                // All items are minors
                for (int i = 0; i < subjects.size(); i++) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("subjectCode", subjects.get(i).toString());
                    item.put("degreeType", "MINOR");
                    
                    List<?> versions = (versionsJson != null && !versionsJson.trim().isEmpty())
                            ? objectMapper.readValue(versionsJson, List.class) : null;
                    if (versions != null && i < versions.size()) {
                        Object versionObj = versions.get(i);
                        if (versionObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> version = (Map<String, Object>) versionObj;
                            item.put("requirementVersion", version);
                        }
                    }
                    result.add(item);
                }
                return result;
            }
            
            // Regular parsing for majors
            degrees = (degreesJson != null && !degreesJson.trim().isEmpty()) 
                    ? objectMapper.readValue(degreesJson, List.class) : null;
            List<?> versions = (versionsJson != null && !versionsJson.trim().isEmpty())
                    ? objectMapper.readValue(versionsJson, List.class) : null;
            
            for (int i = 0; i < subjects.size(); i++) {
                Map<String, Object> item = new HashMap<>();
                item.put("subjectCode", subjects.get(i).toString());
                if (degrees != null && i < degrees.size()) {
                    item.put("degreeType", degrees.get(i).toString());
                }
                if (versions != null && i < versions.size()) {
                    Object versionObj = versions.get(i);
                    if (versionObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> version = (Map<String, Object>) versionObj;
                        item.put("requirementVersion", version);
                    }
                }
                result.add(item);
            }
        } catch (Exception e) {
            System.err.println("Error parsing majors/minors: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Check requirement status for a specific major or minor
     */
    private Map<String, Object> checkRequirementStatus(Map<String, Object> majorOrMinor, 
                                                       List<UserClasses> completedClasses,
                                                       List<UserClasses> inProgressClasses,
                                                       List<UserClasses> allUserClasses) {
        Map<String, Object> status = new HashMap<>();
        status.put("subjectCode", majorOrMinor.get("subjectCode"));
        status.put("degreeType", majorOrMinor.get("degreeType"));
        
        String subjectCode = (String) majorOrMinor.get("subjectCode");
        String degreeTypeStr = (String) majorOrMinor.get("degreeType");
        
        if (degreeTypeStr == null) {
            // Try to determine degree type from DegreeRequirement
            List<DegreeRequirement> reqs = degreeRequirementRepository.findBySubjectCodeAndActive(subjectCode);
            if (!reqs.isEmpty()) {
                degreeTypeStr = reqs.get(0).getDegreeType().toString();
                status.put("degreeType", degreeTypeStr);
            }
        }
        
        // Find the degree requirement
        DegreeRequirement requirement = null;
        if (degreeTypeStr != null) {
            try {
                DegreeType degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
                requirement = degreeRequirementRepository
                        .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType)
                        .orElse(null);
            } catch (IllegalArgumentException e) {
                // Invalid degree type
            }
        }
        
        if (requirement == null) {
            status.put("status", "NOT_FOUND");
            status.put("message", "Degree requirement not found for " + subjectCode);
            return status;
        }
        
        status.put("requirementId", requirement.getRequirementId());
        status.put("requirementTitle", requirement.getSubjectCode() + " " + degreeTypeStr);
        
        // Parse course requirements JSON
        String courseReqsJson = requirement.getCourseRequirements();
        if (courseReqsJson == null || courseReqsJson.trim().isEmpty()) {
            status.put("status", "NO_REQUIREMENTS");
            status.put("message", "No course requirements defined");
            return status;
        }
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> courseReqs = objectMapper.readValue(courseReqsJson, Map.class);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groups = (List<Map<String, Object>>) courseReqs.get("groups");
            
            if (groups == null || groups.isEmpty()) {
                status.put("status", "NO_GROUPS");
                status.put("message", "No requirement groups defined");
                return status;
            }
            
            // First pass: Check non-electives groups to track used courses
            Set<String> usedClassIds = new HashSet<>();
            List<Map<String, Object>> groupStatuses = new ArrayList<>();
            
            // Check non-electives groups first
            for (Map<String, Object> group : groups) {
                String type = (String) group.get("type");
                if (!"electives".equals(type)) {
                    Map<String, Object> groupStatus = checkGroupStatus(group, completedClasses, inProgressClasses, allUserClasses, usedClassIds);
                    groupStatuses.add(groupStatus);
                    // Track used classes from this group
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> satisfiedCourses = (List<Map<String, Object>>) groupStatus.get("satisfiedCourses");
                    if (satisfiedCourses != null) {
                        for (Map<String, Object> courseInfo : satisfiedCourses) {
                            Object classId = courseInfo.get("classId");
                            if (classId != null) {
                                usedClassIds.add(classId.toString());
                            }
                        }
                    }
                }
            }
            
            // Second pass: Check electives groups with used classes excluded
            for (Map<String, Object> group : groups) {
                String type = (String) group.get("type");
                if ("electives".equals(type)) {
                    Map<String, Object> groupStatus = checkGroupStatus(group, completedClasses, inProgressClasses, allUserClasses, usedClassIds);
                    groupStatuses.add(groupStatus);
                }
            }
            
            status.put("groups", groupStatuses);
            
            // Calculate overall status
            long satisfiedGroups = groupStatuses.stream()
                    .filter(gs -> "SATISFIED".equals(gs.get("status")))
                    .count();
            long totalGroups = groupStatuses.size();
            
            status.put("satisfiedGroups", satisfiedGroups);
            status.put("totalGroups", totalGroups);
            status.put("overallStatus", satisfiedGroups == totalGroups ? "SATISFIED" : "IN_PROGRESS");
            status.put("progressPercentage", totalGroups > 0 ? (int) (satisfiedGroups * 100 / totalGroups) : 0);
            
        } catch (Exception e) {
            status.put("status", "ERROR");
            status.put("message", "Error parsing course requirements: " + e.getMessage());
        }
        
        return status;
    }
    
    /**
     * Check status of a single requirement group
     */
    private Map<String, Object> checkGroupStatus(Map<String, Object> group,
                                                 List<UserClasses> completedClasses,
                                                 List<UserClasses> inProgressClasses,
                                                 List<UserClasses> allUserClasses,
                                                 Set<String> usedClassIds) {
        Map<String, Object> groupStatus = new HashMap<>();
        groupStatus.put("groupId", group.get("id"));
        groupStatus.put("groupType", group.get("type"));
        
        String type = (String) group.get("type");
        
        try {
            boolean satisfied = false;
            List<Map<String, Object>> satisfiedCourses = new ArrayList<>();
            
            switch (type) {
                case "all_of":
                    satisfied = checkAllOfGroup(group, completedClasses, satisfiedCourses);
                    break;
                case "one_of":
                    satisfied = checkOneOfGroup(group, completedClasses, satisfiedCourses);
                    break;
                case "one_of_sequences":
                    satisfied = checkOneOfSequencesGroup(group, completedClasses, satisfiedCourses, usedClassIds);
                    break;
                case "electives":
                    satisfied = checkElectivesGroup(group, completedClasses, inProgressClasses, allUserClasses, satisfiedCourses, usedClassIds);
                    break;
                case "two_of":
                    satisfied = checkTwoOfGroup(group, completedClasses, satisfiedCourses);
                    break;
                default:
                    groupStatus.put("status", "UNKNOWN_TYPE");
                    groupStatus.put("message", "Unknown group type: " + type);
                    return groupStatus;
            }
            
            groupStatus.put("status", satisfied ? "SATISFIED" : "NOT_SATISFIED");
            groupStatus.put("satisfiedCourses", satisfiedCourses);
            
            // Add additional info based on type
            if ("electives".equals(type) && group.containsKey("credits_required")) {
                int creditsRequired = (Integer) group.get("credits_required");
                int creditsSatisfied = satisfiedCourses.stream()
                        .mapToInt(sc -> {
                            Object credits = sc.get("credits");
                            return credits instanceof Number ? ((Number) credits).intValue() : 0;
                        })
                        .sum();
                groupStatus.put("creditsRequired", creditsRequired);
                groupStatus.put("creditsSatisfied", creditsSatisfied);
            }
            
        } catch (Exception e) {
            groupStatus.put("status", "ERROR");
            groupStatus.put("message", "Error checking group: " + e.getMessage());
        }
        
        return groupStatus;
    }
    
    /**
     * Check all_of group: all courses must be completed
     */
    private boolean checkAllOfGroup(Map<String, Object> group, List<UserClasses> completedClasses, 
                                    List<Map<String, Object>> satisfiedCourses) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return true;
        }
        
        boolean allFound = true;
        for (Map<String, Object> courseReq : courses) {
            String code = (String) courseReq.get("code");
            if (code == null) continue;
            
            String[] parts = parseCourseCode(code);
            String dept = parts[0];
            String courseNumStr = parts[1];
            
            UserClasses matchingClass = findMatchingClass(completedClasses, dept, courseNumStr);
            if (matchingClass != null) {
                satisfiedCourses.add(buildCourseInfo(matchingClass, code));
            } else {
                allFound = false; // One course missing, but continue to collect all satisfied courses
            }
        }
        
        return allFound && satisfiedCourses.size() == courses.size();
    }
    
    /**
     * Check one_of group: at least one course must be completed
     */
    private boolean checkOneOfGroup(Map<String, Object> group, List<UserClasses> completedClasses,
                                    List<Map<String, Object>> satisfiedCourses) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return true;
        }
        
        for (Map<String, Object> courseReq : courses) {
            String code = (String) courseReq.get("code");
            if (code == null) continue;
            
            String[] parts = parseCourseCode(code);
            String dept = parts[0];
            String courseNumStr = parts[1];
            
            UserClasses matchingClass = findMatchingClass(completedClasses, dept, courseNumStr);
            if (matchingClass != null) {
                satisfiedCourses.add(buildCourseInfo(matchingClass, code));
                return true; // Found one, that's enough
            }
        }
        
        return false;
    }
    
    /**
     * Check one_of_sequences group: at least one complete sequence must be completed
     * Also adds partially completed courses to satisfiedCourses
     */
    private boolean checkOneOfSequencesGroup(Map<String, Object> group, List<UserClasses> completedClasses,
                                             List<Map<String, Object>> satisfiedCourses,
                                             Set<String> usedClassIds) {
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
        if (sequences == null || sequences.isEmpty()) {
            return true;
        }
        
        boolean foundCompleteSequence = false;
        
        // First, check for complete sequences
        for (List<Map<String, Object>> sequence : sequences) {
            boolean sequenceComplete = true;
            List<Map<String, Object>> sequenceCourses = new ArrayList<>();
            
            for (Map<String, Object> courseReq : sequence) {
                String code = (String) courseReq.get("code");
                if (code == null) continue;
                
                String[] parts = parseCourseCode(code);
                String dept = parts[0];
                String courseNumStr = parts[1];
                
                UserClasses matchingClass = findMatchingClass(completedClasses, dept, courseNumStr);
                if (matchingClass != null) {
                    sequenceCourses.add(buildCourseInfo(matchingClass, code));
                } else {
                    sequenceComplete = false;
                    break; // This sequence is not complete
                }
            }
            
            if (sequenceComplete && sequenceCourses.size() == sequence.size()) {
                satisfiedCourses.addAll(sequenceCourses);
                foundCompleteSequence = true;
                break; // Found one complete sequence, that's enough
            }
        }
        
        // If no complete sequence found, still add partially completed courses
        if (!foundCompleteSequence) {
            for (List<Map<String, Object>> sequence : sequences) {
                for (Map<String, Object> courseReq : sequence) {
                    String code = (String) courseReq.get("code");
                    if (code == null) continue;
                    
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    String courseNumStr = parts[1];
                    
                    UserClasses matchingClass = findMatchingClass(completedClasses, dept, courseNumStr);
                    if (matchingClass != null) {
                        // Check if already added (avoid duplicates)
                        boolean alreadyAdded = satisfiedCourses.stream()
                                .anyMatch(sc -> matchingClass.getClassId().equals(sc.get("classId")));
                        if (!alreadyAdded) {
                            satisfiedCourses.add(buildCourseInfo(matchingClass, code));
                        }
                    }
                }
            }
        }
        
        return foundCompleteSequence;
    }
    
    /**
     * Check electives group: must satisfy credits_required from courses/sequences/additional_courses
     * Excludes classes already used in other requirement groups
     */
    private boolean checkElectivesGroup(Map<String, Object> group, List<UserClasses> completedClasses,
                                       List<UserClasses> inProgressClasses, List<UserClasses> allUserClasses,
                                       List<Map<String, Object>> satisfiedCourses,
                                       Set<String> usedClassIds) {
        Integer creditsRequired = (Integer) group.get("credits_required");
        if (creditsRequired == null) {
            return false;
        }
        
        int totalCredits = 0;
        
        // Check sequences first (if any sequence is complete, all courses in that sequence count)
        // For natural science requirement type: "Must include at least one lab sequence"
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
        if (sequences != null && !sequences.isEmpty()) {
            for (List<Map<String, Object>> sequence : sequences) {
                boolean sequenceComplete = true;
                List<UserClasses> sequenceMatches = new ArrayList<>();
                
                for (Map<String, Object> courseReq : sequence) {
                    String code = (String) courseReq.get("code");
                    if (code == null) continue;
                    
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    String courseNumStr = parts[1];
                    
                    UserClasses matchingClass = findMatchingClass(allUserClasses, dept, courseNumStr);
                    String classIdStr = matchingClass != null && matchingClass.getClassId() != null ? matchingClass.getClassId().toString() : null;
                    if (matchingClass != null && classIdStr != null && !usedClassIds.contains(classIdStr)) {
                        sequenceMatches.add(matchingClass);
                    } else {
                        sequenceComplete = false;
                        break;
                    }
                }
                
                if (sequenceComplete && !sequenceMatches.isEmpty()) {
                    // Check excluded courses
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                    
                    for (UserClasses uc : sequenceMatches) {
                        String classIdStr = uc.getClassId() != null ? uc.getClassId().toString() : null;
                        if (uc.getCredits() != null && classIdStr != null && !usedClassIds.contains(classIdStr)) {
                            // Check if course is excluded
                            if (excludedCourses == null || !isExcluded(uc, excludedCourses)) {
                                totalCredits += uc.getCredits();
                                satisfiedCourses.add(buildCourseInfo(uc, uc.getDepartment() + " " + uc.getCourseNum()));
                                usedClassIds.add(classIdStr);
                            }
                        }
                    }
                    // Only count the first complete sequence (natural science requirement needs at least one)
                    // But we continue to add more sequences if needed for credits
                }
            }
        }
        
        // Check additional_courses
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> additionalCourses = (List<Map<String, Object>>) group.get("additional_courses");
        if (additionalCourses != null && !additionalCourses.isEmpty()) {
            for (Map<String, Object> courseReq : additionalCourses) {
                if (totalCredits >= creditsRequired) break;
                
                String code = (String) courseReq.get("code");
                if (code == null) continue;
                
                String[] parts = parseCourseCode(code);
                String dept = parts[0];
                String courseNumStr = parts[1];
                
                UserClasses matchingClass = findMatchingClass(allUserClasses, dept, courseNumStr);
                String classIdStr = matchingClass != null && matchingClass.getClassId() != null ? matchingClass.getClassId().toString() : null;
                if (matchingClass != null && classIdStr != null && !usedClassIds.contains(classIdStr)) {
                    // Check excluded courses
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                    if (excludedCourses == null || !isExcluded(matchingClass, excludedCourses)) {
                        if (matchingClass.getCredits() != null) {
                            totalCredits += matchingClass.getCredits();
                            satisfiedCourses.add(buildCourseInfo(matchingClass, code));
                            usedClassIds.add(classIdStr);
                        }
                    }
                }
            }
        }
        
        // Check regular courses list
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses != null && !courses.isEmpty()) {
            // Handle special patterns like "CSE 3xx+" (upper division)
            for (Map<String, Object> courseReq : courses) {
                if (totalCredits >= creditsRequired) break;
                
                String code = (String) courseReq.get("code");
                if (code == null) continue;
                
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
                    
                    // Find matching upper-division courses
                    for (UserClasses uc : allUserClasses) {
                        if (totalCredits >= creditsRequired) break;
                        String classIdStr = uc.getClassId() != null ? uc.getClassId().toString() : null;
                        if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) &&
                            uc.getCourseNum() != null && uc.getCourseNum() >= minLevel &&
                            classIdStr != null && !usedClassIds.contains(classIdStr)) {
                            
                            // Check excluded courses
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                            if (excludedCourses == null || !isExcluded(uc, excludedCourses)) {
                                if (uc.getCredits() != null) {
                                    totalCredits += uc.getCredits();
                                    satisfiedCourses.add(buildCourseInfo(uc, uc.getDepartment() + " " + uc.getCourseNum()));
                                    usedClassIds.add(classIdStr);
                                }
                            }
                        }
                    }
                } else {
                    // Regular course code
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    String courseNumStr = parts[1];
                    
                    UserClasses matchingClass = findMatchingClass(allUserClasses, dept, courseNumStr);
                    String classIdStr = matchingClass != null && matchingClass.getClassId() != null ? matchingClass.getClassId().toString() : null;
                    if (matchingClass != null && classIdStr != null && !usedClassIds.contains(classIdStr)) {
                        // Check excluded courses
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                        if (excludedCourses == null || !isExcluded(matchingClass, excludedCourses)) {
                            if (matchingClass.getCredits() != null) {
                                totalCredits += matchingClass.getCredits();
                                satisfiedCourses.add(buildCourseInfo(matchingClass, code));
                                usedClassIds.add(classIdStr);
                            }
                        }
                    }
                }
            }
        }
        
        return totalCredits >= creditsRequired;
    }
    
    /**
     * Check two_of group: exactly two courses must be completed
     */
    private boolean checkTwoOfGroup(Map<String, Object> group, List<UserClasses> completedClasses,
                                   List<Map<String, Object>> satisfiedCourses) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return true;
        }
        
        int count = 0;
        for (Map<String, Object> courseReq : courses) {
            String code = (String) courseReq.get("code");
            if (code == null) continue;
            
            String[] parts = parseCourseCode(code);
            String dept = parts[0];
            String courseNumStr = parts[1];
            
            UserClasses matchingClass = findMatchingClass(completedClasses, dept, courseNumStr);
            if (matchingClass != null) {
                satisfiedCourses.add(buildCourseInfo(matchingClass, code));
                count++;
            }
        }
        
        return count >= 2;
    }
    
    /**
     * Parse course code like "CSE 114" or "CSE 3xx+" into department and course number
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
     * Find matching UserClasses for a department and course number
     */
    private UserClasses findMatchingClass(List<UserClasses> classes, String department, String courseNumStr) {
        if (department == null || courseNumStr == null || courseNumStr.isEmpty()) {
            return null;
        }
        
        Integer courseNum;
        try {
            courseNum = Integer.parseInt(courseNumStr);
        } catch (NumberFormatException e) {
            return null; // Can't parse course number
        }
        
        return classes.stream()
                .filter(uc -> uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(department) &&
                             uc.getCourseNum() != null && uc.getCourseNum().equals(courseNum))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Check if a UserClasses matches an excluded course
     */
    private boolean isExcluded(UserClasses uc, List<Map<String, Object>> excludedCourses) {
        for (Map<String, Object> excluded : excludedCourses) {
            String code = (String) excluded.get("code");
            if (code == null) continue;
            
            String[] parts = parseCourseCode(code);
            String dept = parts[0];
            String courseNumStr = parts[1];
            
            if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept)) {
                try {
                    Integer courseNum = Integer.parseInt(courseNumStr);
                    if (uc.getCourseNum() != null && uc.getCourseNum().equals(courseNum)) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        return false;
    }
    
    /**
     * Build course info map for response
     */
    private Map<String, Object> buildCourseInfo(UserClasses uc, String requirementCode) {
        Map<String, Object> info = new HashMap<>();
        info.put("requirementCode", requirementCode);
        info.put("department", uc.getDepartment());
        info.put("courseNum", uc.getCourseNum());
        info.put("section", uc.getSection());
        info.put("semester", uc.getSemester());
        info.put("year", uc.getYear());
        info.put("credits", uc.getCredits());
        info.put("grade", uc.getGrade());
        info.put("classId", uc.getClassId());
        return info;
    }
}


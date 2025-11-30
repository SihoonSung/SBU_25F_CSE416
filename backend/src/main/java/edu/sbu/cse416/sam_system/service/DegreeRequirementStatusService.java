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
        User student = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + userId));
        return computeDegreeRequirementStatus(student, Collections.emptyList());
    }

    /**
     * Get degree requirement status including planned courses (planner forecast).
     */
    public Map<String, Object> getDegreeRequirementsStatusWithPlannedCourses(Long userId, List<UserCoursePlan> plannedCourses) {
        User student = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + userId));
        List<UserClasses> syntheticClasses = createSyntheticClasses(student, plannedCourses);
        return computeDegreeRequirementStatus(student, syntheticClasses);
    }

    private Map<String, Object> computeDegreeRequirementStatus(User student, List<UserClasses> additionalClasses) {
        Map<String, Object> result = new HashMap<>();

        List<UserClasses> baseUserClasses = userClassesRepository.findByUserId(student.getUserId());
        List<UserClasses> allUserClasses = new ArrayList<>(baseUserClasses);
        if (additionalClasses != null && !additionalClasses.isEmpty()) {
            allUserClasses.addAll(additionalClasses);
        }

        // Separate classes by grade status:
        // - completedClasses: all classes with grades (regardless of grade value) - for checking if grade meets min_grade
        // - inProgressClasses: classes with null/empty grade (pending) - grade가 null인 경우
        List<UserClasses> completedClasses = allUserClasses.stream()
                .filter(uc -> {
                    String grade = uc.getGrade();
                    return grade != null && !grade.trim().isEmpty() && !grade.equals("I");
                })
                .collect(Collectors.toList());
        List<UserClasses> inProgressClasses = allUserClasses.stream()
                .filter(uc -> {
                    String grade = uc.getGrade();
                    return grade == null || grade.trim().isEmpty() || grade.equals("I");
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> declaredMajors = parseMajorsOrMinors(student.getMajors(), student.getDegrees(), student.getMajorRequirementVersions());
        List<Map<String, Object>> declaredMinors = parseMajorsOrMinors(student.getMinors(), "MINOR", student.getMinorRequirementVersions());
        List<Map<String, Object>> plannedMajors = parseMajorsOrMinors(student.getPlannedMajors(), student.getPlannedDegrees(), student.getPlannedMajorRequirementVersions());
        List<Map<String, Object>> plannedMinors = parseMajorsOrMinors(student.getPlannedMinors(), "MINOR", student.getPlannedMinorRequirementVersions());

        List<Map<String, Object>> declaredMajorStatuses = evaluateRequirements(declaredMajors, completedClasses, inProgressClasses, allUserClasses);
        List<Map<String, Object>> declaredMinorStatuses = evaluateRequirements(declaredMinors, completedClasses, inProgressClasses, allUserClasses);
        List<Map<String, Object>> plannedMajorStatuses = evaluateRequirements(plannedMajors, completedClasses, inProgressClasses, allUserClasses);
        List<Map<String, Object>> plannedMinorStatuses = evaluateRequirements(plannedMinors, completedClasses, inProgressClasses, allUserClasses);

        result.put("declaredMajors", declaredMajorStatuses);
        result.put("declaredMinors", declaredMinorStatuses);
        result.put("plannedMajors", plannedMajorStatuses);
        result.put("plannedMinors", plannedMinorStatuses);
        result.put("studentId", student.getUserId());
        result.put("studentName", student.getFirstName() + " " + student.getLastName());

        return result;
    }

    private List<Map<String, Object>> evaluateRequirements(List<Map<String, Object>> programs,
                                                           List<UserClasses> completedClasses,
                                                           List<UserClasses> inProgressClasses,
                                                           List<UserClasses> allUserClasses) {
        List<Map<String, Object>> statuses = new ArrayList<>();
        for (Map<String, Object> program : programs) {
            Map<String, Object> status = checkRequirementStatus(program, completedClasses, inProgressClasses, allUserClasses);
            statuses.add(status);
        }
        return statuses;
    }

    private List<UserClasses> createSyntheticClasses(User student, List<UserCoursePlan> plannedCourses) {
        if (plannedCourses == null || plannedCourses.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserClasses> synthetic = new ArrayList<>();
        for (UserCoursePlan plan : plannedCourses) {
            UserClasses uc = new UserClasses();
            uc.setUser(student);
            uc.setClassId(null);
            uc.setDepartment(plan.getDepartment());
            uc.setCourseNum(plan.getCourseNum());
            uc.setSection(null);
            uc.setSemester(plan.getSemester());
            uc.setYear(plan.getYear());
            uc.setCredits(plan.getCredits());
            uc.setGpnc(null);
            uc.setGrade(null); // treat as in-progress
            synthetic.add(uc);
        }
        return synthetic;
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
            
            // Check SBC requirements
            String sbcReqsJson = requirement.getSbcRequirements();
            if (sbcReqsJson != null && !sbcReqsJson.trim().isEmpty()) {
                Map<String, Object> sbcStatus = checkSbcRequirements(sbcReqsJson, completedClasses, inProgressClasses, allUserClasses);
                status.put("sbcRequirements", sbcStatus);
            }
            
        } catch (Exception e) {
            status.put("status", "ERROR");
            status.put("message", "Error parsing course requirements: " + e.getMessage());
        }
        
        return status;
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
                                                       List<UserClasses> completedClasses,
                                                       List<UserClasses> inProgressClasses,
                                                       List<UserClasses> allUserClasses) {
        Map<String, Object> result = new HashMap<>();
        
        if (sbcRequirementsJson == null || sbcRequirementsJson.trim().isEmpty()) {
            result.put("status", "no_requirements");
            return result;
        }

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sbcReqs = (List<Map<String, Object>>) objectMapper.readValue(sbcRequirementsJson, List.class);
            
            List<Map<String, Object>> sbcStatus = new ArrayList<>();
            
            // Build set of all course codes (completed + in progress)
            Set<String> allCourseCodes = new HashSet<>();
            for (UserClasses uc : allUserClasses) {
                if (uc.getDepartment() != null && uc.getCourseNum() != null) {
                    allCourseCodes.add(uc.getDepartment() + " " + uc.getCourseNum());
                }
            }
            
            for (Map<String, Object> sbcReq : sbcReqs) {
                String category = (String) sbcReq.get("sbc");
                if (category == null) {
                    category = (String) sbcReq.get("category");
                }
                
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
                        
                        UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                        if (matchingClass != null) {
                            requiredSatisfied = true;
                            satisfiedRequiredCourses.add(code);
                            break; // Only need one
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
                        
                        UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                        if (matchingClass != null) {
                            satisfiedPartialRequired.add(code);
                        } else {
                            missingPartialRequired.add(code);
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
                
                // Check optional_major_courses_partial (can replace one required_major_courses)
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
                        
                        UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                        if (matchingClass != null) {
                            satisfiedOptionalPartial.add(code);
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
                
                // If no course requirements, SBC cannot be satisfied by major courses
                if (!hasAnyCourseRequirements) {
                    sbcMet = false;
                } else {
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
                    
                    // Both conditions must be met (if they exist)
                    sbcMet = requiredConditionMet && partialConditionMet;
                }
                
                status.put("met", sbcMet);
                status.put("satisfiedRequiredCourses", satisfiedRequiredCourses);
                status.put("satisfiedPartialRequired", satisfiedPartialRequired);
                status.put("missingPartialRequired", missingPartialRequired);
                status.put("satisfiedOptionalPartial", satisfiedOptionalPartial);
                
                sbcStatus.add(status);
            }
            
            result.put("sbcCategories", sbcStatus);
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", "Failed to parse SBC requirements: " + e.getMessage());
        }

        return result;
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
            
            // Determine status: satisfied, pending, or unsatisfied
            // Check for pending courses (grade is null) and unsatisfied courses (grade exists but below min_grade)
            String status = determineGroupStatus(group, type, satisfied, completedClasses, inProgressClasses, satisfiedCourses, usedClassIds);
            
            groupStatus.put("status", status);
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
     * Determine group status: SATISFIED, PENDING, or NOT_SATISFIED
     * - SATISFIED: all required courses have grade >= min_grade
     * - PENDING: at least one course has null grade (in-progress)
     * - NOT_SATISFIED: courses missing or grades below min_grade
     */
    private String determineGroupStatus(Map<String, Object> group, String type,
                                         boolean satisfied,
                                         List<UserClasses> completedClasses,
                                         List<UserClasses> inProgressClasses,
                                         List<Map<String, Object>> satisfiedCourses,
                                         Set<String> usedClassIds) {
        if (satisfied) {
            return "SATISFIED";
        }
        
        // First check if there are courses with low grades (unsatisfied, not pending)
        boolean hasLowGrade = checkIfHasLowGrade(group, type, completedClasses);
        if (hasLowGrade) {
            return "NOT_SATISFIED"; // Course exists but grade is too low - this is NOT_SATISFIED, not PENDING
        }
        
        // Check if there are pending courses (grade is null)
        boolean hasPending = checkIfPending(group, type, completedClasses, inProgressClasses, usedClassIds);
        return hasPending ? "PENDING" : "NOT_SATISFIED";
    }
    
    /**
     * Check if group has courses that are completed but with grades below minimum requirement.
     * This distinguishes between:
     * - Course not taken (could be pending)
     * - Course taken but grade too low (NOT_SATISFIED, not pending)
     */
    private boolean checkIfHasLowGrade(Map<String, Object> group, String type,
                                       List<UserClasses> completedClasses) {
        try {
            switch (type) {
                case "all_of":
                    return checkAllOfHasLowGrade(group, completedClasses);
                case "one_of":
                    return checkOneOfHasLowGrade(group, completedClasses);
                case "one_of_sequences":
                    return checkOneOfSequencesHasLowGrade(group, completedClasses);
                case "two_of":
                    return checkTwoOfHasLowGrade(group, completedClasses);
                case "electives":
                    // For electives, low grade courses are already filtered out in checkElectivesGroup
                    return false;
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if all_of group has any course with low grade.
     */
    private boolean checkAllOfHasLowGrade(Map<String, Object> group, List<UserClasses> completedClasses) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return false;
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
            
            if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                return true; // Found at least one course with low grade
            }
        }
        
        return false;
    }
    
    /**
     * Check if one_of group has any course with low grade.
     */
    private boolean checkOneOfHasLowGrade(Map<String, Object> group, List<UserClasses> completedClasses) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return false;
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
            
            if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                return true; // Found at least one course with low grade
            }
        }
        
        return false;
    }
    
    /**
     * Check if one_of_sequences group has any course with low grade.
     */
    private boolean checkOneOfSequencesHasLowGrade(Map<String, Object> group, List<UserClasses> completedClasses) {
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
        if (sequences == null || sequences.isEmpty()) {
            return false;
        }
        
        for (List<Map<String, Object>> sequence : sequences) {
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
                
                if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                    return true; // Found at least one course with low grade
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if two_of group has any course with low grade.
     */
    private boolean checkTwoOfHasLowGrade(Map<String, Object> group, List<UserClasses> completedClasses) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return false;
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
            
            if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                return true; // Found at least one course with low grade
            }
        }
        
        return false;
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
            
            // Get min_grade from course requirement, default to "C" if not specified
            String minGrade = (String) courseReq.get("min_grade");
            if (minGrade == null || minGrade.trim().isEmpty()) {
                minGrade = "C"; // Default minimum grade
            }
            
            String[] parts = parseCourseCode(code);
            String dept = parts[0];
            String courseNumStr = parts[1];
            
            // Check if course is completed with sufficient grade
            UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
            if (matchingClass != null) {
                satisfiedCourses.add(buildCourseInfo(matchingClass, code));
            } else {
                // Check if course exists but with low grade - don't add to satisfiedCourses
                if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                    // Course exists but grade is too low - don't add to satisfiedCourses
                    allFound = false;
                } else {
                    // Course not found at all
                    allFound = false;
                }
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
            
            // Get min_grade from course requirement, default to "C" if not specified
            String minGrade = (String) courseReq.get("min_grade");
            if (minGrade == null || minGrade.trim().isEmpty()) {
                minGrade = "C"; // Default minimum grade
            }
            
            String[] parts = parseCourseCode(code);
            String dept = parts[0];
            String courseNumStr = parts[1];
            
            UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
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
                
                // Get min_grade from course requirement, default to "C" if not specified
                String minGrade = (String) courseReq.get("min_grade");
                if (minGrade == null || minGrade.trim().isEmpty()) {
                    minGrade = "C"; // Default minimum grade
                }
                
                String[] parts = parseCourseCode(code);
                String dept = parts[0];
                String courseNumStr = parts[1];
                
                UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
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
                    
                    // Get min_grade from course requirement, default to "C" if not specified
                    String minGrade = (String) courseReq.get("min_grade");
                    if (minGrade == null || minGrade.trim().isEmpty()) {
                        minGrade = "C"; // Default minimum grade
                    }
                    
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    String courseNumStr = parts[1];
                    
                    UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                    if (matchingClass != null) {
                        // Check if already added (avoid duplicates)
                        boolean alreadyAdded = satisfiedCourses.stream()
                                .anyMatch(sc -> matchingClass.getClassId() != null && 
                                             matchingClass.getClassId().equals(sc.get("classId")));
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
     * If no group-level min_grade is specified, all courses must be C or above
     */
    private boolean checkElectivesGroup(Map<String, Object> group, List<UserClasses> completedClasses,
                                       List<UserClasses> inProgressClasses, List<UserClasses> allUserClasses,
                                       List<Map<String, Object>> satisfiedCourses,
                                       Set<String> usedClassIds) {
        Integer creditsRequired = (Integer) group.get("credits_required");
        if (creditsRequired == null || creditsRequired <= 0) {
            return false;
        }
        
        // Get group-level min_grade if specified
        String groupMinGrade = (String) group.get("min_grade");
        // If no group-level min_grade, default to "C" for all courses
        if (groupMinGrade == null || groupMinGrade.trim().isEmpty()) {
            groupMinGrade = "C";
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
                    
                    // Get min_grade from course requirement, use group-level min_grade if not specified
                    String minGrade = (String) courseReq.get("min_grade");
                    if (minGrade == null || minGrade.trim().isEmpty()) {
                        minGrade = groupMinGrade; // Use group-level min_grade
                    }
                    
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    String courseNumStr = parts[1];
                    
                    // For electives, check completed classes with min grade requirement
                    UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
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
                                // Verify grade meets group-level min_grade requirement
                                if (uc.getGrade() != null && isGradeSufficientForRequirement(uc.getGrade(), groupMinGrade)) {
                                    totalCredits += uc.getCredits();
                                    satisfiedCourses.add(buildCourseInfo(uc, uc.getDepartment() + " " + uc.getCourseNum()));
                                    usedClassIds.add(classIdStr);
                                }
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
                
                // Get min_grade from course requirement, use group-level min_grade if not specified
                String minGrade = (String) courseReq.get("min_grade");
                if (minGrade == null || minGrade.trim().isEmpty()) {
                    minGrade = groupMinGrade; // Use group-level min_grade
                }
                
                String[] parts = parseCourseCode(code);
                String dept = parts[0];
                String courseNumStr = parts[1];
                
                // For electives, check completed classes with min grade requirement
                UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
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
                
                // Get min_grade from course requirement, use group-level min_grade if not specified
                String minGrade = (String) courseReq.get("min_grade");
                if (minGrade == null || minGrade.trim().isEmpty()) {
                    minGrade = groupMinGrade; // Use group-level min_grade
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
                    
                    // Find matching upper-division courses with min grade requirement
                    // For pattern matching, use group-level min_grade since there's no specific course requirement
                    for (UserClasses uc : completedClasses) {
                        if (totalCredits >= creditsRequired) break;
                        String classIdStr = uc.getClassId() != null ? uc.getClassId().toString() : null;
                        if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) &&
                            uc.getCourseNum() != null && uc.getCourseNum() >= minLevel &&
                            classIdStr != null && !usedClassIds.contains(classIdStr) &&
                            uc.getGrade() != null && isGradeSufficientForRequirement(uc.getGrade(), groupMinGrade)) {
                            
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
                    
                    // For electives, check completed classes with min grade requirement
                    UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                    String classIdStr = matchingClass != null && matchingClass.getClassId() != null ? matchingClass.getClassId().toString() : null;
                    if (matchingClass != null && classIdStr != null && !usedClassIds.contains(classIdStr)) {
                        // Check excluded courses
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                        if (excludedCourses == null || !isExcluded(matchingClass, excludedCourses)) {
                            // Verify grade meets group-level min_grade requirement
                            if (matchingClass.getGrade() != null && isGradeSufficientForRequirement(matchingClass.getGrade(), groupMinGrade)) {
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
        }
        
        // Must have at least the required credits AND at least one satisfied course
        return totalCredits >= creditsRequired && !satisfiedCourses.isEmpty();
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
            
            // Get min_grade from course requirement, default to "C" if not specified
            String minGrade = (String) courseReq.get("min_grade");
            if (minGrade == null || minGrade.trim().isEmpty()) {
                minGrade = "C"; // Default minimum grade
            }
            
            String[] parts = parseCourseCode(code);
            String dept = parts[0];
            String courseNumStr = parts[1];
            
            UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
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
     * Check if course exists in completedClasses but with grade below minimum requirement.
     * This is used to distinguish between:
     * - Course not taken (missing)
     * - Course taken but grade too low (unsatisfied)
     * - Course in progress, grade is null (pending)
     */
    private boolean hasCompletedCourseWithLowGrade(List<UserClasses> completedClasses, 
                                                   String department, String courseNumStr, String minGrade) {
        if (department == null || courseNumStr == null || courseNumStr.isEmpty()) {
            return false;
        }
        
        Integer courseNum;
        try {
            courseNum = Integer.parseInt(courseNumStr);
        } catch (NumberFormatException e) {
            return false;
        }
        
        // Default to "C" if minGrade is not specified
        String requiredGrade = (minGrade == null || minGrade.trim().isEmpty()) ? "C" : minGrade.trim();
        
        return completedClasses.stream()
                .anyMatch(uc -> uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(department) &&
                               uc.getCourseNum() != null && uc.getCourseNum().equals(courseNum) &&
                               uc.getGrade() != null && 
                               !isGradeSufficientForRequirement(uc.getGrade(), requiredGrade));
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
                .filter(uc -> uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(department) &&
                             uc.getCourseNum() != null && uc.getCourseNum().equals(courseNum) &&
                             uc.getGrade() != null && isGradeSufficientForRequirement(uc.getGrade(), requiredGrade))
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
    
    /**
     * Check if requirement is pending (can be satisfied by in-progress classes).
     * A requirement is pending if it's not satisfied but has in-progress classes that could satisfy it.
     */
    private boolean checkIfPending(Map<String, Object> group, String type,
                                    List<UserClasses> completedClasses,
                                    List<UserClasses> inProgressClasses,
                                    Set<String> usedClassIds) {
        if (inProgressClasses == null || inProgressClasses.isEmpty()) {
            return false;
        }
        
        try {
            switch (type) {
                case "all_of":
                    return checkAllOfPending(group, completedClasses, inProgressClasses);
                case "one_of":
                    return checkOneOfPending(group, completedClasses, inProgressClasses);
                case "one_of_sequences":
                    return checkOneOfSequencesPending(group, completedClasses, inProgressClasses);
                case "two_of":
                    return checkTwoOfPending(group, completedClasses, inProgressClasses);
                case "electives":
                    return checkElectivesPending(group, completedClasses, inProgressClasses, usedClassIds);
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if all_of group is pending (all courses are completed or in-progress).
     */
    private boolean checkAllOfPending(Map<String, Object> group, 
                                       List<UserClasses> completedClasses,
                                       List<UserClasses> inProgressClasses) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return false;
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
            UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
            if (completed != null) {
                continue; // This course is satisfied
            }
            
            // Check if course exists in completedClasses but with low grade (unsatisfied, not pending)
            if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                return false; // Course is completed but grade is too low - this is NOT_SATISFIED, not PENDING
            }
            
            // Check if in-progress (grade is null)
            UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
            if (inProgress == null) {
                return false; // One course is neither completed nor in-progress
            }
        }
        
        return true; // All courses are either completed or in-progress
    }
    
    /**
     * Check if one_of group is pending (at least one course is in-progress).
     */
    private boolean checkOneOfPending(Map<String, Object> group,
                                      List<UserClasses> completedClasses,
                                      List<UserClasses> inProgressClasses) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return false;
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
            UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
            if (completed != null) {
                return false; // Already satisfied
            }
            
            // Check if course exists in completedClasses but with low grade (unsatisfied, not pending)
            if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                return false; // Course is completed but grade is too low - this is NOT_SATISFIED, not PENDING
            }
            
            // Check if in-progress (grade is null)
            UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
            if (inProgress != null) {
                return true; // At least one is in-progress
            }
        }
        
        return false; // No courses are in-progress
    }
    
    /**
     * Check if one_of_sequences group is pending.
     */
    private boolean checkOneOfSequencesPending(Map<String, Object> group,
                                                List<UserClasses> completedClasses,
                                                List<UserClasses> inProgressClasses) {
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
        if (sequences == null || sequences.isEmpty()) {
            return false;
        }
        
        for (List<Map<String, Object>> sequence : sequences) {
            boolean sequenceCanBeCompleted = true;
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
                
                // Check if completed with sufficient grade
                UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                if (completed != null) {
                    continue; // This course is satisfied
                }
                
                // Check if course exists in completedClasses but with low grade (unsatisfied, not pending)
                if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                    sequenceCanBeCompleted = false;
                    break; // Course is completed but grade is too low - this is NOT_SATISFIED, not PENDING
                }
                
                // Check if in-progress (grade is null)
                UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
                if (inProgress == null) {
                    sequenceCanBeCompleted = false;
                    break;
                }
            }
            
            if (sequenceCanBeCompleted) {
                return true; // At least one sequence can be completed
            }
        }
        
        return false;
    }
    
    /**
     * Check if two_of group is pending (at least two courses are completed or in-progress).
     */
    private boolean checkTwoOfPending(Map<String, Object> group,
                                      List<UserClasses> completedClasses,
                                      List<UserClasses> inProgressClasses) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return false;
        }
        
        int count = 0;
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
            UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
            if (completed != null) {
                count++;
                continue;
            }
            
            // Check if course exists in completedClasses but with low grade (unsatisfied, not pending)
            if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                // Don't count this - it's unsatisfied, not pending
                continue;
            }
            
            // Check if in-progress (grade is null)
            UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
            if (inProgress != null) {
                count++;
            }
        }
        
        return count >= 2; // At least two are completed or in-progress
    }
    
    /**
     * Check if electives group is pending (can reach required credits with in-progress classes).
     */
    private boolean checkElectivesPending(Map<String, Object> group,
                                          List<UserClasses> completedClasses,
                                          List<UserClasses> inProgressClasses,
                                          Set<String> usedClassIds) {
        Integer creditsRequired = (Integer) group.get("credits_required");
        if (creditsRequired == null) {
            return false;
        }
        
        // Calculate credits from completed classes (already done in checkElectivesGroup)
        // Here we just check if adding in-progress classes could reach the requirement
        int totalCredits = 0;
        
        // Count completed credits (simplified - would need to match the logic from checkElectivesGroup)
        // For simplicity, just check if there are enough in-progress classes that could satisfy
        if (inProgressClasses != null && !inProgressClasses.isEmpty()) {
            for (UserClasses uc : inProgressClasses) {
                if (uc.getCredits() != null && uc.getClassId() != null) {
                    String classIdStr = uc.getClassId().toString();
                    if (!usedClassIds.contains(classIdStr)) {
                        totalCredits += uc.getCredits();
                    }
                }
            }
        }
        
        // If we have some completed credits (from previous check) plus in-progress credits,
        // we might be able to reach the requirement
        // This is a simplified check - in reality, we'd need to match the exact elective logic
        return totalCredits > 0; // If there are in-progress classes, it could be pending
    }
    
    /**
     * Check if grade is sufficient (C or higher) for degree requirements.
     * C- is not sufficient (below C).
     */
    private boolean isGradeSufficient(String grade) {
        if (grade == null) return false;
        
        grade = grade.trim().toUpperCase();
        
        // Letter grades - C or higher
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
    
    /**
     * Check if a grade meets the minimum required grade.
     * @param actualGrade The grade the student received
     * @param requiredGrade The minimum required grade (e.g., "C", "B-", "B")
     * @return true if actualGrade >= requiredGrade
     */
    private boolean isGradeSufficientForRequirement(String actualGrade, String requiredGrade) {
        if (actualGrade == null || requiredGrade == null) {
            return false;
        }
        
        actualGrade = actualGrade.trim().toUpperCase();
        requiredGrade = requiredGrade.trim().toUpperCase();
        
        // Grade point values for comparison
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
        
        // If both are in the map, compare points
        if (actualPoints != null && requiredPoints != null) {
            return actualPoints >= requiredPoints;
        }
        
        // If not in map, try numeric comparison
        try {
            double actualNumeric = Double.parseDouble(actualGrade);
            double requiredNumeric = Double.parseDouble(requiredGrade);
            return actualNumeric >= requiredNumeric;
        } catch (NumberFormatException e) {
            // If can't parse, return false
            return false;
        }
    }
}


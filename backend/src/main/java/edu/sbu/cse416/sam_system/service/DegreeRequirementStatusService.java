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
import java.util.Optional;

/**
 * Service for checking student's degree requirement status.
 * Merged Version: Maintains Version 1's detailed logic/variable names while adding Version 2's Planner features.
 * TP: Used Cursor to implement degree requirement status checking service
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
    
    @Autowired
    private edu.sbu.cse416.sam_system.repository.CourseRepository courseRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Get degree requirement status for a student
     */
    public Map<String, Object> getDegreeRequirementsStatus(Long userId) {
        User student = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + userId));
        return computeDegreeRequirementStatus(student, Collections.emptyList());
    }

    /**
     * [Added from Version 2] Get degree requirement status including planned courses (planner forecast).
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

    /**
     * [Added from Version 2] Creates synthetic UserClasses from UserCoursePlan
     */
    private List<UserClasses> createSyntheticClasses(User student, List<UserCoursePlan> plannedCourses) {
        if (plannedCourses == null || plannedCourses.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserClasses> synthetic = new ArrayList<>();
        for (UserCoursePlan plan : plannedCourses) {
            UserClasses uc = new UserClasses();
            uc.setUser(student);
            uc.setClassId(null); // Planned courses have no ID
            uc.setDepartment(plan.getDepartment());
            uc.setCourseNum(plan.getCourseNum());
            uc.setSection(null);
            uc.setSemester(plan.getSemester());
            uc.setYear(plan.getYear());
            uc.setCredits(plan.getCredits());
            uc.setGpnc(null);
            // Assume all planned courses are completed with C grade for forecasting
            uc.setGrade("C"); 
            synthetic.add(uc);
        }
        return synthetic;
    }
    
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
            // Error parsing majors/minors, skip
        }
        
        return result;
    }
    
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
            List<DegreeRequirement> reqs = degreeRequirementRepository.findBySubjectCodeAndActive(subjectCode);
            if (!reqs.isEmpty()) {
                degreeTypeStr = reqs.get(0).getDegreeType().toString();
                status.put("degreeType", degreeTypeStr);
            }
        }
        
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
            
            Set<String> usedClassIds = new HashSet<>();
            List<Map<String, Object>> groupStatuses = new ArrayList<>();
            
            for (Map<String, Object> group : groups) {
                String type = (String) group.get("type");
                if (!"electives".equals(type)) {
                    Map<String, Object> groupStatus = checkGroupStatus(group, completedClasses, inProgressClasses, allUserClasses, usedClassIds);
                    groupStatuses.add(groupStatus);
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> satisfiedCourses = (List<Map<String, Object>>) groupStatus.get("satisfiedCourses");
                    if (satisfiedCourses != null) {
                        for (Map<String, Object> courseInfo : satisfiedCourses) {
                            Object classId = courseInfo.get("classId");
                            if (classId != null) {
                                usedClassIds.add(classId.toString());
                            } else {
                                // [Merge Fix] Handle planned courses (null ID)
                                usedClassIds.add(courseInfo.get("department") + "_" + courseInfo.get("courseNum") + "_planned");
                            }
                        }
                    }
                }
            }
            
            for (Map<String, Object> group : groups) {
                String type = (String) group.get("type");
                if ("electives".equals(type)) {
                    Map<String, Object> groupStatus = checkGroupStatus(group, completedClasses, inProgressClasses, allUserClasses, usedClassIds);
                    groupStatuses.add(groupStatus);
                }
            }
            
            status.put("groups", groupStatuses);
            
            long satisfiedGroups = groupStatuses.stream()
                    .filter(gs -> "SATISFIED".equals(gs.get("status")))
                    .count();
            long totalGroups = groupStatuses.size();
            
            status.put("satisfiedGroups", satisfiedGroups);
            status.put("totalGroups", totalGroups);
            status.put("overallStatus", satisfiedGroups == totalGroups ? "SATISFIED" : "IN_PROGRESS");
            status.put("progressPercentage", totalGroups > 0 ? (int) (satisfiedGroups * 100 / totalGroups) : 0);
            
            // Check SBC requirements (Kept from Version 1)
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
    
    // [Kept from Version 1] SBC logic is preserved, with status (SATISFIED/PENDING/NOT_SATISFIED) added
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
            
            for (Map<String, Object> sbcReq : sbcReqs) {
                String category = (String) sbcReq.get("sbc");
                if (category == null) {
                    category = (String) sbcReq.get("category");
                }
                
                Map<String, Object> status = new HashMap<>();
                status.put("category", category);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> requiredMajorCourses = (List<Map<String, Object>>) sbcReq.get("required_major_courses");
                boolean requiredSatisfied = false;
                boolean requiredPending = false;
                boolean requiredHasLowGrade = false;
                List<String> satisfiedRequiredCourses = new ArrayList<>();
                List<String> pendingRequiredCourses = new ArrayList<>();
                
                if (requiredMajorCourses != null && !requiredMajorCourses.isEmpty()) {
                    for (Map<String, Object> courseReq : requiredMajorCourses) {
                        String code = (String) courseReq.get("code");
                        if (code == null) continue;
                        
                        String minGrade = (String) courseReq.get("min_grade");
                        if (minGrade == null || minGrade.trim().isEmpty()) {
                            minGrade = "C";
                        }
                        
                        String[] parts = parseCourseCode(code);
                        String dept = parts[0];
                        String courseNumStr = parts[1];
                        
                        // First try to find in completed classes (with grade check)
                        UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                        
                        // If not found in completed classes, check allUserClasses (includes in-progress and planned courses)
                        if (matchingClass == null) {
                            matchingClass = findMatchingClass(allUserClasses, dept, courseNumStr);
                        }
                        
                        if (matchingClass != null) {
                            requiredSatisfied = true;
                            satisfiedRequiredCourses.add(code);
                            break;
                        }
                        
                        // Check for low grade
                        if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                            requiredHasLowGrade = true;
                        }
                        
                        // Check if in progress
                        UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
                        if (inProgress != null && !requiredHasLowGrade) {
                            requiredPending = true;
                            pendingRequiredCourses.add(code);
                        }
                    }
                }
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> requiredMajorCoursesPartial = (List<Map<String, Object>>) sbcReq.get("required_major_courses_partial");
                boolean partialRequiredSatisfied = false;
                boolean partialRequiredPending = false;
                boolean partialRequiredHasLowGrade = false;
                List<String> satisfiedPartialRequired = new ArrayList<>();
                List<String> missingPartialRequired = new ArrayList<>();
                List<String> pendingPartialRequired = new ArrayList<>();
                
                if (requiredMajorCoursesPartial != null && !requiredMajorCoursesPartial.isEmpty()) {
                    int totalPartialRequired = requiredMajorCoursesPartial.size();
                    int satisfiedCount = 0;
                    int pendingCount = 0;
                    
                    for (Map<String, Object> courseReq : requiredMajorCoursesPartial) {
                        String code = (String) courseReq.get("code");
                        if (code == null) continue;
                        
                        String minGrade = (String) courseReq.get("min_grade");
                        if (minGrade == null || minGrade.trim().isEmpty()) {
                            minGrade = "C";
                        }
                        
                        String[] parts = parseCourseCode(code);
                        String dept = parts[0];
                        String courseNumStr = parts[1];
                        
                        // First try to find in completed classes (with grade check)
                        UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                        
                        // If not found in completed classes, check allUserClasses (includes in-progress and planned courses)
                        if (matchingClass == null) {
                            matchingClass = findMatchingClass(allUserClasses, dept, courseNumStr);
                        }
                        
                        if (matchingClass != null) {
                            satisfiedPartialRequired.add(code);
                            satisfiedCount++;
                        } else {
                            missingPartialRequired.add(code);
                            
                            // Check for low grade
                            if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                                partialRequiredHasLowGrade = true;
                            }
                            
                            // Check if in progress
                            UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
                            if (inProgress != null && !partialRequiredHasLowGrade) {
                                pendingCount++;
                                pendingPartialRequired.add(code);
                            }
                        }
                    }
                    
                    if (totalPartialRequired == 2) {
                        partialRequiredSatisfied = satisfiedCount == 2;
                        partialRequiredPending = (satisfiedCount + pendingCount) >= 2 && !partialRequiredHasLowGrade;
                    } else {
                        partialRequiredSatisfied = satisfiedCount >= 2;
                        partialRequiredPending = (satisfiedCount + pendingCount) >= 2 && !partialRequiredHasLowGrade;
                    }
                } else {
                    partialRequiredSatisfied = true;
                }
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> optionalMajorCoursesPartial = (List<Map<String, Object>>) sbcReq.get("optional_major_courses_partial");
                List<String> satisfiedOptionalPartial = new ArrayList<>();
                List<String> pendingOptionalPartial = new ArrayList<>();
                boolean optionalPending = false;
                
                if (optionalMajorCoursesPartial != null && !optionalMajorCoursesPartial.isEmpty()) {
                    for (Map<String, Object> courseReq : optionalMajorCoursesPartial) {
                        String code = (String) courseReq.get("code");
                        if (code == null) continue;
                        
                        String minGrade = (String) courseReq.get("min_grade");
                        if (minGrade == null || minGrade.trim().isEmpty()) {
                            minGrade = "C";
                        }
                        
                        String[] parts = parseCourseCode(code);
                        String dept = parts[0];
                        String courseNumStr = parts[1];
                        
                        // First try to find in completed classes (with grade check)
                        UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                        
                        // If not found in completed classes, check allUserClasses (includes in-progress and planned courses)
                        if (matchingClass == null) {
                            matchingClass = findMatchingClass(allUserClasses, dept, courseNumStr);
                        }
                        
                        if (matchingClass != null) {
                            satisfiedOptionalPartial.add(code);
                        } else {
                            // Check if in progress
                            UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
                            if (inProgress != null) {
                                optionalPending = true;
                                pendingOptionalPartial.add(code);
                            }
                        }
                    }
                }
                
                boolean sbcMet = false;
                boolean sbcPending = false;
                boolean sbcHasLowGrade = false;
                
                boolean hasRequiredCourses = (requiredMajorCourses != null && !requiredMajorCourses.isEmpty());
                boolean hasPartialCourses = (requiredMajorCoursesPartial != null && !requiredMajorCoursesPartial.isEmpty());
                boolean hasOptionalPartial = (optionalMajorCoursesPartial != null && !optionalMajorCoursesPartial.isEmpty());
                boolean hasAnyCourseRequirements = hasRequiredCourses || hasPartialCourses || hasOptionalPartial;
                
                // If no major course requirements, check general courses' SBC information
                List<String> pendingGeneralCourses = new ArrayList<>();
                if (!hasAnyCourseRequirements) {
                    // Check if any completed course satisfies this SBC category
                    boolean generalCourseSatisfied = false;
                    boolean generalCoursePending = false;
                    
                    for (UserClasses uc : completedClasses) {
                        if (uc.getDepartment() != null && uc.getCourseNum() != null && uc.getGrade() != null) {
                            // Find the course in scraped courses
                            Optional<edu.sbu.cse416.sam_system.entity.Course> courseOpt = 
                                courseRepository.findBySubjectAndCourseNumber(uc.getDepartment(), uc.getCourseNum().toString());
                            
                            if (courseOpt.isPresent()) {
                                edu.sbu.cse416.sam_system.entity.Course course = courseOpt.get();
                                String sbcs = course.getSbcsSatisfied();
                                
                                if (sbcs != null && !sbcs.trim().isEmpty()) {
                                    // Check if this SBC category is in the course's SBCs
                                    // SBC format can be like "ARTS", "ARTS, GLO", "Partially fulfills: ARTS", etc.
                                    String sbcsUpper = sbcs.toUpperCase();
                                    String categoryUpper = category.toUpperCase();
                                    
                                    if (sbcsUpper.contains(categoryUpper)) {
                                        // Check if grade is sufficient (C or higher)
                                        if (isGradeSufficientForRequirement(uc.getGrade(), "C")) {
                                            generalCourseSatisfied = true;
                                            satisfiedRequiredCourses.add(uc.getDepartment() + " " + uc.getCourseNum());
                                            break;
                                        } else {
                                            generalCourseSatisfied = false;
                                            sbcHasLowGrade = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Check in-progress courses
                    if (!generalCourseSatisfied && !sbcHasLowGrade) {
                        for (UserClasses uc : inProgressClasses) {
                            if (uc.getDepartment() != null && uc.getCourseNum() != null) {
                                Optional<edu.sbu.cse416.sam_system.entity.Course> courseOpt = 
                                    courseRepository.findBySubjectAndCourseNumber(uc.getDepartment(), uc.getCourseNum().toString());
                                
                                if (courseOpt.isPresent()) {
                                    edu.sbu.cse416.sam_system.entity.Course course = courseOpt.get();
                                    String sbcs = course.getSbcsSatisfied();
                                    
                                    if (sbcs != null && !sbcs.trim().isEmpty()) {
                                        String sbcsUpper = sbcs.toUpperCase();
                                        String categoryUpper = category.toUpperCase();
                                        
                                        if (sbcsUpper.contains(categoryUpper)) {
                                            generalCoursePending = true;
                                            pendingGeneralCourses.add(uc.getDepartment() + " " + uc.getCourseNum());
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    sbcMet = generalCourseSatisfied;
                    sbcPending = generalCoursePending;
                } else {
                    boolean requiredConditionMet = true;
                    boolean requiredConditionPending = false;
                    if (hasRequiredCourses) {
                        requiredConditionMet = requiredSatisfied || !satisfiedOptionalPartial.isEmpty();
                        requiredConditionPending = requiredPending || (optionalPending && !requiredSatisfied && satisfiedOptionalPartial.isEmpty());
                        sbcHasLowGrade = requiredHasLowGrade;
                    }
                    
                    boolean partialConditionMet = true;
                    boolean partialConditionPending = false;
                    if (hasPartialCourses) {
                        partialConditionMet = partialRequiredSatisfied;
                        partialConditionPending = partialRequiredPending;
                        sbcHasLowGrade = sbcHasLowGrade || partialRequiredHasLowGrade;
                    }
                    
                    sbcMet = requiredConditionMet && partialConditionMet;
                    sbcPending = (requiredConditionPending || partialConditionPending) && !sbcMet && !sbcHasLowGrade;
                }
                
                // Determine status
                String sbcStatusStr;
                if (sbcMet) {
                    sbcStatusStr = "SATISFIED";
                } else if (sbcHasLowGrade) {
                    sbcStatusStr = "NOT_SATISFIED";
                } else if (sbcPending) {
                    sbcStatusStr = "PENDING";
                } else {
                    sbcStatusStr = "NOT_SATISFIED";
                }
                
                status.put("status", sbcStatusStr);
                status.put("met", sbcMet);
                status.put("satisfiedRequiredCourses", satisfiedRequiredCourses);
                status.put("satisfiedPartialRequired", satisfiedPartialRequired);
                status.put("missingPartialRequired", missingPartialRequired);
                status.put("satisfiedOptionalPartial", satisfiedOptionalPartial);
                
                // Add pending courses info
                List<String> allPendingCourses = new ArrayList<>();
                allPendingCourses.addAll(pendingRequiredCourses);
                allPendingCourses.addAll(pendingPartialRequired);
                allPendingCourses.addAll(pendingOptionalPartial);
                allPendingCourses.addAll(pendingGeneralCourses);
                if (!allPendingCourses.isEmpty()) {
                    status.put("pendingCourses", allPendingCourses);
                }
                
                sbcStatus.add(status);
            }
            
            result.put("sbcCategories", sbcStatus);
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", "Failed to parse SBC requirements: " + e.getMessage());
        }

        return result;
    }
    
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
                    satisfied = checkAllOfGroup(group, completedClasses, allUserClasses, satisfiedCourses);
                    break;
                case "one_of":
                    satisfied = checkOneOfGroup(group, completedClasses, allUserClasses, satisfiedCourses);
                    break;
                case "one_of_sequences":
                    satisfied = checkOneOfSequencesGroup(group, completedClasses, allUserClasses, satisfiedCourses, usedClassIds);
                    break;
                case "electives":
                    satisfied = checkElectivesGroup(group, completedClasses, inProgressClasses, allUserClasses, satisfiedCourses, usedClassIds);
                    break;
                case "two_of":
                    satisfied = checkTwoOfGroup(group, completedClasses, allUserClasses, satisfiedCourses);
                    break;
                default:
                    groupStatus.put("status", "UNKNOWN_TYPE");
                    groupStatus.put("message", "Unknown group type: " + type);
                    return groupStatus;
            }
            
            // Check for pending courses (Version 1 Logic)
            String status = determineGroupStatus(group, type, satisfied, completedClasses, inProgressClasses, satisfiedCourses, usedClassIds);
            
            groupStatus.put("status", status);
            groupStatus.put("satisfiedCourses", satisfiedCourses);
            
            // Add pending courses info if status is PENDING
            if ("PENDING".equals(status)) {
                List<Map<String, Object>> pendingCourses = findPendingCourses(group, type, completedClasses, inProgressClasses, usedClassIds);
                if (!pendingCourses.isEmpty()) {
                    groupStatus.put("pendingCourses", pendingCourses);
                }
            }
            
            // [Added from Version 2] Add required courses info if not satisfied
            if (!satisfied && !status.equals("SATISFIED")) {
                List<String> requiredCourses = extractRequiredCourses(group, type, satisfiedCourses);
                if (!requiredCourses.isEmpty()) {
                    groupStatus.put("requiredCourses", requiredCourses);
                }
            }
            
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
    
    // [Added from Version 2] Extract required courses for NOT_SATISFIED groups
    @SuppressWarnings("unchecked")
    private List<String> extractRequiredCourses(Map<String, Object> group, String type, 
                                                List<Map<String, Object>> satisfiedCourses) {
        List<String> requiredCourses = new ArrayList<>();
        
        Set<String> satisfiedCodes = satisfiedCourses.stream()
                .map(sc -> (String) sc.get("requirementCode"))
                .filter(code -> code != null)
                .collect(Collectors.toSet());
        
        switch (type) {
            case "all_of": {
                List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
                if (courses != null) {
                    for (Map<String, Object> courseReq : courses) {
                        String code = (String) courseReq.get("code");
                        if (code != null && !satisfiedCodes.contains(code)) {
                            requiredCourses.add(code);
                        }
                    }
                }
                break;
            }
            case "one_of": {
                List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
                if (courses != null) {
                    for (Map<String, Object> courseReq : courses) {
                        String code = (String) courseReq.get("code");
                        if (code != null) {
                            requiredCourses.add(code);
                        }
                    }
                }
                break;
            }
            case "two_of": {
                List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
                if (courses != null) {
                    for (Map<String, Object> courseReq : courses) {
                        String code = (String) courseReq.get("code");
                        if (code != null && !satisfiedCodes.contains(code)) {
                            requiredCourses.add(code);
                        }
                    }
                }
                break;
            }
            case "one_of_sequences": {
                List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
                if (sequences != null) {
                    for (List<Map<String, Object>> sequence : sequences) {
                        List<String> sequenceCodes = new ArrayList<>();
                        for (Map<String, Object> courseReq : sequence) {
                            String code = (String) courseReq.get("code");
                            if (code != null) {
                                sequenceCodes.add(code);
                            }
                        }
                        if (!sequenceCodes.isEmpty()) {
                            requiredCourses.add(String.join(", ", sequenceCodes));
                        }
                    }
                }
                break;
            }
            case "electives": {
                List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
                List<Map<String, Object>> additionalCourses = (List<Map<String, Object>>) group.get("additional_courses");
                 
                if (sequences != null && !sequences.isEmpty()) {
                    for (List<Map<String, Object>> sequence : sequences) {
                        List<String> sequenceCodes = new ArrayList<>();
                        for (Map<String, Object> courseReq : sequence) {
                            String code = (String) courseReq.get("code");
                            if (code != null) {
                                sequenceCodes.add(code);
                            }
                        }
                        if (!sequenceCodes.isEmpty()) {
                            requiredCourses.add("Sequence: " + String.join(", ", sequenceCodes));
                        }
                    }
                }
                
                if (additionalCourses != null && !additionalCourses.isEmpty()) {
                    for (Map<String, Object> courseReq : additionalCourses) {
                        String code = (String) courseReq.get("code");
                        if (code != null) {
                            requiredCourses.add(code);
                        }
                    }
                }
                break;
            }
        }
        
        return requiredCourses;
    }
    
    private String determineGroupStatus(Map<String, Object> group, String type,
                                         boolean satisfied,
                                         List<UserClasses> completedClasses,
                                         List<UserClasses> inProgressClasses,
                                         List<Map<String, Object>> satisfiedCourses,
                                         Set<String> usedClassIds) {
        if (satisfied) {
            return "SATISFIED";
        }
        
        boolean hasLowGrade = checkIfHasLowGrade(group, type, completedClasses);
        if (hasLowGrade) {
            return "NOT_SATISFIED";
        }
        
        boolean hasPending = checkIfPending(group, type, completedClasses, inProgressClasses, usedClassIds);
        return hasPending ? "PENDING" : "NOT_SATISFIED";
    }
    
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
                    return false;
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
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
                return true;
            }
        }
        
        return false;
    }
    
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
                return true;
            }
        }
        
        return false;
    }
    
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
                    return true;
                }
            }
        }
        
        return false;
    }
    
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
                return true;
            }
        }
        
        return false;
    }
    
    private boolean checkAllOfGroup(Map<String, Object> group, List<UserClasses> completedClasses, 
                                    List<UserClasses> allUserClasses,
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
            
            String minGrade = (String) courseReq.get("min_grade");
            if (minGrade == null || minGrade.trim().isEmpty()) {
                minGrade = "C";
            }
            
            String[] parts = parseCourseCode(code);
            String dept = parts[0];
            String courseNumStr = parts[1];
            
            // First try to find in completed classes (with grade check)
            UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
            
            // If not found in completed classes, check allUserClasses (includes planned courses)
            if (matchingClass == null) {
                matchingClass = findMatchingClass(allUserClasses, dept, courseNumStr);
            }
            
            if (matchingClass != null) {
                satisfiedCourses.add(buildCourseInfo(matchingClass, code));
            } else {
                if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                    allFound = false;
                } else {
                    allFound = false;
                }
            }
        }
        
        return allFound && satisfiedCourses.size() == courses.size();
    }
    
    private boolean checkOneOfGroup(Map<String, Object> group, List<UserClasses> completedClasses,
                                    List<UserClasses> allUserClasses,
                                    List<Map<String, Object>> satisfiedCourses) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses == null || courses.isEmpty()) {
            return true;
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
            
            // First try to find in completed classes (with grade check)
            UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
            
            // If not found in completed classes, check allUserClasses (includes planned courses)
            if (matchingClass == null) {
                matchingClass = findMatchingClass(allUserClasses, dept, courseNumStr);
            }
            
            if (matchingClass != null) {
                satisfiedCourses.add(buildCourseInfo(matchingClass, code));
                return true;
            }
        }
        
        return false;
    }
    
    private boolean checkOneOfSequencesGroup(Map<String, Object> group, List<UserClasses> completedClasses,
                                             List<UserClasses> allUserClasses,
                                             List<Map<String, Object>> satisfiedCourses,
                                             Set<String> usedClassIds) {
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
        if (sequences == null || sequences.isEmpty()) {
            return true;
        }
        
        boolean foundCompleteSequence = false;
        
        for (List<Map<String, Object>> sequence : sequences) {
            boolean sequenceComplete = true;
            List<Map<String, Object>> sequenceCourses = new ArrayList<>();
            
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
                
                // First try to find in completed classes (with grade check)
                UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                
                // If not found in completed classes, check allUserClasses (includes in-progress and planned courses)
                if (matchingClass == null) {
                    matchingClass = findMatchingClass(allUserClasses, dept, courseNumStr);
                }
                
                // [Merge Fix] Handle potential null classId (for planned courses)
                String classIdStr = matchingClass != null && matchingClass.getClassId() != null ? matchingClass.getClassId().toString() : null;
                // Create unique ID for planned courses
                String uniqueId = classIdStr != null ? classIdStr : (matchingClass != null ? matchingClass.getDepartment() + "_" + matchingClass.getCourseNum() + "_planned" : null);

                if (matchingClass != null && uniqueId != null && !usedClassIds.contains(uniqueId)) {
                    sequenceCourses.add(buildCourseInfo(matchingClass, code));
                } else {
                    sequenceComplete = false;
                    break;
                }
            }
            
            if (sequenceComplete && sequenceCourses.size() == sequence.size()) {
                satisfiedCourses.addAll(sequenceCourses);
                foundCompleteSequence = true;
                break;
            }
        }
        
        if (!foundCompleteSequence) {
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
                    
                    UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                    if (matchingClass != null) {
                        // [Merge Fix] Check duplicates using safe ID logic
                        String matchClassId = matchingClass.getClassId() != null ? matchingClass.getClassId().toString() : null;
                        String matchUniqueId = matchClassId != null ? matchClassId : (matchingClass.getDepartment() + "_" + matchingClass.getCourseNum() + "_planned");
                        
                        boolean alreadyAdded = satisfiedCourses.stream()
                                .anyMatch(sc -> {
                                    Object scClassId = sc.get("classId");
                                    String scUniqueId = scClassId != null ? scClassId.toString() : (sc.get("department") + "_" + sc.get("courseNum") + "_planned");
                                    return matchUniqueId.equals(scUniqueId);
                                });
                        if (!alreadyAdded) {
                            satisfiedCourses.add(buildCourseInfo(matchingClass, code));
                        }
                    }
                }
            }
        }
        
        return foundCompleteSequence;
    }
    
    private boolean checkElectivesGroup(Map<String, Object> group, List<UserClasses> completedClasses,
                                       List<UserClasses> inProgressClasses, List<UserClasses> allUserClasses,
                                       List<Map<String, Object>> satisfiedCourses,
                                       Set<String> usedClassIds) {
        Integer creditsRequired = (Integer) group.get("credits_required");
        if (creditsRequired == null || creditsRequired <= 0) {
            return false;
        }
        
        String groupMinGrade = (String) group.get("min_grade");
        if (groupMinGrade == null || groupMinGrade.trim().isEmpty()) {
            groupMinGrade = "C";
        }
        
        int totalCredits = 0;
        
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
        if (sequences != null && !sequences.isEmpty()) {
            // First pass: Identify all complete sequences and their courses
            Set<String> allSatisfiedSequenceCourses = new HashSet<>();
            List<List<UserClasses>> allCompleteSequences = new ArrayList<>();
            
            for (List<Map<String, Object>> sequence : sequences) {
                boolean sequenceComplete = true;
                List<UserClasses> sequenceMatches = new ArrayList<>();
                
                for (Map<String, Object> courseReq : sequence) {
                    String code = (String) courseReq.get("code");
                    if (code == null) continue;
                    
                    String minGrade = (String) courseReq.get("min_grade");
                    if (minGrade == null || minGrade.trim().isEmpty()) {
                        minGrade = groupMinGrade;
                    }
                    
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    String courseNumStr = parts[1];
                    
                    // First try to find in completed classes (with grade check)
                    UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                    
                    // If not found in completed classes, check allUserClasses (includes in-progress and planned courses)
                    if (matchingClass == null) {
                        matchingClass = findMatchingClass(allUserClasses, dept, courseNumStr);
                    }
                    
                    if (matchingClass != null) {
                        // [Merge Fix] Safe ID handling
                        String classIdStr = matchingClass.getClassId() != null ? matchingClass.getClassId().toString() : null;
                        String uniqueId = classIdStr != null ? classIdStr : (matchingClass.getDepartment() + "_" + matchingClass.getCourseNum() + "_planned");
                        
                        if (uniqueId != null) {
                            sequenceMatches.add(matchingClass);
                        } else {
                            sequenceComplete = false;
                            break;
                        }
                    } else {
                        sequenceComplete = false;
                        break;
                    }
                }
                
                if (sequenceComplete && !sequenceMatches.isEmpty()) {
                    allCompleteSequences.add(sequenceMatches);
                    // Add all courses from this sequence to the set
                    for (UserClasses uc : sequenceMatches) {
                        String classIdStr = uc.getClassId() != null ? uc.getClassId().toString() : null;
                        String uniqueId = classIdStr != null ? classIdStr : (uc.getDepartment() + "_" + uc.getCourseNum() + "_planned");
                        if (uniqueId != null) {
                            allSatisfiedSequenceCourses.add(uniqueId);
                        }
                    }
                }
            }
            
            // Second pass: Aggregate credits from all courses in identified complete sequences
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
            
            for (String uniqueId : allSatisfiedSequenceCourses) {
                if (usedClassIds.contains(uniqueId)) continue;
                
                // Find the UserClasses object for this uniqueId
                UserClasses matchingClass = null;
                for (List<UserClasses> seq : allCompleteSequences) {
                    for (UserClasses uc : seq) {
                        String classIdStr = uc.getClassId() != null ? uc.getClassId().toString() : null;
                        String ucUniqueId = classIdStr != null ? classIdStr : (uc.getDepartment() + "_" + uc.getCourseNum() + "_planned");
                        if (uniqueId.equals(ucUniqueId)) {
                            matchingClass = uc;
                            break;
                        }
                    }
                    if (matchingClass != null) break;
                }
                
                if (matchingClass != null) {
                    if (excludedCourses == null || !isExcluded(matchingClass, excludedCourses)) {
                        if (matchingClass.getGrade() != null && isGradeSufficientForRequirement(matchingClass.getGrade(), groupMinGrade)) {
                            if (matchingClass.getCredits() != null) {
                                totalCredits += matchingClass.getCredits();
                                satisfiedCourses.add(buildCourseInfo(matchingClass, matchingClass.getDepartment() + " " + matchingClass.getCourseNum()));
                                usedClassIds.add(uniqueId);
                            }
                        }
                    }
                }
            }
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> additionalCourses = (List<Map<String, Object>>) group.get("additional_courses");
        if (additionalCourses != null && !additionalCourses.isEmpty()) {
            for (Map<String, Object> courseReq : additionalCourses) {
                if (totalCredits >= creditsRequired) break;
                
                String code = (String) courseReq.get("code");
                if (code == null) continue;
                
                String minGrade = (String) courseReq.get("min_grade");
                if (minGrade == null || minGrade.trim().isEmpty()) {
                    minGrade = groupMinGrade;
                }
                
                String[] parts = parseCourseCode(code);
                String dept = parts[0];
                String courseNumStr = parts[1];
                
                UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                
                // [Merge Fix] Safe ID handling
                String classIdStr = matchingClass != null && matchingClass.getClassId() != null ? matchingClass.getClassId().toString() : null;
                String uniqueId = classIdStr != null ? classIdStr : (matchingClass != null ? matchingClass.getDepartment() + "_" + matchingClass.getCourseNum() + "_planned" : null);

                if (matchingClass != null && uniqueId != null && !usedClassIds.contains(uniqueId)) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                    if (excludedCourses == null || !isExcluded(matchingClass, excludedCourses)) {
                        if (matchingClass.getCredits() != null) {
                            totalCredits += matchingClass.getCredits();
                            satisfiedCourses.add(buildCourseInfo(matchingClass, code));
                            usedClassIds.add(uniqueId);
                        }
                    }
                }
            }
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses != null && !courses.isEmpty()) {
            for (Map<String, Object> courseReq : courses) {
                if (totalCredits >= creditsRequired) break;
                
                String code = (String) courseReq.get("code");
                if (code == null) continue;
                
                String minGrade = (String) courseReq.get("min_grade");
                if (minGrade == null || minGrade.trim().isEmpty()) {
                    minGrade = groupMinGrade;
                }
                
                if (code.contains("xx+") || code.contains("xx")) {
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    int minLevel = 300;
                    
                    if (code.contains("2xx+")) {
                        minLevel = 200;
                    } else if (code.contains("4xx+")) {
                        minLevel = 400;
                    }
                    
                    for (UserClasses uc : completedClasses) {
                        if (totalCredits >= creditsRequired) break;
                        
                        // [Merge Fix] Safe ID handling
                        String classIdStr = uc.getClassId() != null ? uc.getClassId().toString() : null;
                        String uniqueId = classIdStr != null ? classIdStr : (uc.getDepartment() + "_" + uc.getCourseNum() + "_planned");

                        if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) &&
                            uc.getCourseNum() != null && uc.getCourseNum() >= minLevel &&
                            uniqueId != null && !usedClassIds.contains(uniqueId) &&
                            uc.getGrade() != null && isGradeSufficientForRequirement(uc.getGrade(), groupMinGrade)) {
                            
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                            if (excludedCourses == null || !isExcluded(uc, excludedCourses)) {
                                if (uc.getCredits() != null) {
                                    totalCredits += uc.getCredits();
                                    satisfiedCourses.add(buildCourseInfo(uc, uc.getDepartment() + " " + uc.getCourseNum()));
                                    usedClassIds.add(uniqueId);
                                }
                            }
                        }
                    }
                } else {
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    String courseNumStr = parts[1];
                    
                    UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                    
                    // [Merge Fix] Safe ID handling
                    String classIdStr = matchingClass != null && matchingClass.getClassId() != null ? matchingClass.getClassId().toString() : null;
                    String uniqueId = classIdStr != null ? classIdStr : (matchingClass != null ? matchingClass.getDepartment() + "_" + matchingClass.getCourseNum() + "_planned" : null);

                    if (matchingClass != null && uniqueId != null && !usedClassIds.contains(uniqueId)) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                        if (excludedCourses == null || !isExcluded(matchingClass, excludedCourses)) {
                            if (matchingClass.getGrade() != null && isGradeSufficientForRequirement(matchingClass.getGrade(), groupMinGrade)) {
                                if (matchingClass.getCredits() != null) {
                                    totalCredits += matchingClass.getCredits();
                                    satisfiedCourses.add(buildCourseInfo(matchingClass, code));
                                    usedClassIds.add(uniqueId);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return totalCredits >= creditsRequired && !satisfiedCourses.isEmpty();
    }
    
    private boolean checkTwoOfGroup(Map<String, Object> group, List<UserClasses> completedClasses,
                                   List<UserClasses> allUserClasses,
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
            
            String minGrade = (String) courseReq.get("min_grade");
            if (minGrade == null || minGrade.trim().isEmpty()) {
                minGrade = "C";
            }
            
            String[] parts = parseCourseCode(code);
            String dept = parts[0];
            String courseNumStr = parts[1];
            
            // First try to find in completed classes (with grade check)
            UserClasses matchingClass = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
            
            // If not found in completed classes, check allUserClasses (includes planned courses)
            if (matchingClass == null) {
                matchingClass = findMatchingClass(allUserClasses, dept, courseNumStr);
            }
            
            if (matchingClass != null) {
                satisfiedCourses.add(buildCourseInfo(matchingClass, code));
                count++;
            }
        }
        
        return count >= 2;
    }
    
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
    
    private UserClasses findMatchingClass(List<UserClasses> classes, String department, String courseNumStr) {
        if (department == null || courseNumStr == null || courseNumStr.isEmpty()) {
            return null;
        }
        
        Integer courseNum;
        try {
            courseNum = Integer.parseInt(courseNumStr);
        } catch (NumberFormatException e) {
            return null;
        }
        
        return classes.stream()
                .filter(uc -> uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(department) &&
                             uc.getCourseNum() != null && uc.getCourseNum().equals(courseNum))
                .findFirst()
                .orElse(null);
    }
    
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
        
        String requiredGrade = (minGrade == null || minGrade.trim().isEmpty()) ? "C" : minGrade.trim();
        
        return completedClasses.stream()
                .anyMatch(uc -> uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(department) &&
                               uc.getCourseNum() != null && uc.getCourseNum().equals(courseNum) &&
                               uc.getGrade() != null && 
                               !isGradeSufficientForRequirement(uc.getGrade(), requiredGrade));
    }
    
    private UserClasses findMatchingClassWithMinGrade(List<UserClasses> classes, String department, 
                                                       String courseNumStr, String minGrade) {
        if (department == null || courseNumStr == null || courseNumStr.isEmpty()) {
            return null;
        }
        
        Integer courseNum;
        try {
            courseNum = Integer.parseInt(courseNumStr);
        } catch (NumberFormatException e) {
            return null;
        }
        
        String requiredGrade = (minGrade == null || minGrade.trim().isEmpty()) ? "C" : minGrade.trim();
        
        return classes.stream()
                .filter(uc -> uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(department) &&
                             uc.getCourseNum() != null && uc.getCourseNum().equals(courseNum) &&
                             uc.getGrade() != null && isGradeSufficientForRequirement(uc.getGrade(), requiredGrade))
                .findFirst()
                .orElse(null);
    }
    
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
            
            UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
            if (completed != null) {
                continue;
            }
            
            if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                return false;
            }
            
            UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
            if (inProgress == null) {
                return false;
            }
        }
        
        return true;
    }
    
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
            
            UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
            if (completed != null) {
                return false;
            }
            
            if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                return false;
            }
            
            UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
            if (inProgress != null) {
                return true;
            }
        }
        
        return false;
    }
    
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
                
                UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                if (completed != null) {
                    continue;
                }
                
                if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                    sequenceCanBeCompleted = false;
                    break;
                }
                
                UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
                if (inProgress == null) {
                    sequenceCanBeCompleted = false;
                    break;
                }
            }
            
            if (sequenceCanBeCompleted) {
                return true;
            }
        }
        
        return false;
    }
    
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
            
            UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
            if (completed != null) {
                count++;
                continue;
            }
            
            if (hasCompletedCourseWithLowGrade(completedClasses, dept, courseNumStr, minGrade)) {
                continue;
            }
            
            UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
            if (inProgress != null) {
                count++;
            }
        }
        
        return count >= 2;
    }
    
    private boolean checkElectivesPending(Map<String, Object> group,
                                          List<UserClasses> completedClasses,
                                          List<UserClasses> inProgressClasses,
                                          Set<String> usedClassIds) {
        Integer creditsRequired = (Integer) group.get("credits_required");
        if (creditsRequired == null) {
            return false;
        }
        
        String groupMinGrade = (String) group.get("min_grade");
        if (groupMinGrade == null || groupMinGrade.trim().isEmpty()) {
            groupMinGrade = "C";
        }
        
        int totalCredits = 0;
        
        if (inProgressClasses == null || inProgressClasses.isEmpty()) {
            return false;
        }
        
        // Check sequences (in-progress courses can partially satisfy sequences)
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
        if (sequences != null && !sequences.isEmpty()) {
            for (List<Map<String, Object>> sequence : sequences) {
                for (Map<String, Object> courseReq : sequence) {
                    String code = (String) courseReq.get("code");
                    if (code == null) continue;
                    
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    String courseNumStr = parts[1];
                    
                    // Check if already completed
                    UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, groupMinGrade);
                    if (completed != null) continue;
                    
                    // Check if in progress
                    UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
                    if (inProgress != null) {
                        String classIdStr = inProgress.getClassId() != null ? inProgress.getClassId().toString() : null;
                        String uniqueId = classIdStr != null ? classIdStr : (inProgress.getDepartment() + "_" + inProgress.getCourseNum() + "_planned");
                        if (uniqueId != null && !usedClassIds.contains(uniqueId)) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                            if (excludedCourses == null || !isExcluded(inProgress, excludedCourses)) {
                                if (inProgress.getCredits() != null) {
                                    totalCredits += inProgress.getCredits();
                                    return true; // Found at least one matching in-progress course
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Check additional courses
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> additionalCourses = (List<Map<String, Object>>) group.get("additional_courses");
        if (additionalCourses != null && !additionalCourses.isEmpty()) {
            for (Map<String, Object> courseReq : additionalCourses) {
                String code = (String) courseReq.get("code");
                if (code == null) continue;
                
                String[] parts = parseCourseCode(code);
                String dept = parts[0];
                String courseNumStr = parts[1];
                
                // Check if already completed
                UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, groupMinGrade);
                if (completed != null) continue;
                
                // Check if in progress
                UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
                if (inProgress != null) {
                    String classIdStr = inProgress.getClassId() != null ? inProgress.getClassId().toString() : null;
                    String uniqueId = classIdStr != null ? classIdStr : (inProgress.getDepartment() + "_" + inProgress.getCourseNum() + "_planned");
                    if (uniqueId != null && !usedClassIds.contains(uniqueId)) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                        if (excludedCourses == null || !isExcluded(inProgress, excludedCourses)) {
                            if (inProgress.getCredits() != null) {
                                totalCredits += inProgress.getCredits();
                                return true; // Found at least one matching in-progress course
                            }
                        }
                    }
                }
            }
        }
        
        // Check wildcard patterns (e.g., "CSE 3xx+")
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
        if (courses != null && !courses.isEmpty()) {
            for (Map<String, Object> courseReq : courses) {
                String code = (String) courseReq.get("code");
                if (code == null) continue;
                
                if (code.contains("xx+") || code.contains("xx")) {
                    String[] parts = parseCourseCode(code);
                    String dept = parts[0];
                    int minLevel = 300;
                    
                    if (code.contains("2xx+")) {
                        minLevel = 200;
                    } else if (code.contains("4xx+")) {
                        minLevel = 400;
                    }
                    
                    for (UserClasses uc : inProgressClasses) {
                        String classIdStr = uc.getClassId() != null ? uc.getClassId().toString() : null;
                        String uniqueId = classIdStr != null ? classIdStr : (uc.getDepartment() + "_" + uc.getCourseNum() + "_planned");
                        
                        if (uc.getDepartment() != null && uc.getDepartment().equalsIgnoreCase(dept) &&
                            uc.getCourseNum() != null && uc.getCourseNum() >= minLevel &&
                            uniqueId != null && !usedClassIds.contains(uniqueId)) {
                            
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                            if (excludedCourses == null || !isExcluded(uc, excludedCourses)) {
                                if (uc.getCredits() != null) {
                                    totalCredits += uc.getCredits();
                                    return true; // Found at least one matching in-progress course
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return false; // No matching in-progress courses found
    }
    
    private boolean isGradeSufficient(String grade) {
        if (grade == null) return false;
        
        grade = grade.trim().toUpperCase();
        
        if (grade.equals("A") || grade.equals("A+") || grade.equals("A-") ||
            grade.equals("B") || grade.equals("B+") || grade.equals("B-") ||
            grade.equals("C") || grade.equals("C+")) {
            return true;
        }
        
        if (grade.equals("C-")) {
            return false;
        }
        
        try {
            double numericGrade = Double.parseDouble(grade);
            return numericGrade >= 2.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Find pending courses (in-progress) that match the group requirements
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findPendingCourses(Map<String, Object> group, String type,
                                                         List<UserClasses> completedClasses,
                                                         List<UserClasses> inProgressClasses,
                                                         Set<String> usedClassIds) {
        List<Map<String, Object>> pendingCourses = new ArrayList<>();
        
        if (inProgressClasses == null || inProgressClasses.isEmpty()) {
            return pendingCourses;
        }
        
        try {
            switch (type) {
                case "all_of": {
                    List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
                    if (courses != null) {
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
                            
                            // Check if already completed
                            UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                            if (completed != null) continue;
                            
                            // Check if in progress
                            UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
                            if (inProgress != null) {
                                pendingCourses.add(buildCourseInfo(inProgress, code));
                            }
                        }
                    }
                    break;
                }
                case "one_of": {
                    List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
                    if (courses != null) {
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
                            
                            // Check if already completed
                            UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                            if (completed != null) continue;
                            
                            // Check if in progress
                            UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
                            if (inProgress != null) {
                                pendingCourses.add(buildCourseInfo(inProgress, code));
                            }
                        }
                    }
                    break;
                }
                case "one_of_sequences": {
                    List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
                    if (sequences != null) {
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
                                
                                // Check if already completed
                                UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                                if (completed != null) continue;
                                
                                // Check if in progress
                                UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
                                if (inProgress != null) {
                                    pendingCourses.add(buildCourseInfo(inProgress, code));
                                }
                            }
                        }
                    }
                    break;
                }
                case "two_of": {
                    List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
                    if (courses != null) {
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
                            
                            // Check if already completed
                            UserClasses completed = findMatchingClassWithMinGrade(completedClasses, dept, courseNumStr, minGrade);
                            if (completed != null) continue;
                            
                            // Check if in progress
                            UserClasses inProgress = findMatchingClass(inProgressClasses, dept, courseNumStr);
                            if (inProgress != null) {
                                pendingCourses.add(buildCourseInfo(inProgress, code));
                            }
                        }
                    }
                    break;
                }
                case "electives": {
                    // For electives, check all in-progress courses that match the group criteria
                    Integer creditsRequired = (Integer) group.get("credits_required");
                    String groupMinGrade = (String) group.get("min_grade");
                    if (groupMinGrade == null || groupMinGrade.trim().isEmpty()) {
                        groupMinGrade = "C";
                    }
                    
                    for (UserClasses uc : inProgressClasses) {
                        if (uc.getDepartment() == null || uc.getCourseNum() == null) continue;
                        
                        // Check if already used
                        String classIdStr = uc.getClassId() != null ? uc.getClassId().toString() : null;
                        String uniqueId = classIdStr != null ? classIdStr : (uc.getDepartment() + "_" + uc.getCourseNum() + "_planned");
                        if (usedClassIds.contains(uniqueId)) continue;
                        
                        // Check if excluded
                        List<Map<String, Object>> excludedCourses = (List<Map<String, Object>>) group.get("excluded_courses");
                        if (excludedCourses != null && isExcluded(uc, excludedCourses)) continue;
                        
                        // Check sequences
                        List<List<Map<String, Object>>> sequences = (List<List<Map<String, Object>>>) group.get("sequences");
                        boolean matchesSequence = false;
                        if (sequences != null) {
                            for (List<Map<String, Object>> sequence : sequences) {
                                for (Map<String, Object> courseReq : sequence) {
                                    String code = (String) courseReq.get("code");
                                    if (code == null) continue;
                                    
                                    String[] parts = parseCourseCode(code);
                                    String dept = parts[0];
                                    String courseNumStr = parts[1];
                                    
                                    if (uc.getDepartment().equalsIgnoreCase(dept) && 
                                        uc.getCourseNum().toString().equals(courseNumStr)) {
                                        matchesSequence = true;
                                        pendingCourses.add(buildCourseInfo(uc, code));
                                        break;
                                    }
                                }
                                if (matchesSequence) break;
                            }
                        }
                        
                        // Check additional courses
                        if (!matchesSequence) {
                            List<Map<String, Object>> additionalCourses = (List<Map<String, Object>>) group.get("additional_courses");
                            if (additionalCourses != null) {
                                for (Map<String, Object> courseReq : additionalCourses) {
                                    String code = (String) courseReq.get("code");
                                    if (code == null) continue;
                                    
                                    String[] parts = parseCourseCode(code);
                                    String dept = parts[0];
                                    String courseNumStr = parts[1];
                                    
                                    if (uc.getDepartment().equalsIgnoreCase(dept) && 
                                        uc.getCourseNum().toString().equals(courseNumStr)) {
                                        pendingCourses.add(buildCourseInfo(uc, code));
                                        break;
                                    }
                                }
                            }
                        }
                        
                        // Check wildcard patterns (e.g., "CSE 3xx+")
                        if (!matchesSequence) {
                            List<Map<String, Object>> courses = (List<Map<String, Object>>) group.get("courses");
                            if (courses != null) {
                                for (Map<String, Object> courseReq : courses) {
                                    String code = (String) courseReq.get("code");
                                    if (code == null) continue;
                                    
                                    if (code.contains("xx+") || code.contains("xx")) {
                                        String[] parts = parseCourseCode(code);
                                        String dept = parts[0];
                                        int minLevel = 300;
                                        
                                        if (code.contains("2xx+")) {
                                            minLevel = 200;
                                        } else if (code.contains("4xx+")) {
                                            minLevel = 400;
                                        }
                                        
                                        if (uc.getDepartment().equalsIgnoreCase(dept) && 
                                            uc.getCourseNum() != null && uc.getCourseNum() >= minLevel) {
                                            pendingCourses.add(buildCourseInfo(uc, uc.getDepartment() + " " + uc.getCourseNum()));
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // Ignore errors, return empty list
        }
        
        return pendingCourses;
    }
    
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

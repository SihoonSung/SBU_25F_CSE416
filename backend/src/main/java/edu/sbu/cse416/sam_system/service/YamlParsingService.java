package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.DegreeRequirement;
import edu.sbu.cse416.sam_system.entity.DegreeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for parsing YAML files containing degree requirements.
 * Handles complex YAML structure parsing and data validation.
 * 
 * Note: YAML parsing functionality was implemented 
 * with assistance from Cursor AI and ChatGPT for proper SnakeYAML integration 
 * and robust data structure handling.
 */
@Service
public class YamlParsingService {
    
    private final Yaml yaml = new Yaml();
    
    /**
     * Parse YAML file and create degree requirements
     */
    // TP: Used cursor to parse YAML file and create degree requirements
    public List<DegreeRequirement> parseAndCreateRequirements(MultipartFile file, String createdBy) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> yamlData = yaml.load(inputStream);
            
            List<DegreeRequirement> requirements = new ArrayList<>();
            
            // Parse all subjects from degree_requirements and from root level
            if (yamlData.containsKey("degree_requirements")) {
                Object degreeReqsObj = yamlData.get("degree_requirements");
                
                // New format: degree_requirements is a list of subjects
                if (degreeReqsObj instanceof List) {
                    List<Map<String, Object>> subjects = (List<Map<String, Object>>) degreeReqsObj;
                    for (Map<String, Object> subjectData : subjects) {
                        // Each subject has subject_code, is_active, and entries
                        if (subjectData.containsKey("entries") && subjectData.containsKey("subject_code")) {
                            String subjectCode = (String) subjectData.get("subject_code");
                            Object entriesObj = subjectData.get("entries");
                            
                            if (entriesObj instanceof List) {
                                List<Map<String, Object>> entries = (List<Map<String, Object>>) entriesObj;
                                
                                for (Map<String, Object> entryData : entries) {
                                    // Add subject_code to each entry if not present
                                    if (!entryData.containsKey("subject_code")) {
                                        entryData.put("subject_code", subjectCode);
                                    }
                                    
                                    DegreeRequirement requirement = parseDegreeRequirement(entryData, yamlData, createdBy);
                                    if (requirement != null) {
                                        requirements.add(requirement);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            return requirements;
        }
    }
    
    /**
     * Parse individual degree requirement from YAML data
     */
    // TP: Used cursor to parse individual degree requirement from YAML data
    private DegreeRequirement parseDegreeRequirement(Map<String, Object> reqData, Map<String, Object> yamlData, String createdBy) {
        try {
            // Extract basic information
            String subjectCode = (String) reqData.get("subject_code");
            String degreeTypeStr = (String) reqData.get("degree_type");
            
            // Try to get total_credits_required first, fallback to degree_credits_required if not present
            Integer totalCredits = (Integer) reqData.get("total_credits_required");
            if (totalCredits == null) {
                totalCredits = (Integer) reqData.get("degree_credits_required");
            }
            
            // Debug logging
            System.out.println("DEBUG: Parsing degree requirement for subject: " + subjectCode);
            System.out.println("DEBUG: degreeTypeStr = " + degreeTypeStr);
            System.out.println("DEBUG: totalCredits = " + totalCredits);
            Boolean isRestricted = (Boolean) reqData.getOrDefault("is_restricted", false);
            Double gpaRequirement = (Double) reqData.get("gpa_requirement");
            
            // Get version and term from top level or individual requirement
            String requirementsVersion = (String) yamlData.getOrDefault("requirements_version", "Unknown");
            String effectiveTerm = (String) yamlData.getOrDefault("effective_term", "Unknown");
            
            if (reqData.containsKey("requirements_version")) {
                requirementsVersion = (String) reqData.get("requirements_version");
            }
            if (reqData.containsKey("effective_term")) {
                effectiveTerm = (String) reqData.get("effective_term");
            }
            
            // Validate required fields
            if (subjectCode == null || degreeTypeStr == null || totalCredits == null) {
                throw new IllegalArgumentException("Missing required fields: subject_code, degree_type, or total_credits_required/degree_credits_required");
            }
            
            // Parse degree type
            DegreeType degreeType;
            try {
                if (degreeTypeStr == null || degreeTypeStr.trim().isEmpty()) {
                    throw new IllegalArgumentException("degree_type cannot be null or empty");
                }
                degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid degree type: " + degreeTypeStr + ". Must be 'BA', 'BS', or 'MINOR'");
            }
            
            // Create degree requirement
            DegreeRequirement requirement = new DegreeRequirement(
                    subjectCode, degreeType, requirementsVersion, effectiveTerm, totalCredits, createdBy);
            
            // Set optional fields
            requirement.setIsRestricted(isRestricted);
            requirement.setGpaRequirement(gpaRequirement);
            
            // Parse and set admission requirements (check both "admission" and "admission_requirements" for compatibility)
            if (reqData.containsKey("admission")) {
                Object admissionReqs = reqData.get("admission");
                String admissionReqsJson = convertToJson(admissionReqs);
                requirement.setAdmissionRequirements(admissionReqsJson);
            } else if (reqData.containsKey("admission_requirements")) {
                Object admissionReqs = reqData.get("admission_requirements");
                String admissionReqsJson = convertToJson(admissionReqs);
                requirement.setAdmissionRequirements(admissionReqsJson);
            }
            
            // Parse and set course requirements
            if (reqData.containsKey("course_requirements")) {
                Object courseReqs = reqData.get("course_requirements");
                String courseReqsJson = convertToJson(courseReqs);
                requirement.setCourseRequirements(courseReqsJson);
            }
            
            // Parse and set prerequisite courses (optional field)
            if (reqData.containsKey("prerequisite_courses")) {
                Object prereqCourses = reqData.get("prerequisite_courses");
                String prereqCoursesJson = convertToJson(prereqCourses);
                requirement.setPrerequisiteCourses(prereqCoursesJson);
            }
            
            // Parse and set SBC requirements (check both "sbc_categories" and "sbc_requirements" for compatibility)
            if (reqData.containsKey("sbc_categories")) {
                Object sbcReqs = reqData.get("sbc_categories");
                String sbcReqsJson = convertToJson(sbcReqs);
                requirement.setSbcRequirements(sbcReqsJson);
            } else if (reqData.containsKey("sbc_requirements")) {
                Object sbcReqs = reqData.get("sbc_requirements");
                String sbcReqsJson = convertToJson(sbcReqs);
                requirement.setSbcRequirements(sbcReqsJson);
            }
            
            return requirement;
            
        } catch (Exception e) {
            throw new IllegalArgumentException("Error parsing degree requirement: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert object to JSON string with proper formatting
     */
    // TP: Used cursor to convert object to JSON string with proper formatting
    private String convertToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        
        try {
            // Use Jackson ObjectMapper for proper JSON conversion
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            // Fallback to simple string representation
            return obj.toString();
        }
    }
    
    /**
     * Validate YAML file structure
     */
    // TP: Used cursor to validate top-level YAML structure
    public void validateYamlStructure(Map<String, Object> yamlData) {
        // Check for required top-level fields
        if (!yamlData.containsKey("requirements_version")) {
            throw new IllegalArgumentException("Missing required field: requirements_version");
        }
        
        if (!yamlData.containsKey("effective_term")) {
            throw new IllegalArgumentException("Missing required field: effective_term");
        }
        
        // Check for at least one requirements section
        if (!yamlData.containsKey("degree_requirements") && !yamlData.containsKey("minor_requirements")) {
            throw new IllegalArgumentException("Must contain either 'degree_requirements' or 'minor_requirements' section");
        }
        
        // Validate degree requirements structure
        if (yamlData.containsKey("degree_requirements")) {
            List<Map<String, Object>> degreeReqs = (List<Map<String, Object>>) yamlData.get("degree_requirements");
            validateRequirementsList(degreeReqs, "degree_requirements");
        }
        
        // Validate minor requirements structure
        if (yamlData.containsKey("minor_requirements")) {
            List<Map<String, Object>> minorReqs = (List<Map<String, Object>>) yamlData.get("minor_requirements");
            validateRequirementsList(minorReqs, "minor_requirements");
        }
    }
    
    /**
     * Validate requirements list structure
     */
    // TP: Used cursor to validate list of requirement objects
    private void validateRequirementsList(List<Map<String, Object>> requirements, String sectionName) {
        if (requirements == null || requirements.isEmpty()) {
            throw new IllegalArgumentException(sectionName + " section is empty");
        }
        
        for (int i = 0; i < requirements.size(); i++) {
            Map<String, Object> req = requirements.get(i);
            validateRequirementStructure(req, sectionName, i);
        }
    }
    
    /**
     * Validate individual requirement structure
     */
    // TP: Used cursor to validate a single requirement object shape
    private void validateRequirementStructure(Map<String, Object> req, String sectionName, int index) {
        String prefix = sectionName + "[" + index + "]";
        
        // Check required fields
        if (!req.containsKey("subject_code")) {
            throw new IllegalArgumentException(prefix + " missing required field: subject_code");
        }
        
        if (!req.containsKey("degree_type")) {
            throw new IllegalArgumentException(prefix + " missing required field: degree_type");
        }
        
        if (!req.containsKey("total_credits_required")) {
            throw new IllegalArgumentException(prefix + " missing required field: total_credits_required");
        }
        
        // Validate field types
        if (!(req.get("subject_code") instanceof String)) {
            throw new IllegalArgumentException(prefix + " subject_code must be a string");
        }
        
        if (!(req.get("degree_type") instanceof String)) {
            throw new IllegalArgumentException(prefix + " degree_type must be a string");
        }
        
        if (!(req.get("total_credits_required") instanceof Integer)) {
            throw new IllegalArgumentException(prefix + " total_credits_required must be an integer");
        }
        
        // Validate degree type value
        String degreeType = (String) req.get("degree_type");
        try {
            DegreeType.valueOf(degreeType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(prefix + " invalid degree_type: " + degreeType);
        }
    }
}

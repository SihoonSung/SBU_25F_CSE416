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
    public List<DegreeRequirement> parseAndCreateRequirements(MultipartFile file, String createdBy) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> yamlData = yaml.load(inputStream);
            
            List<DegreeRequirement> requirements = new ArrayList<>();
            
            // Parse degree requirements
            if (yamlData.containsKey("degree_requirements")) {
                List<Map<String, Object>> degreeReqs = (List<Map<String, Object>>) yamlData.get("degree_requirements");
                for (Map<String, Object> reqData : degreeReqs) {
                    DegreeRequirement requirement = parseDegreeRequirement(reqData, yamlData, createdBy);
                    if (requirement != null) {
                        requirements.add(requirement);
                    }
                }
            }
            
            // Parse minor requirements
            if (yamlData.containsKey("minor_requirements")) {
                List<Map<String, Object>> minorReqs = (List<Map<String, Object>>) yamlData.get("minor_requirements");
                for (Map<String, Object> reqData : minorReqs) {
                    DegreeRequirement requirement = parseDegreeRequirement(reqData, yamlData, createdBy);
                    if (requirement != null) {
                        requirements.add(requirement);
                    }
                }
            }
            
            return requirements;
        }
    }
    
    /**
     * Parse individual degree requirement from YAML data
     */
    private DegreeRequirement parseDegreeRequirement(Map<String, Object> reqData, Map<String, Object> yamlData, String createdBy) {
        try {
            // Extract basic information
            String subjectCode = (String) reqData.get("subject_code");
            String degreeTypeStr = (String) reqData.get("degree_type");
            Integer totalCredits = (Integer) reqData.get("total_credits_required");
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
                throw new IllegalArgumentException("Missing required fields: subject_code, degree_type, or total_credits_required");
            }
            
            // Parse degree type
            DegreeType degreeType;
            try {
                degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid degree type: " + degreeTypeStr);
            }
            
            // Create degree requirement
            DegreeRequirement requirement = new DegreeRequirement(
                    subjectCode, degreeType, requirementsVersion, effectiveTerm, totalCredits, createdBy);
            
            // Set optional fields
            requirement.setIsRestricted(isRestricted);
            requirement.setGpaRequirement(gpaRequirement);
            
            // Parse and set admission requirements
            if (reqData.containsKey("admission_requirements")) {
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
            
            // Parse and set prerequisite courses
            if (reqData.containsKey("prerequisite_courses")) {
                Object prereqCourses = reqData.get("prerequisite_courses");
                String prereqCoursesJson = convertToJson(prereqCourses);
                requirement.setPrerequisiteCourses(prereqCoursesJson);
            }
            
            // Parse and set SBC requirements
            if (reqData.containsKey("sbc_requirements")) {
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
     * Convert object to JSON string
     */
    private String convertToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        
        try {
            // Simple JSON conversion for basic objects
            if (obj instanceof String) {
                return (String) obj;
            } else if (obj instanceof Map || obj instanceof List) {
                // For complex objects, we'll use a simple string representation
                // In a real implementation, you might want to use a proper JSON library
                return obj.toString();
            } else {
                return obj.toString();
            }
        } catch (Exception e) {
            return obj.toString();
        }
    }
    
    /**
     * Validate YAML file structure
     */
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

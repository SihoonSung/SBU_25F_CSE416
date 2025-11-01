package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.DegreeRequirement;
import edu.sbu.cse416.sam_system.entity.DegreeType;
import edu.sbu.cse416.sam_system.repository.DegreeRequirementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TP : Used cursor to implement Service class for managing degree requirements.
 * Handles YAML import, CRUD operations, and requirement validation.
 * 
 * Note: Degree requirements management system was implemented 
 * with assistance from Cursor AI and ChatGPT for proper YAML parsing 
 * and data validation capabilities.
 */
@Service
@Transactional
public class DegreeRequirementService {
    
    @Autowired
    private DegreeRequirementRepository degreeRequirementRepository;
    
    @Autowired
    private YamlParsingService yamlParsingService;
    
    /**
     * Import degree requirements from YAML file
     */
    // TP: Used cursor to import degree requirements from YAML and persist
    public List<DegreeRequirement> importDegreeRequirements(MultipartFile file, String createdBy) {
        try {
            // Validate file
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("File is empty or null");
            }
            
            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.toLowerCase().endsWith(".yaml") && 
                !filename.toLowerCase().endsWith(".yml"))) {
                throw new IllegalArgumentException("File must be a YAML file (.yaml or .yml)");
            }
            
            // Parse YAML file
            List<DegreeRequirement> requirements = yamlParsingService.parseAndCreateRequirements(file, createdBy);
            
            // Save requirements to database
            List<DegreeRequirement> savedRequirements = new ArrayList<>();
            for (DegreeRequirement requirement : requirements) {
                // Check if requirement already exists for this term
                if (degreeRequirementRepository.existsBySubjectCodeAndDegreeTypeAndEffectiveTerm(
                        requirement.getSubjectCode(), 
                        requirement.getDegreeType(), 
                        requirement.getEffectiveTerm())) {
                    throw new IllegalArgumentException("Degree requirement already exists for " + 
                            requirement.getSubjectCode() + " " + requirement.getDegreeType() + 
                            " in " + requirement.getEffectiveTerm());
                }
                
                DegreeRequirement saved = degreeRequirementRepository.save(requirement);
                savedRequirements.add(saved);
            }
            
            return savedRequirements;
            
        } catch (IOException e) {
            throw new RuntimeException("Error reading YAML file: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error importing degree requirements: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get all active degree requirements
     */
    @Transactional(readOnly = true)
    // TP: Used cursor to list all active degree requirements
    public List<DegreeRequirement> getAllActiveRequirements() {
        return degreeRequirementRepository.findAllActive();
    }
    
    /**
     * Get degree requirements by subject code
     */
    @Transactional(readOnly = true)
    // TP: Used cursor to fetch requirements for a subject
    public List<DegreeRequirement> getRequirementsBySubject(String subjectCode) {
        return degreeRequirementRepository.findBySubjectCodeAndActive(subjectCode);
    }
    
    /**
     * Get degree requirements by effective term
     */
    @Transactional(readOnly = true)
    // TP: Used cursor to fetch requirements by effective term
    public List<DegreeRequirement> getRequirementsByTerm(String effectiveTerm) {
        return degreeRequirementRepository.findByEffectiveTermAndActive(effectiveTerm);
    }
    
    /**
     * Get specific degree requirement
     */
    @Transactional(readOnly = true)
    // TP: Used cursor to fetch a specific requirement by subject and type
    public Optional<DegreeRequirement> getRequirement(String subjectCode, DegreeType degreeType) {
        return degreeRequirementRepository.findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);
    }
    
    /**
     * Get restricted degree requirements
     */
    @Transactional(readOnly = true)
    // TP: Used cursor to list restricted requirements
    public List<DegreeRequirement> getRestrictedRequirements() {
        return degreeRequirementRepository.findRestrictedRequirements();
    }
    
    /**
     * Create a new degree requirement
     */
    // TP: Used cursor to create a new degree requirement
    public DegreeRequirement createRequirement(DegreeRequirement requirement) {
        // Check if requirement already exists for the term
        if (degreeRequirementRepository.existsBySubjectCodeAndDegreeTypeAndEffectiveTerm(
                requirement.getSubjectCode(), 
                requirement.getDegreeType(), 
                requirement.getEffectiveTerm())) {
            throw new IllegalArgumentException("Degree requirement already exists for this term");
        }
        
        return degreeRequirementRepository.save(requirement);
    }
    
    /**
     * Update an existing degree requirement
     */
    // TP: Used cursor to update an existing requirement
    public DegreeRequirement updateRequirement(Long requirementId, DegreeRequirement updatedRequirement) {
        Optional<DegreeRequirement> existingOpt = degreeRequirementRepository.findById(requirementId);
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("Degree requirement not found");
        }
        
        DegreeRequirement existing = existingOpt.get();
        existing.setSubjectCode(updatedRequirement.getSubjectCode());
        existing.setDegreeType(updatedRequirement.getDegreeType());
        existing.setRequirementsVersion(updatedRequirement.getRequirementsVersion());
        existing.setEffectiveTerm(updatedRequirement.getEffectiveTerm());
        existing.setTotalCreditsRequired(updatedRequirement.getTotalCreditsRequired());
        existing.setIsRestricted(updatedRequirement.getIsRestricted());
        existing.setAdmissionRequirements(updatedRequirement.getAdmissionRequirements());
        existing.setCourseRequirements(updatedRequirement.getCourseRequirements());
        existing.setGpaRequirement(updatedRequirement.getGpaRequirement());
        existing.setPrerequisiteCourses(updatedRequirement.getPrerequisiteCourses());
        existing.setSbcRequirements(updatedRequirement.getSbcRequirements());
        
        return degreeRequirementRepository.save(existing);
    }
    
    /**
     * Deactivate a degree requirement
     */
    // TP: Used cursor to deactivate a requirement
    public void deactivateRequirement(Long requirementId) {
        Optional<DegreeRequirement> requirementOpt = degreeRequirementRepository.findById(requirementId);
        if (requirementOpt.isEmpty()) {
            throw new IllegalArgumentException("Degree requirement not found");
        }
        
        DegreeRequirement requirement = requirementOpt.get();
        requirement.setIsActive(false);
        degreeRequirementRepository.save(requirement);
    }
    
    /**
     * Check if a degree requirement exists for a term
     */
    @Transactional(readOnly = true)
    // TP: Used cursor to check requirement existence for a term
    public boolean requirementExistsForTerm(String subjectCode, DegreeType degreeType, String effectiveTerm) {
        return degreeRequirementRepository.existsBySubjectCodeAndDegreeTypeAndEffectiveTerm(
                subjectCode, degreeType, effectiveTerm);
    }
    
    /**
     * Get degree requirements for multiple subjects
     */
    @Transactional(readOnly = true)
    // TP: Used cursor to fetch requirements for multiple subjects
    public List<DegreeRequirement> getRequirementsForSubjects(List<String> subjectCodes) {
        return degreeRequirementRepository.findBySubjectCodeInAndActive(subjectCodes);
    }
    
    /**
     * Delete a specific degree requirement
     */
    @Transactional
    // TP: Used cursor to delete a specific requirement
    public void deleteRequirement(Long requirementId) {
        Optional<DegreeRequirement> requirement = degreeRequirementRepository.findById(requirementId);
        if (requirement.isPresent()) {
            degreeRequirementRepository.delete(requirement.get());
        } else {
            throw new IllegalArgumentException("Degree requirement not found with ID: " + requirementId);
        }
    }
    
    /**
     * Delete all degree requirements
     */
    @Transactional
    // TP: Used cursor to delete all requirements
    public void deleteAllRequirements() {
        degreeRequirementRepository.deleteAll();
    }
    
    /**
     * Get all available subjects from active degree requirements
     */
    @Transactional(readOnly = true)
    // TP: Used cursor to list all available subjects from active requirements
    public List<String> getAllAvailableSubjects() {
        List<DegreeRequirement> requirements = getAllActiveRequirements();
        return requirements.stream()
                .map(DegreeRequirement::getSubjectCode)
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get available degree types for a specific subject
     */
    @Transactional(readOnly = true)
    // TP: Used cursor to list degree types for a subject
    public List<DegreeType> getAvailableDegreeTypesForSubject(String subjectCode) {
        List<DegreeRequirement> requirements = getRequirementsBySubject(subjectCode);
        return requirements.stream()
                .map(DegreeRequirement::getDegreeType)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get detailed course requirements for a specific subject and degree type
     */
    @Transactional(readOnly = true)
    // TP: Used cursor to provide detailed course requirements payload
    public Map<String, Object> getDetailedCourseRequirements(String subjectCode, DegreeType degreeType) {
        Optional<DegreeRequirement> requirementOpt = getRequirement(subjectCode, degreeType);
        if (requirementOpt.isEmpty()) {
            return new java.util.HashMap<>();
        }
        
        DegreeRequirement requirement = requirementOpt.get();
        Map<String, Object> details = new java.util.HashMap<>();
        
        details.put("subjectCode", requirement.getSubjectCode());
        details.put("degreeType", requirement.getDegreeType());
        details.put("totalCreditsRequired", requirement.getTotalCreditsRequired());
        details.put("isRestricted", requirement.getIsRestricted());
        details.put("gpaRequirement", requirement.getGpaRequirement());
        
        // Parse JSON fields
        if (requirement.getAdmissionRequirements() != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                details.put("admissionRequirements", mapper.readValue(requirement.getAdmissionRequirements(), Object.class));
            } catch (Exception e) {
                details.put("admissionRequirements", requirement.getAdmissionRequirements());
            }
        }
        
        if (requirement.getCourseRequirements() != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                details.put("courseRequirements", mapper.readValue(requirement.getCourseRequirements(), Object.class));
            } catch (Exception e) {
                details.put("courseRequirements", requirement.getCourseRequirements());
            }
        }
        
        if (requirement.getSbcRequirements() != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                details.put("sbcRequirements", mapper.readValue(requirement.getSbcRequirements(), Object.class));
            } catch (Exception e) {
                details.put("sbcRequirements", requirement.getSbcRequirements());
            }
        }
        
        return details;
    }
}

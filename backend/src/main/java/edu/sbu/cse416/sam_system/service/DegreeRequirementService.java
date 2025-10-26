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
    public List<DegreeRequirement> getAllActiveRequirements() {
        return degreeRequirementRepository.findAllActive();
    }
    
    /**
     * Get degree requirements by subject code
     */
    @Transactional(readOnly = true)
    public List<DegreeRequirement> getRequirementsBySubject(String subjectCode) {
        return degreeRequirementRepository.findBySubjectCodeAndActive(subjectCode);
    }
    
    /**
     * Get degree requirements by effective term
     */
    @Transactional(readOnly = true)
    public List<DegreeRequirement> getRequirementsByTerm(String effectiveTerm) {
        return degreeRequirementRepository.findByEffectiveTermAndActive(effectiveTerm);
    }
    
    /**
     * Get specific degree requirement
     */
    @Transactional(readOnly = true)
    public Optional<DegreeRequirement> getRequirement(String subjectCode, DegreeType degreeType) {
        return degreeRequirementRepository.findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);
    }
    
    /**
     * Get restricted degree requirements
     */
    @Transactional(readOnly = true)
    public List<DegreeRequirement> getRestrictedRequirements() {
        return degreeRequirementRepository.findRestrictedRequirements();
    }
    
    /**
     * Create a new degree requirement
     */
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
    public boolean requirementExistsForTerm(String subjectCode, DegreeType degreeType, String effectiveTerm) {
        return degreeRequirementRepository.existsBySubjectCodeAndDegreeTypeAndEffectiveTerm(
                subjectCode, degreeType, effectiveTerm);
    }
    
    /**
     * Get degree requirements for multiple subjects
     */
    @Transactional(readOnly = true)
    public List<DegreeRequirement> getRequirementsForSubjects(List<String> subjectCodes) {
        return degreeRequirementRepository.findBySubjectCodeInAndActive(subjectCodes);
    }
    
    /**
     * Delete a specific degree requirement
     */
    @Transactional
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
    public void deleteAllRequirements() {
        degreeRequirementRepository.deleteAll();
    }
}

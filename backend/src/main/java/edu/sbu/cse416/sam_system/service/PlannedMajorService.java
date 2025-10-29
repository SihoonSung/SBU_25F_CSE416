package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.PlannedMajor;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.DegreeType;
import edu.sbu.cse416.sam_system.entity.DegreeRequirement;
import edu.sbu.cse416.sam_system.repository.PlannedMajorRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.DegreeRequirementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

/**
 * Service for managing planned majors.
 * Handles business logic for students to plan/unplan majors.
 */
@Service
@Transactional
public class PlannedMajorService {
    
    @Autowired
    private PlannedMajorRepository plannedMajorRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private DegreeRequirementRepository degreeRequirementRepository;
    
    /**
     * Add a major to student's planned list
     */
    // TP Used cursor to add a major to student's planned list
    public PlannedMajor planMajor(Long studentId, String subjectCode, DegreeType degreeType) {
        // Validate student exists
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        // Validate degree requirement exists
        Optional<DegreeRequirement> requirement = degreeRequirementRepository
                .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);
        if (requirement.isEmpty()) {
            throw new IllegalArgumentException("Degree requirement not found for " + subjectCode + " " + degreeType);
        }
        
        // Check if already planned
        Optional<PlannedMajor> existing = plannedMajorRepository
                .findByStudentIdAndSubjectAndDegreeType(studentId, subjectCode, degreeType);
        
        if (existing.isPresent()) {
            PlannedMajor plannedMajor = existing.get();
            if (plannedMajor.getIsActive()) {
                throw new IllegalArgumentException("Major already planned");
            } else {
                // Reactivate existing planned major
                plannedMajor.setIsActive(true);
                plannedMajor.setPlannedAt(LocalDateTime.now());
                return plannedMajorRepository.save(plannedMajor);
            }
        }
        
        // Create new planned major
        PlannedMajor plannedMajor = new PlannedMajor(student, subjectCode, degreeType);
        return plannedMajorRepository.save(plannedMajor);
    }
    
    /**
     * Remove a major from student's planned list
     */
    // TP Used cursor to remove a major from student's planned list
    public void unplanMajor(Long studentId, String subjectCode, DegreeType degreeType) {
        Optional<PlannedMajor> plannedMajor = plannedMajorRepository
                .findByStudentIdAndSubjectAndDegreeType(studentId, subjectCode, degreeType);
        
        if (plannedMajor.isEmpty() || !plannedMajor.get().getIsActive()) {
            throw new IllegalArgumentException("Major not currently planned");
        }
        
        PlannedMajor pm = plannedMajor.get();
        pm.setIsActive(false);
        plannedMajorRepository.save(pm);
    }
    
    /**
     * Get all active planned majors for a student
     */
    // TP Used cursor to get all active planned majors for a student
    @Transactional(readOnly = true)
    public List<PlannedMajor> getStudentPlannedMajors(Long studentId) {
        return plannedMajorRepository.findActiveByStudentId(studentId);
    }
    
    /**
     * Get planned major status with requirement information
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPlannedMajorStatus(Long studentId, String subjectCode, DegreeType degreeType) {
        Map<String, Object> status = new HashMap<>();
        
        // Check if major is planned
        Optional<PlannedMajor> plannedMajor = plannedMajorRepository
                .findByStudentIdAndSubjectAndDegreeType(studentId, subjectCode, degreeType);
        
        status.put("isPlanned", plannedMajor.isPresent() && plannedMajor.get().getIsActive());
        
        if (plannedMajor.isPresent()) {
            status.put("plannedAt", plannedMajor.get().getPlannedAt());
        }
        
        // Get degree requirement information
        Optional<DegreeRequirement> requirement = degreeRequirementRepository
                .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);
        
        if (requirement.isPresent()) {
            DegreeRequirement req = requirement.get();
            status.put("degreeRequirement", Map.of(
                "subjectCode", req.getSubjectCode(),
                "degreeType", req.getDegreeType(),
                "totalCreditsRequired", req.getTotalCreditsRequired(),
                "isRestricted", req.getIsRestricted(),
                "gpaRequirement", req.getGpaRequirement(),
                "admissionRequirements", req.getAdmissionRequirements(),
                "courseRequirements", req.getCourseRequirements(),
                "prerequisiteCourses", req.getPrerequisiteCourses(),
                "sbcRequirements", req.getSbcRequirements()
            ));
        } else {
            status.put("degreeRequirement", null);
        }
        
        return status;
    }
    
    /**
     * Get all planned majors with their status for a student
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllPlannedMajorsWithStatus(Long studentId) {
        List<PlannedMajor> plannedMajors = plannedMajorRepository.findActiveByStudentId(studentId);
        
        return plannedMajors.stream()
                .map(pm -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("plannedMajor", pm);
                    status.put("subjectCode", pm.getSubjectCode());
                    status.put("degreeType", pm.getDegreeType());
                    status.put("plannedAt", pm.getPlannedAt());
                    
                    // Get degree requirement information
                    Optional<DegreeRequirement> requirement = degreeRequirementRepository
                            .findBySubjectCodeAndDegreeTypeAndActive(
                                    pm.getSubjectCode(), pm.getDegreeType());
                    
                    if (requirement.isPresent()) {
                        DegreeRequirement req = requirement.get();
                        status.put("degreeRequirement", Map.of(
                            "totalCreditsRequired", req.getTotalCreditsRequired(),
                            "isRestricted", req.getIsRestricted(),
                            "gpaRequirement", req.getGpaRequirement(),
                            "admissionRequirements", req.getAdmissionRequirements(),
                            "courseRequirements", req.getCourseRequirements(),
                            "prerequisiteCourses", req.getPrerequisiteCourses(),
                            "sbcRequirements", req.getSbcRequirements()
                        ));
                    }
                    
                    return status;
                })
                .toList();
    }
    
    /**
     * Check if student can plan a major (basic validation)
     */
    @Transactional(readOnly = true)
    public boolean canPlanMajor(Long studentId, String subjectCode, DegreeType degreeType) {
        // Check if degree requirement exists
        Optional<DegreeRequirement> requirement = degreeRequirementRepository
                .findBySubjectCodeAndDegreeTypeAndActive(subjectCode, degreeType);
        
        if (requirement.isEmpty()) {
            return false;
        }
        
        // Check if already planned
        Optional<PlannedMajor> existing = plannedMajorRepository
                .findByStudentIdAndSubjectAndDegreeType(studentId, subjectCode, degreeType);
        
        return existing.isEmpty() || !existing.get().getIsActive();
    }
}

package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.DeclarationType;
import edu.sbu.cse416.sam_system.entity.DegreeRequirement;
import edu.sbu.cse416.sam_system.entity.MajorMinorDeclaration;
import edu.sbu.cse416.sam_system.repository.DegreeRequirementRepository;
import edu.sbu.cse416.sam_system.repository.MajorMinorDeclarationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TP: Used cursor to implement service for declaration management.
 */
@Service
public class DeclarationService {

    @Autowired
    private MajorMinorDeclarationRepository declarationRepository;

    @Autowired
    private DegreeRequirementRepository degreeRequirementRepository;

    /**
     * Get all degree requirements
     */
    public List<Map<String, Object>> getAllDegreeRequirements() {
        List<DegreeRequirement> requirements = degreeRequirementRepository.findAll();
        return requirements.stream().map(req -> {
            Map<String, Object> reqMap = new HashMap<>();
            reqMap.put("requirementId", req.getRequirementId());
            reqMap.put("subjectCode", req.getSubjectCode());
            reqMap.put("degreeType", req.getDegreeType());
            reqMap.put("totalCreditsRequired", req.getTotalCreditsRequired());
            reqMap.put("isRestricted", req.getIsRestricted());
            reqMap.put("gpaRequirement", req.getGpaRequirement());
            reqMap.put("admissionRequirements", req.getAdmissionRequirements());
            reqMap.put("courseRequirements", req.getCourseRequirements());
            return reqMap;
        }).toList();
    }

    /**
     * Get current declaration count for a student by type
     */
    public int getCurrentDeclarationCount(Long studentId, DeclarationType declarationType) {
        // This is a simplified implementation - in a real system, you'd want to filter by declaration type as well
        return declarationRepository.findByStudentAndStatus(
            new edu.sbu.cse416.sam_system.entity.User() {{
                setUserId(studentId);
            }}, 
            edu.sbu.cse416.sam_system.entity.DeclarationStatus.PENDING
        ).size() + 
        declarationRepository.findByStudentAndStatus(
            new edu.sbu.cse416.sam_system.entity.User() {{
                setUserId(studentId);
            }}, 
            edu.sbu.cse416.sam_system.entity.DeclarationStatus.APPROVED
        ).size();
    }

    /**
     * Check if student can declare more of a specific type
     */
    public boolean canDeclareMore(Long studentId, DeclarationType declarationType) {
        int currentCount = getCurrentDeclarationCount(studentId, declarationType);
        int maxAllowed = declarationType == DeclarationType.MAJOR ? 2 : 3;
        return currentCount < maxAllowed;
    }

    /**
     * Validate declaration requirements
     */
    public Map<String, Object> validateDeclaration(Long studentId, String subjectCode, 
                                                  String degreeType, DeclarationType declarationType) {
        Map<String, Object> result = new HashMap<>();
        result.put("valid", true);
        result.put("errors", new java.util.ArrayList<String>());

        // Check if student can declare more
        if (!canDeclareMore(studentId, declarationType)) {
            result.put("valid", false);
            ((List<String>) result.get("errors")).add(
                "Maximum " + declarationType.toString().toLowerCase() + " declarations reached"
            );
        }

        // Check degree requirements
        List<DegreeRequirement> requirements = degreeRequirementRepository.findBySubjectCode(subjectCode);
        if (requirements.isEmpty()) {
            result.put("valid", false);
            ((List<String>) result.get("errors")).add("No degree requirements found for " + subjectCode);
        }

        return result;
    }
}

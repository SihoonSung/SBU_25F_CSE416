package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.DeclarationStatus;
import edu.sbu.cse416.sam_system.entity.DeclarationType;
import edu.sbu.cse416.sam_system.entity.DegreeRequirement;
import edu.sbu.cse416.sam_system.entity.DegreeType;
import edu.sbu.cse416.sam_system.entity.MajorMinorDeclaration;
import edu.sbu.cse416.sam_system.repository.MajorMinorDeclarationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service class for managing major/minor declarations.
 */
@Service
@Transactional
public class MajorMinorDeclarationService {
    
    @Autowired
    private MajorMinorDeclarationRepository declarationRepository;
    
    @Autowired
    private DegreeRequirementService degreeRequirementService;
    
    private static final int MAX_MAJORS = 2;
    private static final int MAX_MINORS = 3;
    
    /**
     * Submit a new major/minor declaration
     */
    public MajorMinorDeclaration submitDeclaration(String studentNetId, String subjectCode, 
                                                  DegreeType degreeType, DeclarationType declarationType,
                                                  String effectiveTerm) {
        
        // Validate student can declare this type
        validateDeclarationLimits(studentNetId, declarationType);
        
        // Check if student already has an active declaration for this subject and type
        if (declarationRepository.existsActiveByStudentAndSubjectAndType(studentNetId, subjectCode, declarationType)) {
            throw new IllegalArgumentException("Student already has an active " + declarationType.getDisplayName().toLowerCase() + 
                                             " in " + subjectCode);
        }
        
        // Get degree requirement to determine if advisor approval is needed
        Optional<DegreeRequirement> requirementOpt = degreeRequirementService.getRequirement(subjectCode, degreeType);
        if (requirementOpt.isEmpty()) {
            throw new IllegalArgumentException("Degree requirement not found for " + subjectCode + " " + degreeType);
        }
        
        DegreeRequirement requirement = requirementOpt.get();
        
        // Create declaration
        MajorMinorDeclaration declaration = new MajorMinorDeclaration(
                studentNetId, subjectCode, degreeType, declarationType, effectiveTerm);
        
        // Set advisor if required (for restricted majors/minors)
        if (requirement.getIsRestricted()) {
            // Note: Advisor assignment logic can be enhanced based on department mapping
            declaration.setAdvisorNetId(determineAdvisorForSubject(subjectCode));
        } else {
            // Auto-approve for non-restricted programs
            declaration.setStatus(DeclarationStatus.APPROVED);
            declaration.setAdvisorApprovalDate(LocalDateTime.now());
        }
        
        return declarationRepository.save(declaration);
    }
    
    /**
     * Get all declarations for a student
     */
    @Transactional(readOnly = true)
    public List<MajorMinorDeclaration> getStudentDeclarations(String studentNetId) {
        return declarationRepository.findByStudentNetIdAndActive(studentNetId);
    }
    
    /**
     * Get pending declarations for an advisor
     */
    @Transactional(readOnly = true)
    public List<MajorMinorDeclaration> getPendingDeclarationsForAdvisor(String advisorNetId) {
        return declarationRepository.findPendingByAdvisorNetId(advisorNetId);
    }
    
    /**
     * Approve a declaration
     */
    public MajorMinorDeclaration approveDeclaration(Long declarationId, String advisorNetId, String comments) {
        Optional<MajorMinorDeclaration> declarationOpt = declarationRepository.findById(declarationId);
        if (declarationOpt.isEmpty()) {
            throw new IllegalArgumentException("Declaration not found");
        }
        
        MajorMinorDeclaration declaration = declarationOpt.get();
        
        // Verify advisor has permission to approve
        if (!advisorNetId.equals(declaration.getAdvisorNetId())) {
            throw new IllegalArgumentException("Advisor not authorized to approve this declaration");
        }
        
        if (declaration.getStatus() != DeclarationStatus.PENDING) {
            throw new IllegalArgumentException("Declaration is not in pending status");
        }
        
        declaration.setStatus(DeclarationStatus.APPROVED);
        declaration.setAdvisorApprovalDate(LocalDateTime.now());
        declaration.setAdvisorComments(comments);
        
        return declarationRepository.save(declaration);
    }
    
    /**
     * Reject a declaration
     */
    public MajorMinorDeclaration rejectDeclaration(Long declarationId, String advisorNetId, String comments) {
        Optional<MajorMinorDeclaration> declarationOpt = declarationRepository.findById(declarationId);
        if (declarationOpt.isEmpty()) {
            throw new IllegalArgumentException("Declaration not found");
        }
        
        MajorMinorDeclaration declaration = declarationOpt.get();
        
        // Verify advisor has permission to reject
        if (!advisorNetId.equals(declaration.getAdvisorNetId())) {
            throw new IllegalArgumentException("Advisor not authorized to reject this declaration");
        }
        
        if (declaration.getStatus() != DeclarationStatus.PENDING) {
            throw new IllegalArgumentException("Declaration is not in pending status");
        }
        
        declaration.setStatus(DeclarationStatus.REJECTED);
        declaration.setAdvisorApprovalDate(LocalDateTime.now());
        declaration.setAdvisorComments(comments);
        
        return declarationRepository.save(declaration);
    }
    
    /**
     * Withdraw a declaration (student action)
     */
    public MajorMinorDeclaration withdrawDeclaration(Long declarationId, String studentNetId) {
        Optional<MajorMinorDeclaration> declarationOpt = declarationRepository.findById(declarationId);
        if (declarationOpt.isEmpty()) {
            throw new IllegalArgumentException("Declaration not found");
        }
        
        MajorMinorDeclaration declaration = declarationOpt.get();
        
        // Verify student owns this declaration
        if (!studentNetId.equals(declaration.getStudentNetId())) {
            throw new IllegalArgumentException("Student not authorized to withdraw this declaration");
        }
        
        if (declaration.getStatus() != DeclarationStatus.PENDING) {
            throw new IllegalArgumentException("Only pending declarations can be withdrawn");
        }
        
        declaration.setStatus(DeclarationStatus.WITHDRAWN);
        
        return declarationRepository.save(declaration);
    }
    
    /**
     * Get declarations by status
     */
    @Transactional(readOnly = true)
    public List<MajorMinorDeclaration> getDeclarationsByStatus(DeclarationStatus status) {
        return declarationRepository.findByStatusAndActive(status);
    }
    
    /**
     * Get all active declarations
     */
    @Transactional(readOnly = true)
    public List<MajorMinorDeclaration> getAllActiveDeclarations() {
        return declarationRepository.findAllActive();
    }
    
    /**
     * Validate that student hasn't exceeded declaration limits
     */
    private void validateDeclarationLimits(String studentNetId, DeclarationType declarationType) {
        long currentCount = declarationRepository.countActiveByStudentAndType(studentNetId, declarationType);
        
        if (declarationType == DeclarationType.MAJOR && currentCount >= MAX_MAJORS) {
            throw new IllegalArgumentException("Student cannot declare more than " + MAX_MAJORS + " majors");
        }
        
        if (declarationType == DeclarationType.MINOR && currentCount >= MAX_MINORS) {
            throw new IllegalArgumentException("Student cannot declare more than " + MAX_MINORS + " minors");
        }
    }
    
    /**
     * Determine advisor for a subject (placeholder implementation)
     */
    private String determineAdvisorForSubject(String subjectCode) {
        // Note: Advisor determination logic can be implemented based on department mapping
        // This could be based on a mapping or database lookup
        switch (subjectCode.toUpperCase()) {
            case "CSE":
                return "cse_advisor"; // Placeholder
            case "BIO":
                return "bio_advisor"; // Placeholder
            case "PSY":
                return "psy_advisor"; // Placeholder
            default:
                return "general_advisor"; // Placeholder
        }
    }
    
    /**
     * Get active declarations for a student by type
     */
    @Transactional(readOnly = true)
    public List<MajorMinorDeclaration> getActiveDeclarationsByType(String studentNetId, DeclarationType declarationType) {
        return declarationRepository.findActiveByStudentAndType(studentNetId, declarationType);
    }
    
    /**
     * Get declarations by advisor and status
     */
    @Transactional(readOnly = true)
    public List<MajorMinorDeclaration> getDeclarationsByAdvisorAndStatus(String advisorNetId, DeclarationStatus status) {
        return declarationRepository.findByAdvisorAndStatus(advisorNetId, status);
    }
}

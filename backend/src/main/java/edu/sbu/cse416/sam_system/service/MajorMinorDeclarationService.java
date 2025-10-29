package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.MajorMinorDeclaration;
import edu.sbu.cse416.sam_system.entity.DeclarationType;
import edu.sbu.cse416.sam_system.entity.DeclarationStatus;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.repository.MajorMinorDeclarationRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * //tp : Used CURSOR to implement this :
 * Service for managing major/minor declaration requests.
 * Handles business logic for student declarations and advisor approvals.
 */
@Service
@Transactional
public class MajorMinorDeclarationService {
    
    @Autowired
    private MajorMinorDeclarationRepository declarationRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    // Constants for limits
    private static final int MAX_MAJORS = 2;
    private static final int MAX_MINORS = 3;
    
    /**
     * Submit a new major/minor declaration request
     */
    public MajorMinorDeclaration submitDeclaration(Long studentId, String subjectCode, 
                                                  DeclarationType declarationType,
                                                  String effectiveTerm) {
        
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        // Validate student is a student
        if (student.getRole() != edu.sbu.cse416.sam_system.entity.UserRole.STUDENT) {
            throw new IllegalArgumentException("Only students can declare majors/minors");
        }
        
        // Validate limits
        validateDeclarationLimits(student, declarationType);
        
        // Check for duplicate pending declarations
        if (declarationRepository.existsPendingDeclaration(student, subjectCode, declarationType)) {
            throw new IllegalArgumentException("A pending declaration for this subject already exists");
        }
        
        // Check for duplicate approved declarations
        if (declarationRepository.existsApprovedDeclaration(student, subjectCode, declarationType)) {
            throw new IllegalArgumentException("You already have an approved declaration for this subject");
        }
        
        // Validate academic calendar dates (implement based on your calendar system)
        validateAcademicCalendarDates(effectiveTerm);
        
        // Create new declaration
        MajorMinorDeclaration declaration = new MajorMinorDeclaration();
        declaration.setStudent(student);
        declaration.setStudentNetId(student.getSbuId());
        declaration.setSubjectCode(subjectCode);
        declaration.setDeclarationType(declarationType);
        declaration.setEffectiveTerm(effectiveTerm);
        declaration.setSubmittedAt(LocalDateTime.now());
        
        // Determine if CEAS advisor approval is needed
        if (isCeasTransfer(student, subjectCode)) {
            // Set CEAS advisor (you'll need to implement logic to find CEAS advisor)
            User ceasAdvisor = findCeasAdvisor();
            declaration.setCeasAdvisor(ceasAdvisor);
        } else {
            // Set department advisor (you'll need to implement logic to find department advisor)
            User departmentAdvisor = findDepartmentAdvisor(subjectCode);
            declaration.setAdvisor(departmentAdvisor);
        }
        
        return declarationRepository.save(declaration);
    }
    
    /**
     * Approve a declaration request
     */
    public MajorMinorDeclaration approveDeclaration(Long declarationId, Long advisorId, String notes) {
        MajorMinorDeclaration declaration = declarationRepository.findById(declarationId)
                .orElseThrow(() -> new IllegalArgumentException("Declaration not found"));
        
        User advisor = userRepository.findById(advisorId)
                .orElseThrow(() -> new IllegalArgumentException("Advisor not found"));
        
        // Validate advisor has permission to approve
        if (!canAdvisorApprove(advisor, declaration)) {
            throw new IllegalArgumentException("Advisor does not have permission to approve this declaration");
        }
        
        declaration.approve(advisor, notes);
        declarationRepository.save(declaration);
        
        // Update student's majors/minors in User table
        updateStudentMajorsMinors(declaration);
        
        return declaration;
    }
    
    /**
     * Reject a declaration request
     */
    public MajorMinorDeclaration rejectDeclaration(Long declarationId, Long advisorId, String notes) {
        MajorMinorDeclaration declaration = declarationRepository.findById(declarationId)
                .orElseThrow(() -> new IllegalArgumentException("Declaration not found"));
        
        User advisor = userRepository.findById(advisorId)
                .orElseThrow(() -> new IllegalArgumentException("Advisor not found"));
        
        // Validate advisor has permission to reject
        if (!canAdvisorApprove(advisor, declaration)) {
            throw new IllegalArgumentException("Advisor does not have permission to reject this declaration");
        }
        
        declaration.reject(advisor, notes);
        return declarationRepository.save(declaration);
    }
    
    /**
     * Get all declarations for a student
     */
    public List<MajorMinorDeclaration> getStudentDeclarations(Long studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        return declarationRepository.findByStudentOrderByCreatedAtDesc(student);
    }
    
    /**
     * Get pending declarations for an advisor
     */
    public List<MajorMinorDeclaration> getPendingDeclarationsForAdvisor(Long advisorId) {
        User advisor = userRepository.findById(advisorId)
                .orElseThrow(() -> new IllegalArgumentException("Advisor not found"));
        
        return declarationRepository.findByAdvisorOrCeasAdvisorOrderByCreatedAtDesc(advisor)
                .stream()
                .filter(MajorMinorDeclaration::isPending)
                .toList();
    }
    
    /**
     * Validate declaration limits (2 majors, 3 minors)
     */
    private void validateDeclarationLimits(User student, DeclarationType declarationType) {
        if (declarationType == DeclarationType.MAJOR) {
            // Just count current majors from User table (already approved declarations)
            // Don't double count by adding declaredMajors
            long currentMajors = countCurrentMajorsFromUser(student);
            
            System.out.println("DEBUG: Student " + student.getFirstName() + " " + student.getLastName() + 
                             " - Current majors: " + currentMajors);
            
            if (currentMajors >= MAX_MAJORS) {
                throw new IllegalArgumentException("Students can declare at most " + MAX_MAJORS + " majors");
            }
        } else if (declarationType == DeclarationType.MINOR) {
            // Just count current minors from User table (already approved declarations)
            long currentMinors = countCurrentMinorsFromUser(student);
            
            if (currentMinors >= MAX_MINORS) {
                throw new IllegalArgumentException("Students can declare at most " + MAX_MINORS + " minors");
            }
        }
    }
    
    /**
     * Count current majors from User table (JSON field)
     */
    private long countCurrentMajorsFromUser(User student) {
        if (student.getMajors() == null || student.getMajors().trim().isEmpty()) {
            return 0;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<?> majors = mapper.readValue(student.getMajors(), List.class);
            return majors != null ? majors.size() : 0;
        } catch (Exception e) {
            System.err.println("Error parsing majors JSON: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Count current minors from User table (JSON field)
     */
    private long countCurrentMinorsFromUser(User student) {
        if (student.getMinors() == null || student.getMinors().trim().isEmpty()) {
            return 0;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<?> minors = mapper.readValue(student.getMinors(), List.class);
            return minors != null ? minors.size() : 0;
        } catch (Exception e) {
            System.err.println("Error parsing minors JSON: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Validate academic calendar dates
     */
    private void validateAcademicCalendarDates(String effectiveTerm) {
        if (effectiveTerm == null || effectiveTerm.trim().isEmpty()) {
            throw new IllegalArgumentException("Effective term is required");
        }
        
        // Declaration periods based on academic calendar
        java.time.LocalDate currentDate = java.time.LocalDate.now();
        
        if ("FALL 2025".equals(effectiveTerm)) {
            java.time.LocalDate startDate = java.time.LocalDate.of(2025, 3, 31);
            java.time.LocalDate endDate = java.time.LocalDate.of(2025, 8, 29);
            
            if (currentDate.isBefore(startDate) || currentDate.isAfter(endDate)) {
                throw new IllegalArgumentException("Declaration period for Fall 2025 has ended. Please check the academic calendar for available terms.");
            }
        } else if ("SPRING 2026".equals(effectiveTerm)) {
            java.time.LocalDate startDate = java.time.LocalDate.of(2025, 10, 25);
            java.time.LocalDate endDate = java.time.LocalDate.of(2026, 1, 30);
            
            if (currentDate.isBefore(startDate) || currentDate.isAfter(endDate)) {
                throw new IllegalArgumentException("Declaration period for Spring 2026 has ended. Please check the academic calendar for available terms.");
            }
        } else {
            throw new IllegalArgumentException("Invalid effective term. Please select a valid term.");
        }
    }
    
    /**
     * Check if this is a CEAS transfer
     */
    // TP Used cursor to check if declaration requires CEAS transfer
    private boolean isCeasTransfer(User student, String subjectCode) {
        // CEAS subjects: AMS, BME, CME, CIV, ECE, CSE, ESE, ESG, ISE, MEC, TSM
        if (!isCeasMajor(subjectCode)) {
            return false;
        }
        
        // Check if student is already in CEAS (has AOI or declared major in CEAS)
        return !isStudentInCeas(student);
    }
    
    /**
     * Check if a subject is a CEAS major
     */
    // TP Used cursor to check if subject is a CEAS major
    private boolean isCeasMajor(String subjectCode) {
        String[] ceasSubjects = {"AMS", "BME", "CME", "CIV", "ECE", "CSE", "ESE", "ESG", "ISE", "MEC", "TSM"};
        for (String ceasSubject : ceasSubjects) {
            if (ceasSubject.equals(subjectCode)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if student is already in CEAS
     */
    // TP Used cursor to check if student is already in CEAS
    private boolean isStudentInCeas(User student) {
        // Check if student has AOI (Area of Interest) in CEAS
        if (student.getAoi() != null && !student.getAoi().trim().isEmpty()) {
            if (isCeasMajor(student.getAoi())) {
                return true;
            }
        }
        
        // Check if student has any approved CEAS majors
        if (student.getMajors() == null || student.getMajors().trim().isEmpty()) {
            return false;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<?> majors = mapper.readValue(student.getMajors(), List.class);
            if (majors != null) {
                for (Object major : majors) {
                    String majorStr = major.toString();
                    if (isCeasMajor(majorStr)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing majors JSON: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Find CEAS advisor
     */
    // TP Used cursor to find CEAS advisor for CEAS transfers
    private User findCeasAdvisor() {
        // Find advisor with academic_unit containing any CEAS subject
        List<User> allAdvisors = userRepository.findByRole(edu.sbu.cse416.sam_system.entity.UserRole.ADVISOR);
        
        for (User advisor : allAdvisors) {
            if (advisor.getAcademicUnit() != null) {
                String academicUnit = advisor.getAcademicUnit();
                // Check if advisor's academic unit contains any CEAS subject
                String[] ceasSubjects = {"AMS", "BME", "CME", "CIV", "ECE", "CSE", "ESE", "ESG", "ISE", "MEC", "TSM"};
                for (String ceasSubject : ceasSubjects) {
                    if (academicUnit.contains(ceasSubject)) {
                        return advisor;
                    }
                }
            }
        }
        
        // Fallback: find advisor with level "university" or "ceas"
        for (User advisor : allAdvisors) {
            if ("university".equals(advisor.getLevel()) || "ceas".equals(advisor.getLevel())) {
                return advisor;
            }
        }
        
        throw new IllegalStateException("No CEAS advisor found");
    }
    
    /**
     * Find department advisor for subject
     */
    private User findDepartmentAdvisor(String subjectCode) {
        // Try to find advisor with exact academic_unit match
        List<User> advisors = userRepository.findByRoleAndAcademicUnit(
                edu.sbu.cse416.sam_system.entity.UserRole.ADVISOR, subjectCode);
        
        if (!advisors.isEmpty()) {
            return advisors.get(0);
        }
        
        // Fallback: find any advisor with subject in academic_unit
        List<User> allAdvisors = userRepository.findByRole(edu.sbu.cse416.sam_system.entity.UserRole.ADVISOR);
        for (User advisor : allAdvisors) {
            if (advisor.getAcademicUnit() != null && advisor.getAcademicUnit().contains(subjectCode)) {
                return advisor;
            }
        }
        
        // Final fallback: find any department-level advisor
        List<User> departmentAdvisors = userRepository.findByAdvisorLevel("department");
        if (!departmentAdvisors.isEmpty()) {
            return departmentAdvisors.get(0);
        }
        
        throw new IllegalStateException("No department advisor found for " + subjectCode);
    }
    
    /**
     * Check if advisor can approve this declaration
     */
    // TP Used cursor to check if advisor can approve declaration
    private boolean canAdvisorApprove(User advisor, MajorMinorDeclaration declaration) {
        // Check if advisor is the assigned advisor or CEAS advisor
        if ((declaration.getAdvisor() != null && declaration.getAdvisor().equals(advisor)) ||
            (declaration.getCeasAdvisor() != null && declaration.getCeasAdvisor().equals(advisor))) {
            return true;
        }
        
        // Additional permission checks based on advisor type and subject
        String subjectCode = declaration.getSubjectCode();
        
        // University-level advisors can approve anything
        if ("university".equals(advisor.getLevel())) {
            return true;
        }
        
        // CEAS advisors can approve all CEAS subjects
        if (advisor.getAcademicUnit() != null) {
            String academicUnit = advisor.getAcademicUnit();
            String[] ceasSubjects = {"AMS", "BME", "CME", "CIV", "ECE", "CSE", "ESE", "ESG", "ISE", "MEC", "TSM"};
            for (String ceasSubject : ceasSubjects) {
                if (academicUnit.contains(ceasSubject)) {
                    // This is a CEAS advisor, can approve any CEAS subject
                    return isCeasMajor(subjectCode);
                }
            }
        }
        
        // Department advisors can approve their subjects
        if (advisor.getAcademicUnit() != null && advisor.getAcademicUnit().contains(subjectCode)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get all declarations for advisor
     */
    public List<MajorMinorDeclaration> getAllDeclarationsForAdvisor(Long advisorId) {
        User advisor = userRepository.findById(advisorId)
                .orElseThrow(() -> new IllegalArgumentException("Advisor not found"));
        
        if (!advisor.isAdvisor()) {
            throw new IllegalArgumentException("User is not an advisor");
        }
        
        return declarationRepository.findByAdvisorOrCeasAdvisorOrderByCreatedAtDesc(advisor);
    }
    
    /**
     * Withdraw a declaration
     * If PENDING: Delete from database completely
     * If APPROVED/REJECTED: Mark as WITHDRAWN (soft delete)
     */
    public MajorMinorDeclaration withdrawDeclaration(Long declarationId, Long studentId) {
        MajorMinorDeclaration declaration = declarationRepository.findById(declarationId)
                .orElseThrow(() -> new IllegalArgumentException("Declaration not found"));
        
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        // Check if student owns this declaration
        if (!declaration.getStudent().equals(student)) {
            throw new IllegalArgumentException("You can only withdraw your own declarations");
        }
        
        // If PENDING: Delete from database completely
        if (declaration.isPending()) {
            declarationRepository.delete(declaration);
            return null; // Deleted
        }
        
        // If already APPROVED or REJECTED: Mark as WITHDRAWN (soft delete)
        declaration.setStatus(DeclarationStatus.WITHDRAWN);
        declaration.setUpdatedAt(LocalDateTime.now());
        declaration.setIsActive(false);
        
        return declarationRepository.save(declaration);
    }
    
    /**
     * Get declaration by ID
     */
    public Optional<MajorMinorDeclaration> getDeclarationById(Long declarationId) {
        return declarationRepository.findById(declarationId);
    }
    
    /**
     * Update student's majors/minors in User table after approval
     */
    private void updateStudentMajorsMinors(MajorMinorDeclaration declaration) {
        try {
            User student = declaration.getStudent();
            String subjectCode = declaration.getSubjectCode();
            
            if (declaration.getDeclarationType() == DeclarationType.MAJOR) {
                // Add to majors JSON array
                String majorsJson = student.getMajors();
                List<String> majors = new java.util.ArrayList<>();
                
                if (majorsJson != null && !majorsJson.trim().isEmpty()) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        @SuppressWarnings("unchecked")
                        List<String> parsedMajors = mapper.readValue(majorsJson, List.class);
                        majors = parsedMajors;
                    } catch (Exception e) {
                        System.err.println("Error parsing majors JSON: " + e.getMessage());
                    }
                }
                
                // Add new major if not already present
                if (!majors.contains(subjectCode)) {
                    majors.add(subjectCode);
                }
                
                // Update JSON
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String updatedMajorsJson = mapper.writeValueAsString(majors);
                student.setMajors(updatedMajorsJson);
                
                // TP used cursor to: Also update degrees array with "BA" or "BS" based on degree requirements
                
            } else if (declaration.getDeclarationType() == DeclarationType.MINOR) {
                // Add to minors JSON array
                String minorsJson = student.getMinors();
                List<String> minors = new java.util.ArrayList<>();
                
                if (minorsJson != null && !minorsJson.trim().isEmpty()) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        @SuppressWarnings("unchecked")
                        List<String> parsedMinors = mapper.readValue(minorsJson, List.class);
                        minors = parsedMinors;
                    } catch (Exception e) {
                        System.err.println("Error parsing minors JSON: " + e.getMessage());
                    }
                }
                
                // Add new minor if not already present
                if (!minors.contains(subjectCode)) {
                    minors.add(subjectCode);
                }
                
                // Update JSON
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String updatedMinorsJson = mapper.writeValueAsString(minors);
                student.setMinors(updatedMinorsJson);
            }
            
            // Save updated student
            userRepository.save(student);
            
        } catch (Exception e) {
            System.err.println("Error updating student majors/minors: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
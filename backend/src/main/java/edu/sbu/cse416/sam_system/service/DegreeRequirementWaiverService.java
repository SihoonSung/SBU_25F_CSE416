package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.DegreeRequirementWaiver;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.repository.DegreeRequirementWaiverRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing degree requirement waivers.
 * 
 * Features:
 * - Create waivers (by registrar or advisor)
 * - List waivers for a student
 * 
 * TP: Used Cursor to implement degree requirement waiver service
 */
@Service
@Transactional
public class DegreeRequirementWaiverService {
    
    @Autowired
    private DegreeRequirementWaiverRepository waiverRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private edu.sbu.cse416.sam_system.service.AuditLogService auditLogService;
    
    /**
     * Create a new course waiver for degree requirements.
     * 
     * The registrar waives a degree requirement indirectly, by waiving individual courses
     * as if the student had taken the courses and received an adequate grade.
     * Such courses contribute to satisfying major-specific degree requirements
     * but do NOT contribute to university-level minimum credits or GPA.
     * 
     * @param studentId Student for whom course is being waived
     * @param subjectCode Subject code for the major/minor (e.g., "CSE")
     * @param department Course department (e.g., "CSE")
     * @param courseNum Course number (e.g., 300)
     * @param credits Number of credits for this course
     * @param grade Grade assigned (e.g., "A", "B+", "P") - does NOT affect GPA
     * @param approvedById User ID who is approving the waiver (registrar or advisor)
     * @param note Optional note from approver
     * @return Created waiver
     */
    public DegreeRequirementWaiver createWaiver(Long studentId, String subjectCode, String department, 
                                                Integer courseNum, Integer credits, String grade,
                                                Long approvedById, String note) {
        System.out.println("=== DEBUG: DegreeRequirementWaiverService.createWaiver ===");
        System.out.println("DEBUG: studentId: " + studentId);
        System.out.println("DEBUG: approvedById: " + approvedById);
        
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> {
                System.out.println("DEBUG: Student not found with ID: " + studentId);
                return new RuntimeException("Student not found");
            });
        System.out.println("DEBUG: Student found: " + student.getFullName() + " (ID: " + student.getUserId() + ")");
        
        User approvedBy = userRepository.findById(approvedById)
            .orElseThrow(() -> {
                System.out.println("DEBUG: User not found with ID: " + approvedById);
                return new RuntimeException("User not found");
            });
        System.out.println("DEBUG: ApprovedBy found: " + approvedBy.getFullName() + " (ID: " + approvedBy.getUserId() + ", Role: " + approvedBy.getRole() + ")");
        
        // Verify approver is registrar or advisor
        if (approvedBy.getRole() != UserRole.REGISTRAR && approvedBy.getRole() != UserRole.ADVISOR) {
            System.out.println("DEBUG: User role is not REGISTRAR or ADVISOR: " + approvedBy.getRole());
            throw new RuntimeException("Only registrars or advisors can create degree requirement waivers");
        }
        
        // Create new waiver
        System.out.println("DEBUG: Creating waiver entity...");
        DegreeRequirementWaiver waiver = new DegreeRequirementWaiver(
            student, subjectCode, department, courseNum, credits, grade, approvedBy);
        waiver.setNote(note);
        waiver.setApprovedAt(LocalDateTime.now());
        
        System.out.println("DEBUG: Saving waiver to database...");
        waiver = waiverRepository.save(waiver);
        System.out.println("DEBUG: Waiver saved with ID: " + waiver.getWaiverId());
        
        // Log audit entry - course code format: "CSE 300"
        String courseCode = department + " " + courseNum;
        System.out.println("DEBUG: Logging audit entry for course: " + courseCode);
        auditLogService.logDegreeRequirementWaived(student, approvedBy, courseCode, note);
        System.out.println("DEBUG: Audit log entry created");
        
        System.out.println("DEBUG: createWaiver completed successfully");
        return waiver;
    }
    
    /**
     * Get all waivers for a student
     * @param studentId Student ID
     * @return List of waivers
     */
    public List<DegreeRequirementWaiver> getWaiversByStudent(Long studentId) {
        return waiverRepository.findByStudentId(studentId);
    }
    
    /**
     * Get waivers for a student and subject code
     * @param studentId Student ID
     * @param subjectCode Subject code
     * @return List of waivers
     */
    public List<DegreeRequirementWaiver> getWaiversByStudentAndSubject(Long studentId, String subjectCode) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        return waiverRepository.findByStudentAndSubjectCode(student, subjectCode);
    }
    
    /**
     * Get waiver by ID
     * @param waiverId Waiver ID
     * @return Waiver
     */
    public DegreeRequirementWaiver getWaiverById(Long waiverId) {
        return waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
    }
    
    /**
     * Delete a waiver (only by registrar or advisor who created it)
     * @param waiverId Waiver ID
     * @param deletedById User ID who is deleting the waiver
     * @return Deleted waiver
     */
    public DegreeRequirementWaiver deleteWaiver(Long waiverId, Long deletedById) {
        DegreeRequirementWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User deletedBy = userRepository.findById(deletedById)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Verify user has permission (registrar or advisor)
        if (deletedBy.getRole() != UserRole.REGISTRAR && deletedBy.getRole() != UserRole.ADVISOR) {
            throw new RuntimeException("Only registrars or advisors can delete waivers");
        }
        
        waiverRepository.delete(waiver);
        return waiver;
    }
}


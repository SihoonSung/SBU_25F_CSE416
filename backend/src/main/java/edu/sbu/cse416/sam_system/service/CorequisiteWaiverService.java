package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.CorequisiteWaiver;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.repository.CorequisiteWaiverRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing corequisite waivers.
 * 
 * Features:
 * - Create waiver requests
 * - Instructor approvals
 * - Advisor approvals
 * - Check if waiver exists and is approved
 * 
 * TP: Used Cursor to implement corequisite waiver service
 */
@Service
@Transactional
public class CorequisiteWaiverService {
    
    @Autowired
    private CorequisiteWaiverRepository waiverRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ClassRepository classRepository;
    
    @Autowired
    private edu.sbu.cse416.sam_system.service.AuditLogService auditLogService;
    
    /**
     * Create a new waiver request
     * @param studentId Student requesting the waiver
     * @param classId Class ID for which corequisite is being waived
     * @param corequisite Corequisite being waived (e.g., "CSE 114")
     * @param reason Optional reason for the request
     * @return Created waiver
     */
    public CorequisiteWaiver createWaiver(Long studentId, Long classId, String corequisite, String reason) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        
        if (student.getRole() != UserRole.STUDENT) {
            throw new RuntimeException("Only students can request waivers");
        }
        
        ClassEntity classEntity = classRepository.findById(classId)
            .orElseThrow(() -> new RuntimeException("Class not found"));
        
        // Check if waiver already exists
        Optional<CorequisiteWaiver> existing = waiverRepository.findApprovedWaiver(student, classEntity, corequisite);
        if (existing.isPresent()) {
            throw new RuntimeException("Approved waiver already exists for this corequisite");
        }
        
        // Create new waiver
        CorequisiteWaiver waiver = new CorequisiteWaiver(student, classEntity, corequisite);
        waiver.setReason(reason);
        waiver.setStatus(CorequisiteWaiver.WaiverStatus.PENDING);
        
        waiver = waiverRepository.save(waiver);
        
        // Log audit entry for student-initiated waiver request
        String courseCode = classEntity.getDepartment() + " " + classEntity.getCourseNum();
        auditLogService.logCorequisiteWaiverRequested(student, courseCode, corequisite, reason);
        
        return waiver;
    }
    
    /**
     * Approve waiver by instructor
     * @param waiverId Waiver ID
     * @param instructorId Instructor ID approving the waiver
     * @param note Optional note from instructor
     * @return Updated waiver
     */
    public CorequisiteWaiver approveByInstructor(Long waiverId, Long instructorId, String note) {
        CorequisiteWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User instructor = userRepository.findById(instructorId)
            .orElseThrow(() -> new RuntimeException("Instructor not found"));
        
        if (instructor.getRole() != UserRole.INSTRUCTOR) {
            throw new RuntimeException("Only instructors can approve waivers");
        }
        
        // Verify instructor teaches this class
        ClassEntity classEntity = waiver.getClassEntity();
        String classInstructor = classEntity.getInstructor();
        if (classInstructor == null || instructor.getLastName() == null || 
            !classInstructor.toLowerCase().contains(instructor.getLastName().toLowerCase())) {
            throw new RuntimeException("Instructor is not assigned to this class");
        }
        
        waiver.setInstructorApproved(true);
        waiver.setInstructorApprovedBy(instructor);
        waiver.setInstructorApprovedAt(LocalDateTime.now());
        if (note != null && !note.trim().isEmpty()) {
            waiver.setNote(note);
        }
        waiver.updateStatus();
        
        waiver = waiverRepository.save(waiver);
        
        // Log audit entry if waiver is approved
        if (waiver.getStatus() == CorequisiteWaiver.WaiverStatus.APPROVED) {
            String courseCode = classEntity.getDepartment() + " " + classEntity.getCourseNum();
            auditLogService.logCorequisiteWaived(waiver.getStudent(), instructor, courseCode + " (" + waiver.getCorequisite() + ")", note);
        }
        
        return waiver;
    }
    
    /**
     * Reject waiver by instructor
     * @param waiverId Waiver ID
     * @param instructorId Instructor ID rejecting the waiver
     * @param note Optional note from instructor
     * @return Updated waiver
     */
    public CorequisiteWaiver rejectByInstructor(Long waiverId, Long instructorId, String note) {
        CorequisiteWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User instructor = userRepository.findById(instructorId)
            .orElseThrow(() -> new RuntimeException("Instructor not found"));
        
        if (instructor.getRole() != UserRole.INSTRUCTOR) {
            throw new RuntimeException("Only instructors can reject waivers");
        }
        
        ClassEntity classEntity = waiver.getClassEntity();
        String classInstructor = classEntity.getInstructor();
        if (classInstructor == null || instructor.getLastName() == null || 
            !classInstructor.toLowerCase().contains(instructor.getLastName().toLowerCase())) {
            throw new RuntimeException("Instructor is not assigned to this class");
        }
        
        waiver.setInstructorApproved(false);
        waiver.setInstructorApprovedBy(instructor);
        waiver.setInstructorApprovedAt(LocalDateTime.now());
        if (note != null && !note.trim().isEmpty()) {
            waiver.setNote(note);
        }
        waiver.updateStatus();
        
        return waiverRepository.save(waiver);
    }
    
    /**
     * Approve waiver by advisor
     * @param waiverId Waiver ID
     * @param advisorId Advisor ID approving the waiver
     * @param note Optional note from advisor
     * @return Updated waiver
     */
    public CorequisiteWaiver approveByAdvisor(Long waiverId, Long advisorId, String note) {
        CorequisiteWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User advisor = userRepository.findById(advisorId)
            .orElseThrow(() -> new RuntimeException("Advisor not found"));
        
        if (advisor.getRole() != UserRole.ADVISOR) {
            throw new RuntimeException("Only advisors can approve waivers");
        }
        
        waiver.setAdvisorApproved(true);
        waiver.setAdvisorApprovedBy(advisor);
        waiver.setAdvisorApprovedAt(LocalDateTime.now());
        if (note != null && !note.trim().isEmpty()) {
            waiver.setNote(note);
        }
        waiver.updateStatus();
        
        waiver = waiverRepository.save(waiver);
        
        // Log audit entry if waiver is approved
        if (waiver.getStatus() == CorequisiteWaiver.WaiverStatus.APPROVED) {
            String courseCode = waiver.getClassEntity().getDepartment() + " " + waiver.getClassEntity().getCourseNum();
            auditLogService.logCorequisiteWaived(waiver.getStudent(), advisor, courseCode + " (" + waiver.getCorequisite() + ")", note);
        }
        
        return waiver;
    }
    
    /**
     * Reject waiver by advisor
     * @param waiverId Waiver ID
     * @param advisorId Advisor ID rejecting the waiver
     * @param note Optional note from advisor
     * @return Updated waiver
     */
    public CorequisiteWaiver rejectByAdvisor(Long waiverId, Long advisorId, String note) {
        CorequisiteWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User advisor = userRepository.findById(advisorId)
            .orElseThrow(() -> new RuntimeException("Advisor not found"));
        
        if (advisor.getRole() != UserRole.ADVISOR) {
            throw new RuntimeException("Only advisors can reject waivers");
        }
        
        waiver.setAdvisorApproved(false);
        waiver.setAdvisorApprovedBy(advisor);
        waiver.setAdvisorApprovedAt(LocalDateTime.now());
        if (note != null && !note.trim().isEmpty()) {
            waiver.setNote(note);
        }
        waiver.updateStatus();
        
        return waiverRepository.save(waiver);
    }
    
    /**
     * Check if there's an approved waiver for a student and class with specific corequisite
     * @param studentId Student ID
     * @param classId Class ID
     * @param corequisite Corequisite string
     * @return true if approved waiver exists, false otherwise
     */
    public boolean hasApprovedWaiver(Long studentId, Long classId, String corequisite) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        ClassEntity classEntity = classRepository.findById(classId)
            .orElseThrow(() -> new RuntimeException("Class not found"));
        
        Optional<CorequisiteWaiver> waiver = waiverRepository.findApprovedWaiver(student, classEntity, corequisite);
        return waiver.isPresent();
    }
    
    /**
     * Get all waivers for a student
     * @param studentId Student ID
     * @return List of waivers
     */
    public List<CorequisiteWaiver> getWaiversByStudent(Long studentId) {
        return waiverRepository.findByStudentId(studentId);
    }
    
    /**
     * Get all pending waivers
     * @return List of pending waivers
     */
    public List<CorequisiteWaiver> getPendingWaivers() {
        return waiverRepository.findPendingWaivers();
    }
    
    /**
     * Get waivers that need approval from a specific instructor
     * @param instructorId Instructor ID
     * @return List of waivers
     */
    public List<CorequisiteWaiver> getWaiversForInstructor(Long instructorId) {
        User instructor = userRepository.findById(instructorId)
            .orElseThrow(() -> new RuntimeException("Instructor not found"));
        
        if (instructor.getRole() != UserRole.INSTRUCTOR) {
            throw new RuntimeException("User is not an instructor");
        }
        
        String instructorName = instructor.getLastName();
        if (instructorName == null || instructorName.trim().isEmpty()) {
            return List.of();
        }
        
        return waiverRepository.findWaiversForInstructor(instructorName);
    }
    
    /**
     * Get waiver by ID
     * @param waiverId Waiver ID
     * @return Waiver
     */
    public CorequisiteWaiver getWaiverById(Long waiverId) {
        return waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
    }
}


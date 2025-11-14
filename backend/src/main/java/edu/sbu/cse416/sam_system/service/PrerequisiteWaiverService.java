package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.PrerequisiteWaiver;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.repository.PrerequisiteWaiverRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing prerequisite waivers.
 * 
 * Features:
 * - Create waiver requests
 * - Instructor approvals
 * - Advisor approvals
 * - Check if waiver exists and is approved
 * 
 * TP: Used Cursor to implement prerequisite waiver service
 */
@Service
@Transactional
public class PrerequisiteWaiverService {
    
    @Autowired
    private PrerequisiteWaiverRepository waiverRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ClassRepository classRepository;
    
    @Autowired
    private edu.sbu.cse416.sam_system.service.AuditLogService auditLogService;
    
    /**
     * Create a new waiver request
     * @param studentId Student requesting the waiver
     * @param classId Class ID for which prerequisite is being waived
     * @param prerequisite Prerequisite being waived (e.g., "CSE 114")
     * @param reason Optional reason for the request
     * @return Created waiver
     */
    public PrerequisiteWaiver createWaiver(Long studentId, Long classId, String prerequisite, String reason) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        
        if (student.getRole() != UserRole.STUDENT) {
            throw new RuntimeException("Only students can request waivers");
        }
        
        ClassEntity classEntity = classRepository.findById(classId)
            .orElseThrow(() -> new RuntimeException("Class not found"));
        
        // Check if waiver already exists
        Optional<PrerequisiteWaiver> existing = waiverRepository.findApprovedWaiver(student, classEntity, prerequisite);
        if (existing.isPresent()) {
            throw new RuntimeException("Approved waiver already exists for this prerequisite");
        }
        
        // Create new waiver
        PrerequisiteWaiver waiver = new PrerequisiteWaiver(student, classEntity, prerequisite);
        waiver.setReason(reason);
        waiver.setStatus(PrerequisiteWaiver.WaiverStatus.PENDING);
        
        waiver = waiverRepository.save(waiver);
        
        // Log audit entry for student-initiated waiver request
        String courseCode = classEntity.getDepartment() + " " + classEntity.getCourseNum();
        auditLogService.logPrerequisiteWaiverRequested(student, courseCode, prerequisite, reason);
        
        return waiver;
    }
    
    /**
     * Approve waiver by instructor
     * @param waiverId Waiver ID
     * @param instructorId Instructor ID approving the waiver
     * @param note Optional note from instructor
     * @return Updated waiver
     */
    public PrerequisiteWaiver approveByInstructor(Long waiverId, Long instructorId, String note) {
        PrerequisiteWaiver waiver = waiverRepository.findById(waiverId)
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
        if (waiver.getStatus() == PrerequisiteWaiver.WaiverStatus.APPROVED) {
            String courseCode = classEntity.getDepartment() + " " + classEntity.getCourseNum();
            auditLogService.logPrerequisiteWaived(waiver.getStudent(), instructor, courseCode + " (" + waiver.getPrerequisite() + ")", note);
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
    public PrerequisiteWaiver rejectByInstructor(Long waiverId, Long instructorId, String note) {
        PrerequisiteWaiver waiver = waiverRepository.findById(waiverId)
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
    public PrerequisiteWaiver approveByAdvisor(Long waiverId, Long advisorId, String note) {
        PrerequisiteWaiver waiver = waiverRepository.findById(waiverId)
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
        if (waiver.getStatus() == PrerequisiteWaiver.WaiverStatus.APPROVED) {
            String courseCode = waiver.getClassEntity().getDepartment() + " " + waiver.getClassEntity().getCourseNum();
            auditLogService.logPrerequisiteWaived(waiver.getStudent(), advisor, courseCode + " (" + waiver.getPrerequisite() + ")", note);
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
    public PrerequisiteWaiver rejectByAdvisor(Long waiverId, Long advisorId, String note) {
        PrerequisiteWaiver waiver = waiverRepository.findById(waiverId)
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
     * Check if there's an approved waiver for a student and class with specific prerequisite
     * @param studentId Student ID
     * @param classId Class ID
     * @param prerequisite Prerequisite string
     * @return true if approved waiver exists, false otherwise
     */
    public boolean hasApprovedWaiver(Long studentId, Long classId, String prerequisite) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        ClassEntity classEntity = classRepository.findById(classId)
            .orElseThrow(() -> new RuntimeException("Class not found"));
        
        Optional<PrerequisiteWaiver> waiver = waiverRepository.findApprovedWaiver(student, classEntity, prerequisite);
        return waiver.isPresent();
    }
    
    /**
     * Get all waivers for a student
     * @param studentId Student ID
     * @return List of waivers
     */
    public List<PrerequisiteWaiver> getWaiversByStudent(Long studentId) {
        return waiverRepository.findByStudentId(studentId);
    }
    
    /**
     * Get all pending waivers
     * @return List of pending waivers
     */
    public List<PrerequisiteWaiver> getPendingWaivers() {
        return waiverRepository.findPendingWaivers();
    }
    
    /**
     * Get waivers that need approval from a specific instructor
     * @param instructorId Instructor ID
     * @return List of waivers
     */
    public List<PrerequisiteWaiver> getWaiversForInstructor(Long instructorId) {
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
    public PrerequisiteWaiver getWaiverById(Long waiverId) {
        return waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
    }
}


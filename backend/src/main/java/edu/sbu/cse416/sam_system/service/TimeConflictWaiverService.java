package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.TimeConflictWaiver;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.repository.TimeConflictWaiverRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing time conflict waivers.
 * 
 * Features:
 * - Create waiver requests
 * - Instructor approvals (for enrolled class and conflicting class)
 * - Department-level advisor approvals
 * - Check if waiver exists and is approved
 * 
 * TP: Used Cursor to implement time conflict waiver service
 */
@Service
@Transactional
public class TimeConflictWaiverService {
    
    @Autowired
    private TimeConflictWaiverRepository waiverRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ClassRepository classRepository;
    
    @Autowired
    private edu.sbu.cse416.sam_system.service.AuditLogService auditLogService;
    
    /**
     * Create a new waiver request
     * @param studentId Student requesting the waiver
     * @param enrolledClassId Currently enrolled class ID
     * @param conflictingClassId Conflicting class ID to add
     * @param reason Optional reason for the request
     * @return Created waiver
     */
    public TimeConflictWaiver createWaiver(Long studentId, Long enrolledClassId, Long conflictingClassId, String reason) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        
        if (student.getRole() != UserRole.STUDENT) {
            throw new RuntimeException("Only students can request waivers");
        }
        
        ClassEntity enrolledClass = classRepository.findById(enrolledClassId)
            .orElseThrow(() -> new RuntimeException("Enrolled class not found"));
        
        ClassEntity conflictingClass = classRepository.findById(conflictingClassId)
            .orElseThrow(() -> new RuntimeException("Conflicting class not found"));
        
        // Check if student is actually enrolled in the enrolled class
        // This would require checking UserClasses or ClassRoster
        // For now, we'll assume the student is enrolled if they're requesting the waiver
        
        // Check if waiver already exists
        Optional<TimeConflictWaiver> existing = waiverRepository.findByStudentAndClasses(
            studentId, enrolledClassId, conflictingClassId);
        
        if (existing.isPresent()) {
            throw new RuntimeException("Waiver request already exists for these classes");
        }
        
        // Create new waiver
        TimeConflictWaiver waiver = new TimeConflictWaiver(student, enrolledClass, conflictingClass);
        waiver.setReason(reason);
        waiver.setStatus(TimeConflictWaiver.WaiverStatus.PENDING);
        
        waiver = waiverRepository.save(waiver);
        
        // Log audit entry for student-initiated waiver request
        String classACode = enrolledClass.getDepartment() + " " + enrolledClass.getCourseNum();
        String classBCode = conflictingClass.getDepartment() + " " + conflictingClass.getCourseNum();
        auditLogService.logTimeConflictWaiverRequested(student, classACode, classBCode, reason);
        
        return waiver;
    }
    
    /**
     * Approve waiver by enrolled class instructor
     * @param waiverId Waiver ID
     * @param instructorId Instructor ID approving the waiver
     * @return Updated waiver
     */
    public TimeConflictWaiver approveByEnrolledInstructor(Long waiverId, Long instructorId) {
        TimeConflictWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User instructor = userRepository.findById(instructorId)
            .orElseThrow(() -> new RuntimeException("Instructor not found"));
        
        if (instructor.getRole() != UserRole.INSTRUCTOR) {
            throw new RuntimeException("Only instructors can approve waivers");
        }
        
        // Explicitly load the enrolled class to avoid lazy loading issues
        ClassEntity enrolledClass = waiver.getEnrolledClass();
        if (enrolledClass == null) {
            throw new RuntimeException("Enrolled class not found");
        }
        
        // Force load instructor field
        String classInstructor = enrolledClass.getInstructor();
        if (classInstructor == null || instructor.getLastName() == null || 
            !classInstructor.toLowerCase().contains(instructor.getLastName().toLowerCase())) {
            throw new RuntimeException("Instructor is not assigned to this class");
        }
        
        waiver.setEnrolledInstructorApproved(true);
        waiver.setEnrolledInstructorApprovedBy(instructor);
        waiver.setEnrolledInstructorApprovedAt(LocalDateTime.now());
        waiver.updateStatus();
        
        return waiverRepository.save(waiver);
    }
    
    /**
     * Approve waiver by conflicting class instructor
     * @param waiverId Waiver ID
     * @param instructorId Instructor ID approving the waiver
     * @return Updated waiver
     */
    public TimeConflictWaiver approveByConflictingInstructor(Long waiverId, Long instructorId) {
        TimeConflictWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User instructor = userRepository.findById(instructorId)
            .orElseThrow(() -> new RuntimeException("Instructor not found"));
        
        if (instructor.getRole() != UserRole.INSTRUCTOR) {
            throw new RuntimeException("Only instructors can approve waivers");
        }
        
        // Explicitly load the conflicting class to avoid lazy loading issues
        ClassEntity conflictingClass = waiver.getConflictingClass();
        if (conflictingClass == null) {
            throw new RuntimeException("Conflicting class not found");
        }
        
        // Force load instructor field
        String classInstructor = conflictingClass.getInstructor();
        if (classInstructor == null || instructor.getLastName() == null || 
            !classInstructor.toLowerCase().contains(instructor.getLastName().toLowerCase())) {
            throw new RuntimeException("Instructor is not assigned to this class");
        }
        
        waiver.setConflictingInstructorApproved(true);
        waiver.setConflictingInstructorApprovedBy(instructor);
        waiver.setConflictingInstructorApprovedAt(LocalDateTime.now());
        waiver.updateStatus();
        
        return waiverRepository.save(waiver);
    }
    
    /**
     * Reject waiver by enrolled class instructor
     * @param waiverId Waiver ID
     * @param instructorId Instructor ID rejecting the waiver
     * @return Updated waiver
     */
    public TimeConflictWaiver rejectByEnrolledInstructor(Long waiverId, Long instructorId) {
        TimeConflictWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User instructor = userRepository.findById(instructorId)
            .orElseThrow(() -> new RuntimeException("Instructor not found"));
        
        if (instructor.getRole() != UserRole.INSTRUCTOR) {
            throw new RuntimeException("Only instructors can reject waivers");
        }
        
        // Explicitly load the enrolled class to avoid lazy loading issues
        ClassEntity enrolledClass = waiver.getEnrolledClass();
        if (enrolledClass == null) {
            throw new RuntimeException("Enrolled class not found");
        }
        
        // Force load instructor field
        String classInstructor = enrolledClass.getInstructor();
        if (classInstructor == null || instructor.getLastName() == null || 
            !classInstructor.toLowerCase().contains(instructor.getLastName().toLowerCase())) {
            throw new RuntimeException("Instructor is not assigned to this class");
        }
        
        waiver.setEnrolledInstructorApproved(false);
        waiver.setEnrolledInstructorApprovedBy(instructor);
        waiver.setEnrolledInstructorApprovedAt(LocalDateTime.now());
        waiver.updateStatus();
        
        return waiverRepository.save(waiver);
    }
    
    /**
     * Reject waiver by conflicting class instructor
     * @param waiverId Waiver ID
     * @param instructorId Instructor ID rejecting the waiver
     * @return Updated waiver
     */
    public TimeConflictWaiver rejectByConflictingInstructor(Long waiverId, Long instructorId) {
        TimeConflictWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User instructor = userRepository.findById(instructorId)
            .orElseThrow(() -> new RuntimeException("Instructor not found"));
        
        if (instructor.getRole() != UserRole.INSTRUCTOR) {
            throw new RuntimeException("Only instructors can reject waivers");
        }
        
        // Explicitly load the conflicting class to avoid lazy loading issues
        ClassEntity conflictingClass = waiver.getConflictingClass();
        if (conflictingClass == null) {
            throw new RuntimeException("Conflicting class not found");
        }
        
        // Force load instructor field
        String classInstructor = conflictingClass.getInstructor();
        if (classInstructor == null || instructor.getLastName() == null || 
            !classInstructor.toLowerCase().contains(instructor.getLastName().toLowerCase())) {
            throw new RuntimeException("Instructor is not assigned to this class");
        }
        
        waiver.setConflictingInstructorApproved(false);
        waiver.setConflictingInstructorApprovedBy(instructor);
        waiver.setConflictingInstructorApprovedAt(LocalDateTime.now());
        waiver.updateStatus();
        
        return waiverRepository.save(waiver);
    }
    
    /**
     * Approve waiver by department-level advisor
     * Only department-level advisors can approve waivers
     * @param waiverId Waiver ID
     * @param advisorId Advisor ID approving the waiver
     * @return Updated waiver
     */
    public TimeConflictWaiver approveByAdvisor(Long waiverId, Long advisorId) {
        TimeConflictWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User advisor = userRepository.findById(advisorId)
            .orElseThrow(() -> new RuntimeException("Advisor not found"));
        
        if (advisor.getRole() != UserRole.ADVISOR) {
            throw new RuntimeException("Only advisors can approve waivers");
        }
        
        // Verify advisor is department-level
        String advisorLevel = advisor.getLevel();
        if (advisorLevel == null || !"department".equalsIgnoreCase(advisorLevel)) {
            throw new RuntimeException("Only department-level advisors can approve waivers");
        }
        
        // Verify advisor's academic unit matches one of the classes' departments
        String advisorAcademicUnit = advisor.getAcademicUnit();
        if (advisorAcademicUnit == null || advisorAcademicUnit.trim().isEmpty()) {
            throw new RuntimeException("Advisor must have an academic unit assigned");
        }
        
        String enrolledDept = waiver.getEnrolledClass().getDepartment();
        String conflictingDept = waiver.getConflictingClass().getDepartment();
        
        // Parse academicUnit - it can be comma-separated like "CSE,POL,AAS"
        String[] academicUnits = advisorAcademicUnit.split(",");
        boolean matchesEnrolled = false;
        boolean matchesConflicting = false;
        
        for (String unit : academicUnits) {
            String trimmedUnit = unit.trim();
            if (trimmedUnit.equalsIgnoreCase(enrolledDept)) {
                matchesEnrolled = true;
            }
            if (trimmedUnit.equalsIgnoreCase(conflictingDept)) {
                matchesConflicting = true;
            }
        }
        
        if (!matchesEnrolled && !matchesConflicting) {
            throw new RuntimeException(String.format(
                "Advisor's department(s) (%s) must match one of the classes' departments. " +
                "Enrolled class: %s %s (department: %s), Conflicting class: %s %s (department: %s)",
                advisorAcademicUnit, 
                enrolledDept, waiver.getEnrolledClass().getCourseNum(), enrolledDept,
                conflictingDept, waiver.getConflictingClass().getCourseNum(), conflictingDept));
        }
        
        waiver.setAdvisorApproved(true);
        waiver.setAdvisorApprovedBy(advisor);
        waiver.setAdvisorApprovedAt(LocalDateTime.now());
        waiver.updateStatus();
        
        waiver = waiverRepository.save(waiver);
        
        // Log audit entry if waiver is fully approved
        if (waiver.getStatus() == TimeConflictWaiver.WaiverStatus.APPROVED) {
            String classA = waiver.getEnrolledClass().getDepartment() + " " + waiver.getEnrolledClass().getCourseNum();
            String classB = waiver.getConflictingClass().getDepartment() + " " + waiver.getConflictingClass().getCourseNum();
            auditLogService.logTimeConflictWaived(waiver.getStudent(), advisor, classA, classB, null);
        }
        
        return waiver;
    }
    
    /**
     * Reject waiver by department-level advisor
     * @param waiverId Waiver ID
     * @param advisorId Advisor ID rejecting the waiver
     * @return Updated waiver
     */
    public TimeConflictWaiver rejectByAdvisor(Long waiverId, Long advisorId) {
        TimeConflictWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User advisor = userRepository.findById(advisorId)
            .orElseThrow(() -> new RuntimeException("Advisor not found"));
        
        if (advisor.getRole() != UserRole.ADVISOR) {
            throw new RuntimeException("Only advisors can reject waivers");
        }
        
        // Verify advisor is department-level
        String advisorLevel = advisor.getLevel();
        if (advisorLevel == null || !"department".equalsIgnoreCase(advisorLevel)) {
            throw new RuntimeException("Only department-level advisors can reject waivers");
        }
        
        // Verify advisor's academic unit matches one of the classes' departments
        String advisorAcademicUnit = advisor.getAcademicUnit();
        if (advisorAcademicUnit == null || advisorAcademicUnit.trim().isEmpty()) {
            throw new RuntimeException("Advisor must have an academic unit assigned");
        }
        
        String enrolledDept = waiver.getEnrolledClass().getDepartment();
        String conflictingDept = waiver.getConflictingClass().getDepartment();
        
        // Parse academicUnit - it can be comma-separated like "CSE,POL,AAS"
        String[] academicUnits = advisorAcademicUnit.split(",");
        boolean matchesEnrolled = false;
        boolean matchesConflicting = false;
        
        for (String unit : academicUnits) {
            String trimmedUnit = unit.trim();
            if (trimmedUnit.equalsIgnoreCase(enrolledDept)) {
                matchesEnrolled = true;
            }
            if (trimmedUnit.equalsIgnoreCase(conflictingDept)) {
                matchesConflicting = true;
            }
        }
        
        if (!matchesEnrolled && !matchesConflicting) {
            throw new RuntimeException(String.format(
                "Advisor's department(s) (%s) must match one of the classes' departments. " +
                "Enrolled class: %s %s (department: %s), Conflicting class: %s %s (department: %s)",
                advisorAcademicUnit, 
                enrolledDept, waiver.getEnrolledClass().getCourseNum(), enrolledDept,
                conflictingDept, waiver.getConflictingClass().getCourseNum(), conflictingDept));
        }
        
        waiver.setAdvisorApproved(false);
        waiver.setAdvisorApprovedBy(advisor);
        waiver.setAdvisorApprovedAt(LocalDateTime.now());
        waiver.updateStatus();
        
        return waiverRepository.save(waiver);
    }
    
    /**
     * Check if there's an approved waiver for a student and two classes
     * @param studentId Student ID
     * @param classId1 First class ID
     * @param classId2 Second class ID
     * @return true if approved waiver exists, false otherwise
     */
    public boolean hasApprovedWaiver(Long studentId, Long classId1, Long classId2) {
        Optional<TimeConflictWaiver> waiver = waiverRepository.findApprovedWaiver(studentId, classId1, classId2);
        return waiver.isPresent();
    }
    
    /**
     * Get all waivers for a student
     * @param studentId Student ID
     * @return List of waivers
     */
    public List<TimeConflictWaiver> getWaiversByStudent(Long studentId) {
        return waiverRepository.findByStudentId(studentId);
    }
    
    /**
     * Get all pending waivers
     * @return List of pending waivers
     */
    public List<TimeConflictWaiver> getPendingWaivers() {
        return waiverRepository.findPendingWaivers();
    }
    
    /**
     * Get waivers that need approval from a specific instructor
     * @param instructorId Instructor ID
     * @return List of waivers
     */
    public List<TimeConflictWaiver> getWaiversForInstructor(Long instructorId) {
        User instructor = userRepository.findById(instructorId)
            .orElseThrow(() -> new RuntimeException("Instructor not found"));
        
        if (instructor.getRole() != UserRole.INSTRUCTOR) {
            throw new RuntimeException("User is not an instructor");
        }
        
        // Get instructor's last name for matching
        String instructorName = instructor.getLastName();
        if (instructorName == null || instructorName.trim().isEmpty()) {
            return List.of();
        }
        
        return waiverRepository.findWaiversForInstructor(instructorName);
    }
    
    /**
     * Get waivers that need approval from a department-level advisor
     * @param advisorId Advisor ID
     * @return List of waivers
     */
    public List<TimeConflictWaiver> getWaiversForAdvisor(Long advisorId) {
        User advisor = userRepository.findById(advisorId)
            .orElseThrow(() -> new RuntimeException("Advisor not found"));
        
        if (advisor.getRole() != UserRole.ADVISOR) {
            throw new RuntimeException("User is not an advisor");
        }
        
        String advisorLevel = advisor.getLevel();
        if (advisorLevel == null || !"department".equalsIgnoreCase(advisorLevel)) {
            throw new RuntimeException("Only department-level advisors can view waivers");
        }
        
        String advisorAcademicUnit = advisor.getAcademicUnit();
        if (advisorAcademicUnit == null) {
            return List.of();
        }
        
        return waiverRepository.findWaiversForDepartment(advisorAcademicUnit);
    }
    
    /**
     * Get waiver by ID
     * @param waiverId Waiver ID
     * @return Waiver
     */
    public TimeConflictWaiver getWaiverById(Long waiverId) {
        return waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
    }
}


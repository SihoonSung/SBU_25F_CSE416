package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.RequisiteWaiver;
import edu.sbu.cse416.sam_system.entity.RequisiteWaiver.RequisiteType;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.ClassEntity;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.repository.RequisiteWaiverRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.repository.ClassRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing requisite waivers.
 * 
 * Features:
 * - Create waiver requests
 * - Advisor approvals
 * - Registrar approvals
 * - Check if waiver exists and is approved
 * 
 * TP: Used Cursor to implement requisite waiver service
 */
@Service
@Transactional
public class RequisiteWaiverService {
    
    @Autowired
    private RequisiteWaiverRepository waiverRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ClassRepository classRepository;
    
    @Autowired
    private AuditLogService auditLogService;
    
    /**
     * Create a new waiver request
     * @param studentId Student requesting the waiver
     * @param classId Class ID for which the requisite is being waived
     * @param requisiteType Type of requisite (PREREQUISITE, ANTIREQUISITE, COREQUISITE)
     * @param reason Optional reason for the request
     * @return Created waiver
     */
    public RequisiteWaiver createWaiver(Long studentId, Long classId, RequisiteType requisiteType, String reason) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        
        if (student.getRole() != UserRole.STUDENT) {
            throw new RuntimeException("Only students can request waivers");
        }
        
        ClassEntity classEntity = classRepository.findById(classId)
            .orElseThrow(() -> new RuntimeException("Class not found"));
        
        // Check if waiver already exists
        Optional<RequisiteWaiver> existing = waiverRepository.findByStudentAndClassAndType(
            studentId, classId, requisiteType);
        
        if (existing.isPresent()) {
            throw new RuntimeException("Waiver request already exists for this class and requisite type");
        }
        
        // Create new waiver
        RequisiteWaiver waiver = new RequisiteWaiver(student, classEntity, requisiteType);
        waiver.setReason(reason);
        waiver.setStatus(RequisiteWaiver.WaiverStatus.PENDING);
        
        waiver = waiverRepository.save(waiver);
        
        // Log audit entry for student-initiated waiver request
        String courseCode = classEntity.getDepartment() + " " + classEntity.getCourseNum();
        auditLogService.logRequisiteWaiverRequested(student, courseCode, requisiteType.toString(), reason != null ? reason : "");
        
        return waiver;
    }
    
    /**
     * Approve waiver by advisor
     * @param waiverId Waiver ID
     * @param advisorId Advisor ID approving the waiver
     * @return Updated waiver
     */
    public RequisiteWaiver approveByAdvisor(Long waiverId, Long advisorId) {
        RequisiteWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User advisor = userRepository.findById(advisorId)
            .orElseThrow(() -> new RuntimeException("Advisor not found"));
        
        if (advisor.getRole() != UserRole.ADVISOR) {
            throw new RuntimeException("Only advisors can approve waivers");
        }
        
        // Verify advisor's academic unit matches the class's department
        String advisorAcademicUnit = advisor.getAcademicUnit();
        if (advisorAcademicUnit == null || advisorAcademicUnit.trim().isEmpty()) {
            throw new RuntimeException("Advisor must have an academic unit assigned");
        }
        
        String classDept = waiver.getClassEntity().getDepartment();
        
        // Parse academicUnit - it can be comma-separated like "CSE,POL,AAS"
        String[] academicUnits = advisorAcademicUnit.split(",");
        boolean matches = false;
        
        for (String unit : academicUnits) {
            String trimmedUnit = unit.trim();
            if (trimmedUnit.equalsIgnoreCase(classDept)) {
                matches = true;
                break;
            }
        }
        
        if (!matches) {
            throw new RuntimeException(String.format(
                "Advisor's department(s) (%s) must match the class's department (%s)",
                advisorAcademicUnit, classDept));
        }
        
        waiver.setAdvisorApproved(true);
        waiver.setAdvisorApprovedBy(advisor);
        waiver.setAdvisorApprovedAt(LocalDateTime.now());
        waiver.updateStatus();
        
        waiver = waiverRepository.save(waiver);
        
        // Log audit entry if waiver is fully approved
        if (waiver.getStatus() == RequisiteWaiver.WaiverStatus.APPROVED) {
            String courseCode = waiver.getClassEntity().getDepartment() + " " + waiver.getClassEntity().getCourseNum();
            // Use advisor (current approver) as performingUser
            auditLogService.logRequisiteWaived(waiver.getStudent(), advisor, courseCode, 
                waiver.getRequisiteType().toString(), null);
        }
        
        return waiver;
    }
    
    /**
     * Reject waiver by advisor
     * @param waiverId Waiver ID
     * @param advisorId Advisor ID rejecting the waiver
     * @return Updated waiver
     */
    public RequisiteWaiver rejectByAdvisor(Long waiverId, Long advisorId) {
        RequisiteWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User advisor = userRepository.findById(advisorId)
            .orElseThrow(() -> new RuntimeException("Advisor not found"));
        
        if (advisor.getRole() != UserRole.ADVISOR) {
            throw new RuntimeException("Only advisors can reject waivers");
        }
        
        // Verify advisor's academic unit matches the class's department
        String advisorAcademicUnit = advisor.getAcademicUnit();
        if (advisorAcademicUnit == null || advisorAcademicUnit.trim().isEmpty()) {
            throw new RuntimeException("Advisor must have an academic unit assigned");
        }
        
        String classDept = waiver.getClassEntity().getDepartment();
        
        // Parse academicUnit - it can be comma-separated like "CSE,POL,AAS"
        String[] academicUnits = advisorAcademicUnit.split(",");
        boolean matches = false;
        
        for (String unit : academicUnits) {
            String trimmedUnit = unit.trim();
            if (trimmedUnit.equalsIgnoreCase(classDept)) {
                matches = true;
                break;
            }
        }
        
        if (!matches) {
            throw new RuntimeException(String.format(
                "Advisor's department(s) (%s) must match the class's department (%s)",
                advisorAcademicUnit, classDept));
        }
        
        waiver.setAdvisorApproved(false);
        waiver.setAdvisorApprovedBy(advisor);
        waiver.setAdvisorApprovedAt(LocalDateTime.now());
        waiver.updateStatus();
        
        return waiverRepository.save(waiver);
    }
    
    /**
     * Approve waiver by registrar
     * @param waiverId Waiver ID
     * @param registrarId Registrar ID approving the waiver
     * @return Updated waiver
     */
    public RequisiteWaiver approveByRegistrar(Long waiverId, Long registrarId) {
        RequisiteWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User registrar = userRepository.findById(registrarId)
            .orElseThrow(() -> new RuntimeException("Registrar not found"));
        
        if (registrar.getRole() != UserRole.REGISTRAR) {
            throw new RuntimeException("Only registrars can approve waivers");
        }
        
        waiver.setRegistrarApproved(true);
        waiver.setRegistrarApprovedBy(registrar);
        waiver.setRegistrarApprovedAt(LocalDateTime.now());
        waiver.updateStatus();
        
        waiver = waiverRepository.save(waiver);
        
        // Log audit entry if waiver is fully approved
        if (waiver.getStatus() == RequisiteWaiver.WaiverStatus.APPROVED) {
            String courseCode = waiver.getClassEntity().getDepartment() + " " + waiver.getClassEntity().getCourseNum();
            // Use registrar (current approver) as performingUser
            auditLogService.logRequisiteWaived(waiver.getStudent(), registrar, courseCode, 
                waiver.getRequisiteType().toString(), null);
        }
        
        return waiver;
    }
    
    /**
     * Reject waiver by registrar
     * @param waiverId Waiver ID
     * @param registrarId Registrar ID rejecting the waiver
     * @return Updated waiver
     */
    public RequisiteWaiver rejectByRegistrar(Long waiverId, Long registrarId) {
        RequisiteWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        
        User registrar = userRepository.findById(registrarId)
            .orElseThrow(() -> new RuntimeException("Registrar not found"));
        
        if (registrar.getRole() != UserRole.REGISTRAR) {
            throw new RuntimeException("Only registrars can reject waivers");
        }
        
        waiver.setRegistrarApproved(false);
        waiver.setRegistrarApprovedBy(registrar);
        waiver.setRegistrarApprovedAt(LocalDateTime.now());
        waiver.updateStatus();
        
        return waiverRepository.save(waiver);
    }
    
    /**
     * Check if there's an approved waiver for a student, class, and requisite type
     * @param studentId Student ID
     * @param classId Class ID
     * @param requisiteType Requisite type
     * @return true if approved waiver exists, false otherwise
     */
    public boolean hasApprovedWaiver(Long studentId, Long classId, RequisiteType requisiteType) {
        Optional<RequisiteWaiver> waiver = waiverRepository.findApprovedWaiver(studentId, classId, requisiteType);
        return waiver.isPresent();
    }
    
    /**
     * Get all waivers for a student
     * @param studentId Student ID
     * @return List of waivers
     */
    public List<RequisiteWaiver> getWaiversByStudent(Long studentId) {
        return waiverRepository.findByStudentId(studentId);
    }
    
    /**
     * Get all pending waivers
     * @return List of pending waivers
     */
    public List<RequisiteWaiver> getPendingWaivers() {
        return waiverRepository.findPendingWaivers();
    }
    
    /**
     * Get waivers that need approval from a department-level advisor
     * @param advisorId Advisor ID
     * @return List of waivers
     */
    public List<RequisiteWaiver> getWaiversForAdvisor(Long advisorId) {
        User advisor = userRepository.findById(advisorId)
            .orElseThrow(() -> new RuntimeException("Advisor not found"));
        
        if (advisor.getRole() != UserRole.ADVISOR) {
            throw new RuntimeException("User is not an advisor");
        }
        
        String advisorAcademicUnit = advisor.getAcademicUnit();
        if (advisorAcademicUnit == null || advisorAcademicUnit.trim().isEmpty()) {
            return List.of();
        }
        
        // Get waivers for all departments the advisor handles
        List<RequisiteWaiver> allWaivers = List.of();
        String[] academicUnits = advisorAcademicUnit.split(",");
        
        for (String unit : academicUnits) {
            String trimmedUnit = unit.trim();
            List<RequisiteWaiver> waivers = waiverRepository.findWaiversForAdvisor(trimmedUnit);
            if (allWaivers.isEmpty()) {
                allWaivers = waivers;
            } else {
                // Combine lists (avoid duplicates)
                for (RequisiteWaiver w : waivers) {
                    if (!allWaivers.contains(w)) {
                        allWaivers = new java.util.ArrayList<>(allWaivers);
                        allWaivers.add(w);
                    }
                }
            }
        }
        
        return allWaivers;
    }
    
    /**
     * Get waivers that need approval from registrar
     * @return List of waivers
     */
    public List<RequisiteWaiver> getWaiversForRegistrar() {
        return waiverRepository.findWaiversForRegistrar();
    }
    
    /**
     * Get waiver by ID
     * @param waiverId Waiver ID
     * @return Waiver
     */
    public RequisiteWaiver getWaiverById(Long waiverId) {
        return waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
    }
    
    /**
     * Delete a waiver
     * @param waiverId Waiver ID
     */
    public void deleteWaiver(Long waiverId) {
        RequisiteWaiver waiver = waiverRepository.findById(waiverId)
            .orElseThrow(() -> new RuntimeException("Waiver not found"));
        waiverRepository.delete(waiver);
    }
}


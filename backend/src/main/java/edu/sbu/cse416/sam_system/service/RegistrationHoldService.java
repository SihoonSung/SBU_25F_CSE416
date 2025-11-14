package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.RegistrationHold;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.repository.RegistrationHoldRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TP: Used Cursor to implement registration hold service
 * Service for managing registration holds.
 * 
 * Features:
 * - Place and remove registration holds
 * - Verify advisor permissions based on level and academic unit
 * - Check if student has active holds
 * 
 * 
 */
@Service
@Transactional
public class RegistrationHoldService {
    
    @Autowired
    private RegistrationHoldRepository holdRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private edu.sbu.cse416.sam_system.service.AuditLogService auditLogService;
    
    /**
     * Place a registration hold on a student
     * @param studentId Student ID
     * @param holdType Hold type (ACADEMIC_ADVISING or FINANCIAL)
     * @param placedById User ID who is placing the hold
     * @param reason Reason for the hold
     * @param note Optional note for the audit log
     * @return Created RegistrationHold, or null if unauthorized
     */
    public RegistrationHold placeHold(Long studentId, RegistrationHold.HoldType holdType, 
                                     Long placedById, String reason, String note) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        
        User placedBy = userRepository.findById(placedById)
            .orElseThrow(() -> new RuntimeException("User placing hold not found"));
        
        // Verify permissions
        if (!canPlaceHold(placedBy, holdType, student)) {
            throw new RuntimeException("User does not have permission to place this type of hold");
        }
        
        // Create new hold
        RegistrationHold hold = new RegistrationHold(student, holdType, placedBy, reason);
        hold = holdRepository.save(hold);
        
        // Log audit entry
        auditLogService.logHoldPlaced(student, placedBy, holdType.name(), reason, note);
        
        return hold;
    }
    
    /**
     * Remove a registration hold
     * @param holdId Hold ID
     * @param removedById User ID who is removing the hold
     * @param note Optional note for the audit log
     * @return Removed RegistrationHold, or null if not found or unauthorized
     */
    public RegistrationHold removeHold(Long holdId, Long removedById, String note) {
        RegistrationHold hold = holdRepository.findById(holdId)
            .orElseThrow(() -> new RuntimeException("Hold not found"));
        
        User removedBy = userRepository.findById(removedById)
            .orElseThrow(() -> new RuntimeException("User removing hold not found"));
        
        // Verify permissions
        if (!canRemoveHold(removedBy, hold, hold.getStudent())) {
            throw new RuntimeException("User does not have permission to remove this hold");
        }
        
        // Log audit entry before deletion
        auditLogService.logHoldRemoved(hold.getStudent(), removedBy, hold.getHoldType().name(), note);
        
        // Physically delete the hold record instead of soft-deleting
        holdRepository.delete(hold);
        return hold; // return the deleted entity snapshot for controller response
    }
    
    /**
     * Check if student has any active registration holds
     * @param studentId Student ID
     * @return true if student has active holds, false otherwise
     */
    public boolean hasActiveHolds(Long studentId) {
        return holdRepository.hasActiveHolds(studentId);
    }
    
    /**
     * Get all active holds for a student
     * @param studentId Student ID
     * @return List of active registration holds
     */
    public List<RegistrationHold> getActiveHolds(Long studentId) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        return holdRepository.findActiveHoldsByStudent(student);
    }
    
    /**
     * Get all holds (active and inactive) for a student
     * @param studentId Student ID
     * @return List of all registration holds
     */
    public List<RegistrationHold> getAllHolds(Long studentId) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        return holdRepository.findAllHoldsByStudent(student);
    }
    
    /**
     * Check if a user can place a hold
     * @param user User who wants to place the hold
     * @param holdType Type of hold
     * @param student Student on whom the hold will be placed
     * @return true if user has permission, false otherwise
     */
    private boolean canPlaceHold(User user, RegistrationHold.HoldType holdType, User student) {
        if (holdType == RegistrationHold.HoldType.FINANCIAL) {
            // Financial holds can only be placed by REGISTRAR
            return user.getRole() == UserRole.REGISTRAR;
        } else if (holdType == RegistrationHold.HoldType.ACADEMIC_ADVISING) {
            // Academic advising holds can be placed by advisors with appropriate permissions
            if (user.getRole() != UserRole.ADVISOR) {
                return false;
            }
            
            String advisorLevel = user.getLevel();
            String advisorAcademicUnit = user.getAcademicUnit();
            
            // University-level advisor can place holds on any student
            if ("university".equalsIgnoreCase(advisorLevel)) {
                return true;
            }
            
            // College-level advisor can place holds on students in the same college
            // Level can be "college" or a college acronym (e.g., "CEAS", "CAS")
            if ("college".equalsIgnoreCase(advisorLevel) || 
                (advisorLevel != null && !"university".equalsIgnoreCase(advisorLevel) && 
                 !"department".equalsIgnoreCase(advisorLevel))) {
                // If advisor level is a college acronym, check if student's college matches
                if (advisorLevel != null && !"college".equalsIgnoreCase(advisorLevel)) {
                    if (student.getCollege() != null && advisorLevel.equalsIgnoreCase(student.getCollege())) {
                        return true;
                    }
                }
                
                // Check if advisor's academic_unit contains student's college
                if (advisorAcademicUnit != null && student.getCollege() != null) {
                    String[] academicUnits = advisorAcademicUnit.split(",");
                    for (String unit : academicUnits) {
                        if (unit.trim().equalsIgnoreCase(student.getCollege())) {
                            return true;
                        }
                    }
                }
                
                // If student's college is not set, check if student's major matches advisor's academic_unit
                // (This handles cases where advisor's academic_unit contains major codes)
                if (advisorAcademicUnit != null) {
                    String[] academicUnits = advisorAcademicUnit.split(",");
                    
                    // Check student's majors (JSON array)
                    String studentMajors = student.getMajors();
                    if (studentMajors != null) {
                        String normalizedMajors = studentMajors.replaceAll("[\\[\\]\"\\s]", "");
                        String[] majors = normalizedMajors.split(",");
                        for (String major : majors) {
                            for (String advisorUnit : academicUnits) {
                                if (advisorUnit.trim().equalsIgnoreCase(major.trim())) {
                                    return true;
                                }
                            }
                        }
                    }
                    
                    // Also check single major field
                    if (student.getMajor() != null) {
                        for (String advisorUnit : academicUnits) {
                            if (advisorUnit.trim().equalsIgnoreCase(student.getMajor())) {
                                return true;
                            }
                        }
                    }
                }
                
                // Also check direct match
                return advisorAcademicUnit != null && 
                       advisorAcademicUnit.equalsIgnoreCase(student.getCollege());
            }
            
            // Department-level advisor can place holds on students with matching major
            if ("department".equalsIgnoreCase(advisorLevel)) {
                if (advisorAcademicUnit == null) {
                    return false;
                }
                
                // Parse comma-separated academic units
                String[] advisorUnits = advisorAcademicUnit.split(",");
                
                // Check if student has this department as a major
                String studentMajors = student.getMajors(); // JSON array
                if (studentMajors != null) {
                    // Parse JSON array (simple parsing - assumes format like ["CSE","AMS"])
                    String normalizedMajors = studentMajors.replaceAll("[\\[\\]\"\\s]", "");
                    String[] majors = normalizedMajors.split(",");
                    for (String major : majors) {
                        for (String advisorUnit : advisorUnits) {
                            if (advisorUnit.trim().equalsIgnoreCase(major.trim())) {
                                return true;
                            }
                        }
                    }
                }
                
                // Also check single major field
                for (String advisorUnit : advisorUnits) {
                    if (advisorUnit.trim().equalsIgnoreCase(student.getMajor())) {
                        return true;
                    }
                }
            }
            
            return false;
        }
        
        return false;
    }
    
    /**
     * Check if a user can remove a hold
     * @param user User who wants to remove the hold
     * @param hold The hold to be removed
     * @param student Student on whom the hold was placed
     * @return true if user has permission, false otherwise
     */
    private boolean canRemoveHold(User user, RegistrationHold hold, User student) {
        // Same permissions as placing a hold
        return canPlaceHold(user, hold.getHoldType(), student);
    }
}


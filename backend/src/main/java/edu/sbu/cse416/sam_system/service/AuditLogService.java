package edu.sbu.cse416.sam_system.service;

import edu.sbu.cse416.sam_system.entity.AuditLog;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.repository.AuditLogRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing audit log entries.
 * Handles logging of all student profile changes and provides methods to retrieve logs
 * with appropriate privacy filtering (registrar actions shown as "registrar" to students/advisors).
 * 
 * TP: Used Cursor to implement audit log service
 */
@Service
@Transactional
public class AuditLogService {
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Create a new audit log entry
     * @param student Student whose profile was changed
     * @param performingUser User who performed the action
     * @param action Type of action performed
     * @param details Details of the change (can be JSON string)
     * @param note Optional note from registrar or advisor
     * @return Created audit log entry
     */
    public AuditLog logAction(User student, User performingUser, AuditLog.ActionType action, 
                              String details, String note) {
        AuditLog log = new AuditLog(student, performingUser, action, details);
        if (note != null && !note.trim().isEmpty()) {
            log.setNote(note);
        }
        return auditLogRepository.save(log);
    }
    
    /**
     * Create a new audit log entry without a note
     */
    public AuditLog logAction(User student, User performingUser, AuditLog.ActionType action, String details) {
        return logAction(student, performingUser, action, details, null);
    }
    
    /**
     * Get audit log entries for a student, with privacy filtering based on viewer role
     * @param studentId Student ID
     * @param viewerRole Role of the user viewing the log (null for public/unauthenticated)
     * @return List of audit log entries as maps, with privacy filtering applied
     */
    public List<Map<String, Object>> getAuditLogForStudent(Long studentId, UserRole viewerRole) {
        User student = new User();
        student.setUserId(studentId);
        List<AuditLog> logs = auditLogRepository.findByStudentIdOrderByTimestampDesc(studentId);
        
        return logs.stream()
            .map(log -> convertToMap(log, viewerRole))
            .collect(Collectors.toList());
    }
    
    /**
     * Get audit log entries for a student by student ID string
     * @param studentIdString Student ID as string
     * @param viewerRole Role of the user viewing the log
     * @return List of audit log entries as maps
     */
    public List<Map<String, Object>> getAuditLogForStudent(String studentIdString, UserRole viewerRole) {
        try {
            // Find student by studentId string
            Optional<User> studentOpt = userRepository.findByStudentId(studentIdString);
            if (studentOpt.isEmpty()) {
                return List.of();
            }
            
            User student = studentOpt.get();
            List<AuditLog> logs = auditLogRepository.findByStudentOrderByTimestampDesc(student);
            
            return logs.stream()
                .map(log -> convertToMap(log, viewerRole))
                .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }
    
    /**
     * Convert AuditLog entity to Map for API response, applying privacy filtering
     * @param log Audit log entry
     * @param viewerRole Role of the user viewing the log
     * @return Map representation of the log entry
     */
    private Map<String, Object> convertToMap(AuditLog log, UserRole viewerRole) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", log.getId());
        map.put("timestamp", log.getTimestamp().toString());
        map.put("action", log.getAction().name());
        
        // Parse details JSON string to object for frontend convenience
        Object detailsObj = parseDetails(log.getDetails());
        map.put("details", detailsObj);
        
        if (log.getNote() != null && !log.getNote().trim().isEmpty()) {
            map.put("note", log.getNote());
        }
        
        // Apply privacy filtering: if viewer is STUDENT or ADVISOR, and performing user is REGISTRAR,
        // show "registrar" instead of actual user name
        if (viewerRole == UserRole.STUDENT || viewerRole == UserRole.ADVISOR) {
            if (log.getPerformingUser() != null && log.getPerformingUser().getRole() == UserRole.REGISTRAR) {
                map.put("actorRole", "REGISTRAR");
                map.put("actorDisplay", "registrar");
            } else {
                map.put("actorRole", log.getActorRole() != null ? log.getActorRole().name() : null);
                map.put("actorDisplay", log.getActorDisplay());
            }
        } else {
            // REGISTRAR or other roles see full information
            map.put("actorRole", log.getActorRole() != null ? log.getActorRole().name() : null);
            map.put("actorDisplay", log.getActorDisplay());
        }
        
        return map;
    }
    
    /**
     * Parse details JSON string to object
     * If parsing fails, return the original string
     * @param detailsJson JSON string from database
     * @return Parsed object or original string if parsing fails
     */
    private Object parseDetails(String detailsJson) {
        if (detailsJson == null || detailsJson.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Try to parse as JSON object
            return objectMapper.readValue(detailsJson, Object.class);
        } catch (Exception e) {
            // If parsing fails, return original string
            return detailsJson;
        }
    }
    
    /**
     * Update note for an audit log entry
     * Only registrar and advisor can update notes
     * @param logId Audit log entry ID
     * @param performingUser User updating the note (must be REGISTRAR or ADVISOR)
     * @param note New note to set
     * @return Updated audit log entry
     */
    public AuditLog updateNote(Long logId, User performingUser, String note) {
        // Validate user role
        if (performingUser.getRole() != UserRole.REGISTRAR && performingUser.getRole() != UserRole.ADVISOR) {
            throw new IllegalArgumentException("Only registrar and advisor can update audit log notes");
        }
        
        // Find audit log entry
        AuditLog log = auditLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("Audit log entry not found"));
        
        // Update note
        log.setNote(note);
        
        return auditLogRepository.save(log);
    }
    
    /**
     * Log major declaration
     */
    public AuditLog logMajorDeclared(User student, User performingUser, String major, String note) {
        String details = String.format("{\"major\":\"%s\"}", major);
        return logAction(student, performingUser, AuditLog.ActionType.MAJOR_DECLARED, details, note);
    }
    
    /**
     * Log major change
     */
    public AuditLog logMajorChanged(User student, User performingUser, String fromMajor, String toMajor, String note) {
        String details = String.format("{\"fromMajor\":\"%s\",\"toMajor\":\"%s\"}", fromMajor, toMajor);
        return logAction(student, performingUser, AuditLog.ActionType.MAJOR_CHANGED, details, note);
    }
    
    /**
     * Log major removal
     */
    public AuditLog logMajorRemoved(User student, User performingUser, String major, String note) {
        String details = String.format("{\"major\":\"%s\"}", major);
        return logAction(student, performingUser, AuditLog.ActionType.MAJOR_REMOVED, details, note);
    }
    
    /**
     * Log minor declaration
     */
    public AuditLog logMinorDeclared(User student, User performingUser, String minor, String note) {
        String details = String.format("{\"minor\":\"%s\"}", minor);
        return logAction(student, performingUser, AuditLog.ActionType.MINOR_DECLARED, details, note);
    }
    
    /**
     * Log minor change
     */
    public AuditLog logMinorChanged(User student, User performingUser, String fromMinor, String toMinor, String note) {
        String details = String.format("{\"fromMinor\":\"%s\",\"toMinor\":\"%s\"}", fromMinor, toMinor);
        return logAction(student, performingUser, AuditLog.ActionType.MINOR_CHANGED, details, note);
    }
    
    /**
     * Log minor removal
     */
    public AuditLog logMinorRemoved(User student, User performingUser, String minor, String note) {
        String details = String.format("{\"minor\":\"%s\"}", minor);
        return logAction(student, performingUser, AuditLog.ActionType.MINOR_REMOVED, details, note);
    }
    
    /**
     * Log major declaration request (student-initiated)
     */
    public AuditLog logMajorDeclarationRequested(User student, String major, String effectiveTerm) {
        String details = String.format("{\"major\":\"%s\",\"effectiveTerm\":\"%s\"}", major, effectiveTerm);
        return logAction(student, student, AuditLog.ActionType.MAJOR_DECLARATION_REQUESTED, details, null);
    }
    
    /**
     * Log minor declaration request (student-initiated)
     */
    public AuditLog logMinorDeclarationRequested(User student, String minor, String effectiveTerm) {
        String details = String.format("{\"minor\":\"%s\",\"effectiveTerm\":\"%s\"}", minor, effectiveTerm);
        return logAction(student, student, AuditLog.ActionType.MINOR_DECLARATION_REQUESTED, details, null);
    }
    
    /**
     * Log major requirement version change
     */
    public AuditLog logMajorRequirementVersionChanged(User student, User performingUser, String major, String version, String note) {
        String details = String.format("{\"major\":\"%s\",\"version\":\"%s\"}", major, version);
        return logAction(student, performingUser, AuditLog.ActionType.MAJOR_REQUIREMENT_VERSION_CHANGED, details, note);
    }
    
    /**
     * Log minor requirement version change
     */
    public AuditLog logMinorRequirementVersionChanged(User student, User performingUser, String minor, String version, String note) {
        String details = String.format("{\"minor\":\"%s\",\"version\":\"%s\"}", minor, version);
        return logAction(student, performingUser, AuditLog.ActionType.MINOR_REQUIREMENT_VERSION_CHANGED, details, note);
    }
    
    /**
     * Log degree requirement waiver
     */
    public AuditLog logDegreeRequirementWaived(User student, User performingUser, String requirement, String note) {
        String details = String.format("{\"requirement\":\"%s\"}", requirement);
        return logAction(student, performingUser, AuditLog.ActionType.DEGREE_REQUIREMENT_WAIVED, details, note);
    }
    
    /**
     * Log prerequisite waiver
     */
    public AuditLog logPrerequisiteWaived(User student, User performingUser, String course, String note) {
        String details = String.format("{\"course\":\"%s\"}", course);
        return logAction(student, performingUser, AuditLog.ActionType.PREREQUISITE_WAIVED, details, note);
    }
    
    /**
     * Log corequisite waiver
     */
    public AuditLog logCorequisiteWaived(User student, User performingUser, String course, String note) {
        String details = String.format("{\"course\":\"%s\"}", course);
        return logAction(student, performingUser, AuditLog.ActionType.COREQUISITE_WAIVED, details, note);
    }
    
    /**
     * Log time conflict waiver
     */
    public AuditLog logTimeConflictWaived(User student, User performingUser, String classA, String classB, String note) {
        String details = String.format("{\"classA\":\"%s\",\"classB\":\"%s\"}", classA, classB);
        return logAction(student, performingUser, AuditLog.ActionType.TIME_CONFLICT_WAIVED, details, note);
    }
    
    /**
     * Log prerequisite waiver request (student-initiated)
     */
    public AuditLog logPrerequisiteWaiverRequested(User student, String course, String prerequisite, String reason) {
        String details = String.format("{\"course\":\"%s\",\"prerequisite\":\"%s\",\"reason\":\"%s\"}", course, prerequisite, reason);
        return logAction(student, student, AuditLog.ActionType.PREREQUISITE_WAIVER_REQUESTED, details, null);
    }
    
    /**
     * Log corequisite waiver request (student-initiated)
     */
    public AuditLog logCorequisiteWaiverRequested(User student, String course, String corequisite, String reason) {
        String details = String.format("{\"course\":\"%s\",\"corequisite\":\"%s\",\"reason\":\"%s\"}", course, corequisite, reason);
        return logAction(student, student, AuditLog.ActionType.COREQUISITE_WAIVER_REQUESTED, details, null);
    }
    
    /**
     * Log time conflict waiver request (student-initiated)
     */
    public AuditLog logTimeConflictWaiverRequested(User student, String classA, String classB, String reason) {
        String details = String.format("{\"classA\":\"%s\",\"classB\":\"%s\",\"reason\":\"%s\"}", classA, classB, reason);
        return logAction(student, student, AuditLog.ActionType.TIME_CONFLICT_WAIVER_REQUESTED, details, null);
    }
    
    /**
     * Log class enrollment
     */
    public AuditLog logClassEnrolled(User student, User performingUser, String className, String note) {
        String details = String.format("{\"class\":\"%s\"}", className);
        return logAction(student, performingUser, AuditLog.ActionType.CLASS_ENROLLED, details, note);
    }
    
    /**
     * Log class dropped
     */
    public AuditLog logClassDropped(User student, User performingUser, String className, String note) {
        String details = String.format("{\"class\":\"%s\"}", className);
        return logAction(student, performingUser, AuditLog.ActionType.CLASS_DROPPED, details, note);
    }
    
    /**
     * Log class added
     */
    public AuditLog logClassAdded(User student, User performingUser, String className, String note) {
        String details = String.format("{\"class\":\"%s\"}", className);
        return logAction(student, performingUser, AuditLog.ActionType.CLASS_ADDED, details, note);
    }
    
    /**
     * Log hold placed
     */
    public AuditLog logHoldPlaced(User student, User performingUser, String holdType, String reason, String note) {
        String details = String.format("{\"holdType\":\"%s\",\"reason\":\"%s\"}", holdType, reason);
        return logAction(student, performingUser, AuditLog.ActionType.HOLD_PLACED, details, note);
    }
    
    /**
     * Log hold removed
     */
    public AuditLog logHoldRemoved(User student, User performingUser, String holdType, String note) {
        String details = String.format("{\"holdType\":\"%s\"}", holdType);
        return logAction(student, performingUser, AuditLog.ActionType.HOLD_REMOVED, details, note);
    }
    
    /**
     * Log grade assigned
     */
    public AuditLog logGradeAssigned(User student, User performingUser, String className, String grade, String note) {
        String details = String.format("{\"class\":\"%s\",\"grade\":\"%s\"}", className, grade);
        return logAction(student, performingUser, AuditLog.ActionType.GRADE_ASSIGNED, details, note);
    }
    
    /**
     * Log grade changed
     */
    public AuditLog logGradeChanged(User student, User performingUser, String className, String fromGrade, String toGrade, String note) {
        String details = String.format("{\"class\":\"%s\",\"fromGrade\":\"%s\",\"toGrade\":\"%s\"}", className, fromGrade, toGrade);
        return logAction(student, performingUser, AuditLog.ActionType.GRADE_CHANGED, details, note);
    }
}


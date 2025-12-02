package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.TimeConflictWaiver;
import edu.sbu.cse416.sam_system.service.TimeConflictWaiverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing time conflict waivers.
 * 
 * TP: Used Cursor to implement REST controller for time conflict waivers.
 */
@RestController
@RequestMapping("/api/waivers")
@CrossOrigin(origins = "http://localhost:5173")
public class TimeConflictWaiverController {
    
    @Autowired
    private TimeConflictWaiverService waiverService;
    
    /**
     * Create a new waiver request
     * POST /api/waivers
     * Body: { "studentId": 1, "enrolledClassId": 123, "conflictingClassId": 456, "reason": "Optional reason" }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createWaiver(@RequestBody Map<String, Object> body) {
        try {
            if (body == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Request body is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            if (!body.containsKey("studentId") || !body.containsKey("enrolledClassId") || !body.containsKey("conflictingClassId")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "studentId, enrolledClassId, and conflictingClassId are required");
                return ResponseEntity.badRequest().body(error);
            }
            
            Long studentId = Long.parseLong(body.get("studentId").toString());
            Long enrolledClassId = Long.parseLong(body.get("enrolledClassId").toString());
            Long conflictingClassId = Long.parseLong(body.get("conflictingClassId").toString());
            String reason = body.containsKey("reason") ? body.get("reason").toString() : null;
            
            TimeConflictWaiver waiver = waiverService.createWaiver(studentId, enrolledClassId, conflictingClassId, reason);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver request created successfully");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid ID format: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to create waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Get all waivers for a student
     * GET /api/waivers/student/{studentId}
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<Map<String, Object>>> getWaiversByStudent(@PathVariable Long studentId) {
        try {
            List<TimeConflictWaiver> waivers = waiverService.getWaiversByStudent(studentId);
            List<Map<String, Object>> response = waivers.stream()
                .map(this::buildWaiverResponse)
                .toList();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get time conflict waivers by student ID (query parameter version)
     * GET /api/waivers/time-conflicts?studentId={studentId}
     */
    @GetMapping("/time-conflicts")
    public ResponseEntity<List<Map<String, Object>>> getTimeConflictWaivers(
            @RequestParam(required = false) Long studentId) {
        try {
            if (studentId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            List<TimeConflictWaiver> waivers = waiverService.getWaiversByStudent(studentId);
            List<Map<String, Object>> response = waivers.stream()
                .map(this::buildWaiverResponse)
                .toList();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get all pending waivers
     * GET /api/waivers/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingWaivers() {
        try {
            List<TimeConflictWaiver> waivers = waiverService.getPendingWaivers();
            List<Map<String, Object>> response = waivers.stream()
                .map(this::buildWaiverResponse)
                .toList();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get waivers that need approval from an instructor
     * GET /api/waivers/instructor/{instructorId}
     */
    @GetMapping("/instructor/{instructorId}")
    public ResponseEntity<List<Map<String, Object>>> getWaiversForInstructor(@PathVariable Long instructorId) {
        try {
            List<TimeConflictWaiver> waivers = waiverService.getWaiversForInstructor(instructorId);
            List<Map<String, Object>> response = waivers.stream()
                .map(this::buildWaiverResponse)
                .toList();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * Get waivers that need approval from an advisor
     * GET /api/waivers/advisor/{advisorId}
     */
    @GetMapping("/advisor/{advisorId}")
    public ResponseEntity<List<Map<String, Object>>> getWaiversForAdvisor(@PathVariable Long advisorId) {
        try {
            List<TimeConflictWaiver> waivers = waiverService.getWaiversForAdvisor(advisorId);
            List<Map<String, Object>> response = waivers.stream()
                .map(this::buildWaiverResponse)
                .toList();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * Get waiver by ID
     * GET /api/waivers/{waiverId}
     */
    @GetMapping("/{waiverId}")
    public ResponseEntity<Map<String, Object>> getWaiverById(@PathVariable Long waiverId) {
        try {
            TimeConflictWaiver waiver = waiverService.getWaiverById(waiverId);
            return ResponseEntity.ok(buildWaiverResponse(waiver));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Waiver not found: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Approve waiver by enrolled class instructor
     * POST /api/waivers/{waiverId}/approve/enrolled-instructor
     * Body: { "instructorId": 10 }
     */
    @PostMapping("/{waiverId}/approve/enrolled-instructor")
    public ResponseEntity<Map<String, Object>> approveByEnrolledInstructor(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            if (body == null || !body.containsKey("instructorId")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "instructorId is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            Long instructorId = Long.parseLong(body.get("instructorId").toString());
            TimeConflictWaiver waiver = waiverService.approveByEnrolledInstructor(waiverId, instructorId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver approved by enrolled class instructor");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid instructorId format: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to approve waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Approve waiver by conflicting class instructor
     * POST /api/waivers/{waiverId}/approve/conflicting-instructor
     * Body: { "instructorId": 10 }
     */
    @PostMapping("/{waiverId}/approve/conflicting-instructor")
    public ResponseEntity<Map<String, Object>> approveByConflictingInstructor(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            if (body == null || !body.containsKey("instructorId")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "instructorId is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            Long instructorId = Long.parseLong(body.get("instructorId").toString());
            TimeConflictWaiver waiver = waiverService.approveByConflictingInstructor(waiverId, instructorId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver approved by conflicting class instructor");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid instructorId format: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to approve waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Approve waiver by advisor
     * POST /api/waivers/{waiverId}/approve/advisor
     * Body: { "advisorId": 5 }
     */
    @PostMapping("/{waiverId}/approve/advisor")
    public ResponseEntity<Map<String, Object>> approveByAdvisor(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            if (body == null || !body.containsKey("advisorId")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "advisorId is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            Long advisorId = Long.parseLong(body.get("advisorId").toString());
            TimeConflictWaiver waiver = waiverService.approveByAdvisor(waiverId, advisorId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver approved by advisor");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid advisorId format: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to approve waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Reject waiver by enrolled class instructor
     * POST /api/waivers/{waiverId}/reject/enrolled-instructor
     * Body: { "instructorId": 10 }
     */
    @PostMapping("/{waiverId}/reject/enrolled-instructor")
    public ResponseEntity<Map<String, Object>> rejectByEnrolledInstructor(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            if (body == null || !body.containsKey("instructorId")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "instructorId is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            Long instructorId = Long.parseLong(body.get("instructorId").toString());
            TimeConflictWaiver waiver = waiverService.rejectByEnrolledInstructor(waiverId, instructorId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver rejected by enrolled class instructor");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid instructorId format: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to reject waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Reject waiver by conflicting class instructor
     * POST /api/waivers/{waiverId}/reject/conflicting-instructor
     * Body: { "instructorId": 10 }
     */
    @PostMapping("/{waiverId}/reject/conflicting-instructor")
    public ResponseEntity<Map<String, Object>> rejectByConflictingInstructor(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            if (body == null || !body.containsKey("instructorId")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "instructorId is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            Long instructorId = Long.parseLong(body.get("instructorId").toString());
            TimeConflictWaiver waiver = waiverService.rejectByConflictingInstructor(waiverId, instructorId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver rejected by conflicting class instructor");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid instructorId format: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to reject waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Reject waiver by advisor
     * POST /api/waivers/{waiverId}/reject/advisor
     * Body: { "advisorId": 5 }
     */
    @PostMapping("/{waiverId}/reject/advisor")
    public ResponseEntity<Map<String, Object>> rejectByAdvisor(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            if (body == null || !body.containsKey("advisorId")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "advisorId is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            Long advisorId = Long.parseLong(body.get("advisorId").toString());
            TimeConflictWaiver waiver = waiverService.rejectByAdvisor(waiverId, advisorId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver rejected by advisor");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid advisorId format: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to reject waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Build response map from waiver entity
     */
    private Map<String, Object> buildWaiverResponse(TimeConflictWaiver waiver) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("waiverId", waiver.getWaiverId());
            
            // Student info with null check
            if (waiver.getStudent() != null) {
                response.put("studentId", waiver.getStudent().getUserId());
                String firstName = waiver.getStudent().getFirstName() != null ? waiver.getStudent().getFirstName() : "";
                String lastName = waiver.getStudent().getLastName() != null ? waiver.getStudent().getLastName() : "";
                response.put("studentName", (firstName + " " + lastName).trim());
            }
            
            // Enrolled class info with null check
            if (waiver.getEnrolledClass() != null) {
                Map<String, Object> enrolledClass = new HashMap<>();
                enrolledClass.put("classId", waiver.getEnrolledClass().getClassId());
                enrolledClass.put("department", waiver.getEnrolledClass().getDepartment());
                enrolledClass.put("courseNum", waiver.getEnrolledClass().getCourseNum());
                enrolledClass.put("section", waiver.getEnrolledClass().getSection());
                enrolledClass.put("title", waiver.getEnrolledClass().getTitle());
                enrolledClass.put("instructor", waiver.getEnrolledClass().getInstructor());
                response.put("enrolledClass", enrolledClass);
            }
            
            // Conflicting class info with null check
            if (waiver.getConflictingClass() != null) {
                Map<String, Object> conflictingClass = new HashMap<>();
                conflictingClass.put("classId", waiver.getConflictingClass().getClassId());
                conflictingClass.put("department", waiver.getConflictingClass().getDepartment());
                conflictingClass.put("courseNum", waiver.getConflictingClass().getCourseNum());
                conflictingClass.put("section", waiver.getConflictingClass().getSection());
                conflictingClass.put("title", waiver.getConflictingClass().getTitle());
                conflictingClass.put("instructor", waiver.getConflictingClass().getInstructor());
                response.put("conflictingClass", conflictingClass);
            }
            
            // null = pending, true = approved, false = rejected
            response.put("enrolledInstructorApproved", waiver.getEnrolledInstructorApproved());
            if (waiver.getEnrolledInstructorApprovedBy() != null) {
                response.put("enrolledInstructorApprovedBy", waiver.getEnrolledInstructorApprovedBy().getUserId());
                String firstName = waiver.getEnrolledInstructorApprovedBy().getFirstName() != null ? waiver.getEnrolledInstructorApprovedBy().getFirstName() : "";
                String lastName = waiver.getEnrolledInstructorApprovedBy().getLastName() != null ? waiver.getEnrolledInstructorApprovedBy().getLastName() : "";
                response.put("enrolledInstructorApprovedByName", (firstName + " " + lastName).trim());
            }
            response.put("enrolledInstructorApprovedAt", waiver.getEnrolledInstructorApprovedAt());
            
            // null = pending, true = approved, false = rejected
            response.put("conflictingInstructorApproved", waiver.getConflictingInstructorApproved());
            if (waiver.getConflictingInstructorApprovedBy() != null) {
                response.put("conflictingInstructorApprovedBy", waiver.getConflictingInstructorApprovedBy().getUserId());
                String firstName = waiver.getConflictingInstructorApprovedBy().getFirstName() != null ? waiver.getConflictingInstructorApprovedBy().getFirstName() : "";
                String lastName = waiver.getConflictingInstructorApprovedBy().getLastName() != null ? waiver.getConflictingInstructorApprovedBy().getLastName() : "";
                response.put("conflictingInstructorApprovedByName", (firstName + " " + lastName).trim());
            }
            response.put("conflictingInstructorApprovedAt", waiver.getConflictingInstructorApprovedAt());
            
            // null = pending, true = approved, false = rejected
            response.put("advisorApproved", waiver.getAdvisorApproved());
            if (waiver.getAdvisorApprovedBy() != null) {
                response.put("advisorApprovedBy", waiver.getAdvisorApprovedBy().getUserId());
                String firstName = waiver.getAdvisorApprovedBy().getFirstName() != null ? waiver.getAdvisorApprovedBy().getFirstName() : "";
                String lastName = waiver.getAdvisorApprovedBy().getLastName() != null ? waiver.getAdvisorApprovedBy().getLastName() : "";
                response.put("advisorApprovedByName", (firstName + " " + lastName).trim());
            }
            response.put("advisorApprovedAt", waiver.getAdvisorApprovedAt());
            
            response.put("status", waiver.getStatus() != null ? waiver.getStatus().toString() : "PENDING");
            response.put("reason", waiver.getReason());
            response.put("createdAt", waiver.getCreatedAt());
            response.put("updatedAt", waiver.getUpdatedAt());
            
            return response;
        } catch (Exception e) {
            // Fallback response if there's any issue building the response
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("waiverId", waiver.getWaiverId());
            errorResponse.put("error", "Error building response: " + e.getMessage());
            return errorResponse;
        }
    }
}


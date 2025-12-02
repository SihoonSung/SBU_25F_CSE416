package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.PrerequisiteWaiver;
import edu.sbu.cse416.sam_system.service.PrerequisiteWaiverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing prerequisite waivers.
 * 
 * TP: Used Cursor to implement REST controller for prerequisite waivers
 */
@RestController
@RequestMapping("/api/waivers/prerequisites")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class PrerequisiteWaiverController {
    
    @Autowired
    private PrerequisiteWaiverService waiverService;
    
    /**
     * Create a new waiver request
     * POST /api/waivers/prerequisites
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createWaiver(@RequestBody Map<String, Object> body) {
        try {
            Long studentId = Long.parseLong(body.get("studentId").toString());
            Long classId = Long.parseLong(body.get("classId").toString());
            String prerequisite = (String) body.get("prerequisite");
            String reason = (String) body.get("reason");
            
            PrerequisiteWaiver waiver = waiverService.createWaiver(studentId, classId, prerequisite, reason);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver request created successfully");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to create waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Get all waivers for a student
     * GET /api/waivers/prerequisites/student/{studentId}
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<Map<String, Object>>> getWaiversByStudent(@PathVariable Long studentId) {
        try {
            List<PrerequisiteWaiver> waivers = waiverService.getWaiversByStudent(studentId);
            List<Map<String, Object>> response = waivers.stream()
                .map(this::buildWaiverResponse)
                .toList();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * Get all pending waivers
     * GET /api/waivers/prerequisites/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingWaivers() {
        try {
            List<PrerequisiteWaiver> waivers = waiverService.getPendingWaivers();
            List<Map<String, Object>> response = waivers.stream()
                .map(this::buildWaiverResponse)
                .toList();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * Get waivers for an instructor
     * GET /api/waivers/prerequisites/instructor/{instructorId}
     */
    @GetMapping("/instructor/{instructorId}")
    public ResponseEntity<List<Map<String, Object>>> getWaiversForInstructor(@PathVariable Long instructorId) {
        try {
            List<PrerequisiteWaiver> waivers = waiverService.getWaiversForInstructor(instructorId);
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
     * GET /api/waivers/prerequisites/{waiverId}
     */
    @GetMapping("/{waiverId}")
    public ResponseEntity<Map<String, Object>> getWaiverById(@PathVariable Long waiverId) {
        try {
            PrerequisiteWaiver waiver = waiverService.getWaiverById(waiverId);
            return ResponseEntity.ok(buildWaiverResponse(waiver));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Waiver not found: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Approve waiver by instructor
     * POST /api/waivers/prerequisites/{waiverId}/approve/instructor
     */
    @PostMapping("/{waiverId}/approve/instructor")
    public ResponseEntity<Map<String, Object>> approveByInstructor(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            Long instructorId = Long.parseLong(body.get("instructorId").toString());
            String note = (String) body.get("note");
            
            PrerequisiteWaiver waiver = waiverService.approveByInstructor(waiverId, instructorId, note);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver approved by instructor");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to approve waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Approve waiver by advisor
     * POST /api/waivers/prerequisites/{waiverId}/approve/advisor
     */
    @PostMapping("/{waiverId}/approve/advisor")
    public ResponseEntity<Map<String, Object>> approveByAdvisor(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            Long advisorId = Long.parseLong(body.get("advisorId").toString());
            String note = (String) body.get("note");
            
            PrerequisiteWaiver waiver = waiverService.approveByAdvisor(waiverId, advisorId, note);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver approved by advisor");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to approve waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Reject waiver by instructor
     * POST /api/waivers/prerequisites/{waiverId}/reject/instructor
     */
    @PostMapping("/{waiverId}/reject/instructor")
    public ResponseEntity<Map<String, Object>> rejectByInstructor(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            Long instructorId = Long.parseLong(body.get("instructorId").toString());
            String note = (String) body.get("note");
            
            PrerequisiteWaiver waiver = waiverService.rejectByInstructor(waiverId, instructorId, note);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver rejected by instructor");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to reject waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Reject waiver by advisor
     * POST /api/waivers/prerequisites/{waiverId}/reject/advisor
     */
    @PostMapping("/{waiverId}/reject/advisor")
    public ResponseEntity<Map<String, Object>> rejectByAdvisor(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            Long advisorId = Long.parseLong(body.get("advisorId").toString());
            String note = (String) body.get("note");
            
            PrerequisiteWaiver waiver = waiverService.rejectByAdvisor(waiverId, advisorId, note);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver rejected by advisor");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
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
    private Map<String, Object> buildWaiverResponse(PrerequisiteWaiver waiver) {
        Map<String, Object> response = new HashMap<>();
        response.put("waiverId", waiver.getWaiverId());
        
        if (waiver.getStudent() != null) {
            response.put("studentId", waiver.getStudent().getUserId());
            response.put("studentName", waiver.getStudent().getFullName());
        }
        
        if (waiver.getClassEntity() != null) {
            Map<String, Object> classInfo = new HashMap<>();
            classInfo.put("classId", waiver.getClassEntity().getClassId());
            classInfo.put("department", waiver.getClassEntity().getDepartment());
            classInfo.put("courseNum", waiver.getClassEntity().getCourseNum());
            classInfo.put("title", waiver.getClassEntity().getTitle());
            response.put("class", classInfo);
        }
        
        response.put("prerequisite", waiver.getPrerequisite());
        response.put("reason", waiver.getReason());
        response.put("status", waiver.getStatus().name());
        response.put("instructorApproved", waiver.getInstructorApproved());
        response.put("advisorApproved", waiver.getAdvisorApproved());
        response.put("note", waiver.getNote());
        response.put("createdAt", waiver.getCreatedAt());
        response.put("updatedAt", waiver.getUpdatedAt());
        
        return response;
    }
}


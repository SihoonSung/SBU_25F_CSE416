package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.RequisiteWaiver;
import edu.sbu.cse416.sam_system.entity.RequisiteWaiver.RequisiteType;
import edu.sbu.cse416.sam_system.service.RequisiteWaiverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing requisite waivers.
 * 
 * TP: Used Cursor to implement REST controller for requisite waivers.
 */
@RestController
@RequestMapping("/api/requisite-waivers")
@CrossOrigin(origins = "http://localhost:5173")
public class RequisiteWaiverController {
    
    @Autowired
    private RequisiteWaiverService waiverService;
    
    /**
     * Create a new waiver request
     * POST /api/requisite-waivers
     * Body: { "studentId": 1, "classId": 123, "requisiteType": "PREREQUISITE", "reason": "Optional reason" }
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
            
            if (!body.containsKey("studentId") || !body.containsKey("classId") || !body.containsKey("requisiteType")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "studentId, classId, and requisiteType are required");
                return ResponseEntity.badRequest().body(error);
            }
            
            Long studentId = Long.parseLong(body.get("studentId").toString());
            Long classId = Long.parseLong(body.get("classId").toString());
            String requisiteTypeStr = body.get("requisiteType").toString().toUpperCase();
            RequisiteType requisiteType;
            try {
                requisiteType = RequisiteType.valueOf(requisiteTypeStr);
            } catch (IllegalArgumentException e) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Invalid requisiteType. Must be PREREQUISITE, ANTIREQUISITE, or COREQUISITE");
                return ResponseEntity.badRequest().body(error);
            }
            String reason = body.containsKey("reason") ? body.get("reason").toString() : null;
            
            RequisiteWaiver waiver = waiverService.createWaiver(studentId, classId, requisiteType, reason);
            
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
     * GET /api/requisite-waivers/student/{studentId}
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<Map<String, Object>>> getWaiversByStudent(@PathVariable Long studentId) {
        try {
            List<RequisiteWaiver> waivers = waiverService.getWaiversByStudent(studentId);
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
     * GET /api/requisite-waivers/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingWaivers() {
        try {
            List<RequisiteWaiver> waivers = waiverService.getPendingWaivers();
            List<Map<String, Object>> response = waivers.stream()
                .map(this::buildWaiverResponse)
                .toList();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get waivers that need approval from an advisor
     * GET /api/requisite-waivers/advisor/{advisorId}
     */
    @GetMapping("/advisor/{advisorId}")
    public ResponseEntity<List<Map<String, Object>>> getWaiversForAdvisor(@PathVariable Long advisorId) {
        try {
            List<RequisiteWaiver> waivers = waiverService.getWaiversForAdvisor(advisorId);
            List<Map<String, Object>> response = waivers.stream()
                .map(this::buildWaiverResponse)
                .toList();
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * Get waivers that need approval from registrar
     * GET /api/requisite-waivers/registrar
     */
    @GetMapping("/registrar")
    public ResponseEntity<List<Map<String, Object>>> getWaiversForRegistrar() {
        try {
            List<RequisiteWaiver> waivers = waiverService.getWaiversForRegistrar();
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
     * GET /api/requisite-waivers/{waiverId}
     */
    @GetMapping("/{waiverId}")
    public ResponseEntity<Map<String, Object>> getWaiverById(@PathVariable Long waiverId) {
        try {
            RequisiteWaiver waiver = waiverService.getWaiverById(waiverId);
            return ResponseEntity.ok(buildWaiverResponse(waiver));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Waiver not found: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Approve waiver by advisor
     * POST /api/requisite-waivers/{waiverId}/approve/advisor
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
            RequisiteWaiver waiver = waiverService.approveByAdvisor(waiverId, advisorId);
            
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
     * Reject waiver by advisor
     * POST /api/requisite-waivers/{waiverId}/reject/advisor
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
            RequisiteWaiver waiver = waiverService.rejectByAdvisor(waiverId, advisorId);
            
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
     * Approve waiver by registrar
     * POST /api/requisite-waivers/{waiverId}/approve/registrar
     * Body: { "registrarId": 3 }
     */
    @PostMapping("/{waiverId}/approve/registrar")
    public ResponseEntity<Map<String, Object>> approveByRegistrar(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            if (body == null || !body.containsKey("registrarId")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "registrarId is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            Long registrarId = Long.parseLong(body.get("registrarId").toString());
            RequisiteWaiver waiver = waiverService.approveByRegistrar(waiverId, registrarId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver approved by registrar");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid registrarId format: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to approve waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Reject waiver by registrar
     * POST /api/requisite-waivers/{waiverId}/reject/registrar
     * Body: { "registrarId": 3 }
     */
    @PostMapping("/{waiverId}/reject/registrar")
    public ResponseEntity<Map<String, Object>> rejectByRegistrar(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            if (body == null || !body.containsKey("registrarId")) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "registrarId is required");
                return ResponseEntity.badRequest().body(error);
            }
            
            Long registrarId = Long.parseLong(body.get("registrarId").toString());
            RequisiteWaiver waiver = waiverService.rejectByRegistrar(waiverId, registrarId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver rejected by registrar");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Invalid registrarId format: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to reject waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Delete a waiver
     * DELETE /api/requisite-waivers/{waiverId}
     */
    @DeleteMapping("/{waiverId}")
    public ResponseEntity<Map<String, Object>> deleteWaiver(@PathVariable Long waiverId) {
        try {
            waiverService.deleteWaiver(waiverId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver deleted successfully");
            response.put("waiverId", waiverId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to delete waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Build response map from waiver entity
     */
    private Map<String, Object> buildWaiverResponse(RequisiteWaiver waiver) {
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
            
            // Class info with null check
            if (waiver.getClassEntity() != null) {
                Map<String, Object> classInfo = new HashMap<>();
                classInfo.put("classId", waiver.getClassEntity().getClassId());
                classInfo.put("department", waiver.getClassEntity().getDepartment());
                classInfo.put("courseNum", waiver.getClassEntity().getCourseNum());
                classInfo.put("section", waiver.getClassEntity().getSection());
                classInfo.put("title", waiver.getClassEntity().getTitle());
                response.put("classEntity", classInfo);
            }
            
            response.put("requisiteType", waiver.getRequisiteType() != null ? waiver.getRequisiteType().toString() : null);
            
            // null = pending, true = approved, false = rejected
            response.put("advisorApproved", waiver.getAdvisorApproved());
            if (waiver.getAdvisorApprovedBy() != null) {
                response.put("advisorApprovedBy", waiver.getAdvisorApprovedBy().getUserId());
                String firstName = waiver.getAdvisorApprovedBy().getFirstName() != null ? waiver.getAdvisorApprovedBy().getFirstName() : "";
                String lastName = waiver.getAdvisorApprovedBy().getLastName() != null ? waiver.getAdvisorApprovedBy().getLastName() : "";
                response.put("advisorApprovedByName", (firstName + " " + lastName).trim());
            }
            response.put("advisorApprovedAt", waiver.getAdvisorApprovedAt());
            
            // null = pending, true = approved, false = rejected
            response.put("registrarApproved", waiver.getRegistrarApproved());
            if (waiver.getRegistrarApprovedBy() != null) {
                response.put("registrarApprovedBy", waiver.getRegistrarApprovedBy().getUserId());
                String firstName = waiver.getRegistrarApprovedBy().getFirstName() != null ? waiver.getRegistrarApprovedBy().getFirstName() : "";
                String lastName = waiver.getRegistrarApprovedBy().getLastName() != null ? waiver.getRegistrarApprovedBy().getLastName() : "";
                response.put("registrarApprovedByName", (firstName + " " + lastName).trim());
            }
            response.put("registrarApprovedAt", waiver.getRegistrarApprovedAt());
            
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


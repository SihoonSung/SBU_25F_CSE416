package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.DegreeRequirementWaiver;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.service.DegreeRequirementWaiverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for managing degree requirement waivers.
 * 
 * TP: Used Cursor to implement REST controller for degree requirement waivers
 */
@RestController
@RequestMapping("/api/waivers/degree-requirements")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class DegreeRequirementWaiverController {
    
    @Autowired
    private DegreeRequirementWaiverService waiverService;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Create a new course waiver for degree requirements.
     * 
     * The registrar waives a degree requirement indirectly, by waiving individual courses
     * as if the student had taken the courses and received an adequate grade.
     * 
     * POST /api/waivers/degree-requirements
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createWaiver(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal OAuth2User principal) {
        try {
            System.out.println("=== DEBUG: createWaiver called ===");
            System.out.println("DEBUG: Principal is null: " + (principal == null));
            if (principal != null) {
                System.out.println("DEBUG: Principal attributes: " + principal.getAttributes());
                System.out.println("DEBUG: Principal name: " + principal.getName());
            }
            System.out.println("DEBUG: Request body: " + body);
            
            // Get current authenticated user
            Long approvedById;
            if (principal != null) {
                approvedById = getUserIdFromPrincipal(principal);
                System.out.println("DEBUG: approvedById from principal: " + approvedById);
            } else {
                // Fallback: Get approvedById from request body (for testing purposes)
                System.out.println("DEBUG: Principal is null, trying to get approvedById from body");
                if (body.containsKey("approvedById")) {
                    approvedById = Long.parseLong(body.get("approvedById").toString());
                    System.out.println("DEBUG: approvedById from body: " + approvedById);
                } else {
                    throw new IllegalArgumentException("User not authenticated and approvedById not provided in request body");
                }
            }
            
            Long studentId = Long.parseLong(body.get("studentId").toString());
            String subjectCode = (String) body.get("subjectCode");
            String department = (String) body.get("department");
            Integer courseNum = Integer.parseInt(body.get("courseNum").toString());
            Integer credits = Integer.parseInt(body.get("credits").toString());
            String grade = (String) body.get("grade");
            String note = (String) body.get("note");
            
            System.out.println("DEBUG: Calling waiverService.createWaiver with:");
            System.out.println("  studentId: " + studentId);
            System.out.println("  subjectCode: " + subjectCode);
            System.out.println("  department: " + department);
            System.out.println("  courseNum: " + courseNum);
            System.out.println("  credits: " + credits);
            System.out.println("  grade: " + grade);
            System.out.println("  approvedById: " + approvedById);
            System.out.println("  note: " + note);
            
            DegreeRequirementWaiver waiver = waiverService.createWaiver(
                studentId, subjectCode, department, courseNum, credits, grade, approvedById, note);
            
            System.out.println("DEBUG: Waiver created successfully, ID: " + waiver.getWaiverId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Course waiver created successfully");
            response.put("waiver", buildWaiverResponse(waiver));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("DEBUG: Exception in createWaiver: " + e.getClass().getName());
            System.out.println("DEBUG: Exception message: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to create waiver: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * Get all waivers for a student
     * GET /api/waivers/degree-requirements/student/{studentId}
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<Map<String, Object>>> getWaiversByStudent(@PathVariable Long studentId) {
        try {
            List<DegreeRequirementWaiver> waivers = waiverService.getWaiversByStudent(studentId);
            List<Map<String, Object>> response = waivers.stream()
                .map(this::buildWaiverResponse)
                .toList();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * Get waivers for a student and subject code
     * GET /api/waivers/degree-requirements/student/{studentId}/subject/{subjectCode}
     */
    @GetMapping("/student/{studentId}/subject/{subjectCode}")
    public ResponseEntity<List<Map<String, Object>>> getWaiversByStudentAndSubject(
            @PathVariable Long studentId, @PathVariable String subjectCode) {
        try {
            List<DegreeRequirementWaiver> waivers = waiverService.getWaiversByStudentAndSubject(studentId, subjectCode);
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
     * GET /api/waivers/degree-requirements/{waiverId}
     */
    @GetMapping("/{waiverId}")
    public ResponseEntity<Map<String, Object>> getWaiverById(@PathVariable Long waiverId) {
        try {
            DegreeRequirementWaiver waiver = waiverService.getWaiverById(waiverId);
            return ResponseEntity.ok(buildWaiverResponse(waiver));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Waiver not found: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Delete a waiver
     * DELETE /api/waivers/degree-requirements/{waiverId}
     */
    @DeleteMapping("/{waiverId}")
    public ResponseEntity<Map<String, Object>> deleteWaiver(
            @PathVariable Long waiverId,
            @RequestBody Map<String, Object> body) {
        try {
            Long deletedById = Long.parseLong(body.get("deletedById").toString());
            
            DegreeRequirementWaiver waiver = waiverService.deleteWaiver(waiverId, deletedById);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Waiver deleted successfully");
            response.put("waiver", buildWaiverResponse(waiver));
            
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
    private Map<String, Object> buildWaiverResponse(DegreeRequirementWaiver waiver) {
        Map<String, Object> response = new HashMap<>();
        response.put("waiverId", waiver.getWaiverId());
        
        if (waiver.getStudent() != null) {
            response.put("studentId", waiver.getStudent().getUserId());
            response.put("studentName", waiver.getStudent().getFullName());
        }
        
        response.put("subjectCode", waiver.getSubjectCode());
        response.put("department", waiver.getDepartment());
        response.put("courseNum", waiver.getCourseNum());
        response.put("courseCode", waiver.getCourseCode());
        response.put("credits", waiver.getCredits());
        response.put("grade", waiver.getGrade());
        
        if (waiver.getApprovedBy() != null) {
            response.put("approvedById", waiver.getApprovedBy().getUserId());
            response.put("approvedByName", waiver.getApprovedBy().getFullName());
        }
        
        response.put("note", waiver.getNote());
        response.put("approvedAt", waiver.getApprovedAt());
        response.put("createdAt", waiver.getCreatedAt());
        
        return response;
    }
    
    /**
     * Extract user ID from OAuth2 principal
     */
    private Long getUserIdFromPrincipal(OAuth2User principal) {
        System.out.println("DEBUG: getUserIdFromPrincipal called");
        if (principal == null) {
            System.out.println("DEBUG: Principal is null!");
            throw new IllegalArgumentException("User not authenticated");
        }
        
        // Get Google ID and email from OAuth2 principal
        String googleId = principal.getAttribute("sub");
        String email = principal.getAttribute("email");
        
        System.out.println("DEBUG: Google ID from principal: " + googleId);
        System.out.println("DEBUG: Email from principal: " + email);
        
        if (googleId != null) {
            // Try to find user by Google ID first
            Optional<User> user = userRepository.findByGoogleId(googleId);
            if (user.isPresent()) {
                System.out.println("DEBUG: Found user by Google ID: " + user.get().getUserId() + " (" + user.get().getEmail() + ")");
                return user.get().getUserId();
            } else {
                System.out.println("DEBUG: User not found by Google ID: " + googleId);
            }
        }
        
        if (email != null) {
            // Try to find user by email
            Optional<User> user = userRepository.findByEmailIgnoreCase(email);
            if (user.isPresent()) {
                System.out.println("DEBUG: Found user by email: " + user.get().getUserId() + " (" + user.get().getEmail() + ")");
                return user.get().getUserId();
            } else {
                System.out.println("DEBUG: User not found by email: " + email);
            }
        }
        
        System.out.println("DEBUG: User not found - Google ID: " + googleId + ", Email: " + email);
        throw new IllegalArgumentException("User not found");
    }
}


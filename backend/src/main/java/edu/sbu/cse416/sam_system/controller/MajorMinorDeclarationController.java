package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.DeclarationStatus;
import edu.sbu.cse416.sam_system.entity.DeclarationType;
import edu.sbu.cse416.sam_system.entity.DegreeType;
import edu.sbu.cse416.sam_system.entity.MajorMinorDeclaration;
import edu.sbu.cse416.sam_system.security.CurrentUser;
import edu.sbu.cse416.sam_system.service.MajorMinorDeclarationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for major/minor declaration management.
 */
@RestController
@RequestMapping("/api/declarations")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class MajorMinorDeclarationController {
    
    @Autowired
    private MajorMinorDeclarationService declarationService;
    
    @Autowired
    private CurrentUser currentUser;
    
    /**
     * Submit a new major/minor declaration (Student)
     */
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitDeclaration(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            String studentNetId = currentUser.getNetId();
            String subjectCode = (String) request.get("subjectCode");
            String degreeTypeStr = (String) request.get("degreeType");
            String declarationTypeStr = (String) request.get("declarationType");
            String effectiveTerm = (String) request.get("effectiveTerm");
            
            DegreeType degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
            DeclarationType declarationType = DeclarationType.valueOf(declarationTypeStr.toUpperCase());
            
            MajorMinorDeclaration declaration = declarationService.submitDeclaration(
                    studentNetId, subjectCode, degreeType, declarationType, effectiveTerm);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Declaration submitted successfully");
            response.put("declaration", declaration);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get student's declarations
     */
    @GetMapping("/my-declarations")
    public ResponseEntity<Map<String, Object>> getMyDeclarations(@AuthenticationPrincipal OAuth2User principal) {
        try {
            String studentNetId = currentUser.getNetId();
            List<MajorMinorDeclaration> declarations = declarationService.getStudentDeclarations(studentNetId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("declarations", declarations);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get pending declarations for advisor
     */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPendingDeclarations(@AuthenticationPrincipal OAuth2User principal) {
        try {
            String advisorNetId = currentUser.getNetId();
            List<MajorMinorDeclaration> declarations = declarationService.getPendingDeclarationsForAdvisor(advisorNetId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("declarations", declarations);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Approve a declaration (Advisor)
     */
    @PostMapping("/{declarationId}/approve")
    public ResponseEntity<Map<String, Object>> approveDeclaration(
            @PathVariable Long declarationId,
            @RequestBody(required = false) Map<String, String> request,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            String advisorNetId = currentUser.getNetId();
            String comments = request != null ? request.get("comments") : null;
            
            MajorMinorDeclaration declaration = declarationService.approveDeclaration(declarationId, advisorNetId, comments);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Declaration approved successfully");
            response.put("declaration", declaration);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Reject a declaration (Advisor)
     */
    @PostMapping("/{declarationId}/reject")
    public ResponseEntity<Map<String, Object>> rejectDeclaration(
            @PathVariable Long declarationId,
            @RequestBody(required = false) Map<String, String> request,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            String advisorNetId = currentUser.getNetId();
            String comments = request != null ? request.get("comments") : null;
            
            MajorMinorDeclaration declaration = declarationService.rejectDeclaration(declarationId, advisorNetId, comments);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Declaration rejected");
            response.put("declaration", declaration);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Withdraw a declaration (Student)
     */
    @PostMapping("/{declarationId}/withdraw")
    public ResponseEntity<Map<String, Object>> withdrawDeclaration(
            @PathVariable Long declarationId,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            String studentNetId = currentUser.getNetId();
            MajorMinorDeclaration declaration = declarationService.withdrawDeclaration(declarationId, studentNetId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Declaration withdrawn successfully");
            response.put("declaration", declaration);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get declarations by status (Admin/Registrar)
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<Map<String, Object>> getDeclarationsByStatus(@PathVariable DeclarationStatus status) {
        try {
            List<MajorMinorDeclaration> declarations = declarationService.getDeclarationsByStatus(status);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("declarations", declarations);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get all active declarations (Admin/Registrar)
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllDeclarations() {
        try {
            List<MajorMinorDeclaration> declarations = declarationService.getAllActiveDeclarations();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("declarations", declarations);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get active declarations by type for current student
     */
    @GetMapping("/my-declarations/{declarationType}")
    public ResponseEntity<Map<String, Object>> getMyDeclarationsByType(
            @PathVariable DeclarationType declarationType,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            String studentNetId = currentUser.getNetId();
            List<MajorMinorDeclaration> declarations = declarationService.getActiveDeclarationsByType(studentNetId, declarationType);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("declarations", declarations);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get declarations by advisor and status
     */
    @GetMapping("/advisor/{advisorNetId}/status/{status}")
    public ResponseEntity<Map<String, Object>> getDeclarationsByAdvisorAndStatus(
            @PathVariable String advisorNetId,
            @PathVariable DeclarationStatus status) {
        
        try {
            List<MajorMinorDeclaration> declarations = declarationService.getDeclarationsByAdvisorAndStatus(advisorNetId, status);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("declarations", declarations);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}

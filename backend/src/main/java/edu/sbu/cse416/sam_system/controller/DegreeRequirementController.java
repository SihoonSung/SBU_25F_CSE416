package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.DegreeRequirement;
import edu.sbu.cse416.sam_system.entity.DegreeType;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.security.CurrentUser;
import edu.sbu.cse416.sam_system.service.DegreeRequirementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TP: Used cursor to implement REST controller for degree requirements management.
 * Controller for degree requirements management.
 */
@RestController
@RequestMapping("/api/degree-requirements")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class DegreeRequirementController {
    
    @Autowired
    private DegreeRequirementService degreeRequirementService;
    
    @Autowired
    private CurrentUser currentUser;
    
    /**
     * Import degree requirements from YAML file (Registrar only)
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importRequirements(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            // Check if user is registrar
            String netId = currentUser.getNetId();
            // Note: Role validation can be added when user service is fully integrated
            
            List<DegreeRequirement> requirements = degreeRequirementService.importDegreeRequirements(file, netId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully imported " + requirements.size() + " degree requirements");
            response.put("requirements", requirements);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get all active degree requirements
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllRequirements() {
        try {
            List<DegreeRequirement> requirements = degreeRequirementService.getAllActiveRequirements();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requirements", requirements);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get degree requirements by subject code
     */
    @GetMapping("/subject/{subjectCode}")
    public ResponseEntity<Map<String, Object>> getRequirementsBySubject(@PathVariable String subjectCode) {
        try {
            List<DegreeRequirement> requirements = degreeRequirementService.getRequirementsBySubject(subjectCode);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requirements", requirements);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get degree requirements by effective term
     */
    @GetMapping("/term/{effectiveTerm}")
    public ResponseEntity<Map<String, Object>> getRequirementsByTerm(@PathVariable String effectiveTerm) {
        try {
            List<DegreeRequirement> requirements = degreeRequirementService.getRequirementsByTerm(effectiveTerm);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requirements", requirements);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get specific degree requirement
     */
    @GetMapping("/{subjectCode}/{degreeType}")
    public ResponseEntity<Map<String, Object>> getRequirement(
            @PathVariable String subjectCode,
            @PathVariable DegreeType degreeType) {
        try {
            Optional<DegreeRequirement> requirement = degreeRequirementService.getRequirement(subjectCode, degreeType);
            
            Map<String, Object> response = new HashMap<>();
            if (requirement.isPresent()) {
                response.put("success", true);
                response.put("requirement", requirement.get());
            } else {
                response.put("success", false);
                response.put("error", "Degree requirement not found");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get restricted degree requirements
     */
    @GetMapping("/restricted")
    public ResponseEntity<Map<String, Object>> getRestrictedRequirements() {
        try {
            List<DegreeRequirement> requirements = degreeRequirementService.getRestrictedRequirements();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requirements", requirements);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Create a new degree requirement (Registrar only)
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createRequirement(
            @RequestBody DegreeRequirement requirement,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            // Check if user is registrar
            String netId = currentUser.getNetId();
            // Note: Role validation can be added when user service is fully integrated
            
            requirement.setCreatedBy(netId);
            DegreeRequirement created = degreeRequirementService.createRequirement(requirement);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Degree requirement created successfully");
            response.put("requirement", created);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Update a degree requirement (Registrar only)
     */
    @PutMapping("/{requirementId}")
    public ResponseEntity<Map<String, Object>> updateRequirement(
            @PathVariable Long requirementId,
            @RequestBody DegreeRequirement requirement,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            // Check if user is registrar
            String netId = currentUser.getNetId();
            // Note: Role validation can be added when user service is fully integrated
            
            DegreeRequirement updated = degreeRequirementService.updateRequirement(requirementId, requirement);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Degree requirement updated successfully");
            response.put("requirement", updated);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Deactivate a degree requirement (Registrar only)
     */
    @DeleteMapping("/{requirementId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateRequirement(
            @PathVariable Long requirementId,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            // Check if user is registrar
            String netId = currentUser.getNetId();
            // Note: Role validation can be added when user service is fully integrated
            
            degreeRequirementService.deactivateRequirement(requirementId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Degree requirement deactivated successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Delete a specific degree requirement (Registrar only)
     */
    @DeleteMapping("/{requirementId}")
    public ResponseEntity<Map<String, Object>> deleteRequirement(
            @PathVariable Long requirementId,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            // Check if user is registrar
            String netId = currentUser.getNetId();
            // Note: Role validation can be added when user service is fully integrated
            
            degreeRequirementService.deleteRequirement(requirementId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Degree requirement deleted successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Delete all degree requirements (Registrar only)
     */
    @DeleteMapping("/delete-all")
    public ResponseEntity<Map<String, Object>> deleteAllRequirements(
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            // Check if user is registrar
            String netId = currentUser.getNetId();
            // Note: Role validation can be added when user service is fully integrated
            
            degreeRequirementService.deleteAllRequirements();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All degree requirements deleted successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}

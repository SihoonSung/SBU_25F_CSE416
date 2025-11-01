package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.DegreeType;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.service.PlannedMajorService;
import edu.sbu.cse416.sam_system.service.DegreeRequirementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for planned major operations.
 * Handles API endpoints for students to plan/unplan majors.
 */
@RestController
@RequestMapping("/api/planned-majors")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class PlannedMajorController {
    
    @Autowired
    private PlannedMajorService plannedMajorService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private DegreeRequirementService degreeRequirementService;
    
    /**
     * Plan a major (add to planned list)
     */
    // TP: Used cursor to plan a major (add to planned list)
    @PostMapping("/plan")
    public ResponseEntity<?> planMajor(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            Long studentId = getUserIdFromPrincipal(principal);
            String subjectCode = (String) request.get("subjectCode");
            String degreeTypeStr = (String) request.get("degreeType");
            
            // Validate required fields
            if (subjectCode == null || subjectCode.trim().isEmpty()) {
                throw new IllegalArgumentException("subjectCode is required");
            }
            if (degreeTypeStr == null || degreeTypeStr.trim().isEmpty()) {
                throw new IllegalArgumentException("degreeType is required");
            }
            
            DegreeType degreeType;
            try {
                degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid degreeType. Must be 'BS', 'BA', 'MINOR', etc.");
            }
            
            var plannedMajor = plannedMajorService.planMajor(studentId, subjectCode, degreeType);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Major planned successfully",
                    "plannedMajor", plannedMajor
            ));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Unplan a major (remove from planned list)
     */
    // TP: Used cursor to unplan a major (remove from planned list)
    @PostMapping("/unplan")
    public ResponseEntity<?> unplanMajor(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            Long studentId = getUserIdFromPrincipal(principal);
            String subjectCode = (String) request.get("subjectCode");
            String degreeTypeStr = (String) request.get("degreeType");
            
            // Validate required fields
            if (subjectCode == null || subjectCode.trim().isEmpty()) {
                throw new IllegalArgumentException("subjectCode is required");
            }
            if (degreeTypeStr == null || degreeTypeStr.trim().isEmpty()) {
                throw new IllegalArgumentException("degreeType is required");
            }
            
            DegreeType degreeType;
            try {
                degreeType = DegreeType.valueOf(degreeTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid degreeType. Must be 'BS', 'BA', 'MINOR', etc.");
            }
            
            plannedMajorService.unplanMajor(studentId, subjectCode, degreeType);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Major unplanned successfully"
            ));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get all planned majors for the current student
     */
    // TP: Used cursor to get all planned majors for the current student
    @GetMapping("/my-planned")
    public ResponseEntity<?> getMyPlannedMajors(@AuthenticationPrincipal OAuth2User principal) {
        try {
            Long studentId = getUserIdFromPrincipal(principal);
            List<Map<String, Object>> plannedMajors = plannedMajorService.getAllPlannedMajorsWithStatus(studentId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "plannedMajors", plannedMajors
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve planned majors: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get status of a specific planned major
     */
    @GetMapping("/status/{subjectCode}/{degreeType}")
    public ResponseEntity<?> getPlannedMajorStatus(
            @PathVariable String subjectCode,
            @PathVariable String degreeType,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            Long studentId = getUserIdFromPrincipal(principal);
            
            DegreeType degreeTypeEnum;
            try {
                degreeTypeEnum = DegreeType.valueOf(degreeType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid degreeType: " + degreeType);
            }
            
            Map<String, Object> status = plannedMajorService.getPlannedMajorStatus(studentId, subjectCode, degreeTypeEnum);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", status
            ));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve status: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Check if student can plan a major
     */
    @GetMapping("/can-plan/{subjectCode}/{degreeType}")
    public ResponseEntity<?> canPlanMajor(
            @PathVariable String subjectCode,
            @PathVariable String degreeType,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            Long studentId = getUserIdFromPrincipal(principal);
            
            DegreeType degreeTypeEnum;
            try {
                degreeTypeEnum = DegreeType.valueOf(degreeType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid degreeType: " + degreeType);
            }
            
            boolean canPlan = plannedMajorService.canPlanMajor(studentId, subjectCode, degreeTypeEnum);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "canPlan", canPlan
            ));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to check if can plan major: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get available subjects for planning (same as declaration)
     */
    @GetMapping("/available-subjects")
    public ResponseEntity<Map<String, Object>> getAvailableSubjects() {
        try {
            List<String> subjects = degreeRequirementService.getAllAvailableSubjects();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "subjects", subjects,
                "totalCount", subjects.size(),
                "note", "Students can plan majors in all subjects for which degree requirements have been imported"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Failed to retrieve available subjects: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get available degree types for a specific subject
     */
    @GetMapping("/available-degree-types/{subjectCode}")
    public ResponseEntity<Map<String, Object>> getAvailableDegreeTypes(@PathVariable String subjectCode) {
        try {
            List<DegreeType> degreeTypes = degreeRequirementService.getAvailableDegreeTypesForSubject(subjectCode);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "subjectCode", subjectCode,
                "degreeTypes", degreeTypes
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Failed to retrieve degree types for " + subjectCode + ": " + e.getMessage()
            ));
        }
    }
    
    /**
     * Extract user ID from OAuth2 principal
     */
    private Long getUserIdFromPrincipal(OAuth2User principal) {
        if (principal == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
        
        // Get Google ID and email from OAuth2 principal
        String googleId = principal.getAttribute("sub");
        String email = principal.getAttribute("email");
        
        if (googleId != null) {
            // Try to find user by Google ID first
            Optional<User> user = userRepository.findByGoogleId(googleId);
            if (user.isPresent()) {
                return user.get().getUserId();
            }
        }
        
        if (email != null) {
            // Try to find user by email
            Optional<User> user = userRepository.findByEmail(email);
            if (user.isPresent()) {
                return user.get().getUserId();
            }
        }
        
        throw new IllegalArgumentException("User not found in system. Please contact administrator.");
    }
}

package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.MajorMinorDeclaration;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.DeclarationType;
import edu.sbu.cse416.sam_system.entity.DeclarationStatus;
import edu.sbu.cse416.sam_system.repository.MajorMinorDeclarationRepository;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.service.DeclarationService;
import edu.sbu.cse416.sam_system.service.MajorMinorDeclarationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import edu.sbu.cse416.sam_system.security.UserPrincipal;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TP: Used cursor to implement REST controller for declaration management.
 * Handles major/minor declaration submission, approval, rejection, and withdrawal.
 */
@CrossOrigin(
    origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:5174", "http://localhost:5175"},
    allowCredentials = "true"
)
@RestController
@RequestMapping("/api/declarations")
public class DeclarationController {

    @Autowired
    private DeclarationService declarationService;
    
    @Autowired
    private MajorMinorDeclarationService majorMinorDeclarationService;

    @Autowired
    private MajorMinorDeclarationRepository declarationRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Get all degree requirements
     */
    @GetMapping("/degree-requirements")
    public ResponseEntity<Map<String, Object>> getDegreeRequirements() {
        try {
            List<Map<String, Object>> requirements = declarationService.getAllDegreeRequirements();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requirements", requirements);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to fetch degree requirements: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Submit a new major/minor declaration
     */
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submitDeclaration(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Object principal) {
        
        try {
            if (principal == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
            }

            User student = getUserFromPrincipal(principal);
            if (!student.isStudent()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only students can submit declarations");
            }

            String subjectCode = (String) request.get("subjectCode");
            String degreeType = (String) request.get("degreeType");
            String declarationType = (String) request.get("declarationType");
            String effectiveTerm = (String) request.get("effectiveTerm");

            if (subjectCode == null || degreeType == null || declarationType == null || effectiveTerm == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required fields");
            }

            // Use the service to submit the declaration (handles all validation and advisor assignment)
            MajorMinorDeclaration savedDeclaration = majorMinorDeclarationService.submitDeclaration(
                student.getUserId(),
                subjectCode,
                DeclarationType.valueOf(declarationType),
                effectiveTerm,
                null  // fromSubjectCode for change requests
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("declarationId", savedDeclaration.getDeclarationId());
            response.put("message", "Declaration submitted successfully");

            return ResponseEntity.ok(response);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to submit declaration: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get student's declarations
     */
    @GetMapping("/my-declarations")
    public ResponseEntity<Map<String, Object>> getMyDeclarations(
            @AuthenticationPrincipal Object principal) {
        
        try {
            if (principal == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
            }

            User student = getUserFromPrincipal(principal);
            List<MajorMinorDeclaration> declarations = declarationRepository.findByStudent(student);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("declarations", declarations);

            return ResponseEntity.ok(response);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to fetch declarations: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get pending declarations (for advisors)
     */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Object>> getPendingDeclarations(
            @AuthenticationPrincipal Object principal) {
        
        try {
            if (principal == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
            }

            User advisor = getUserFromPrincipal(principal);
            if (!advisor.isAdvisor()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only advisors can view pending declarations");
            }

            List<MajorMinorDeclaration> declarations = declarationRepository.findByStatus(DeclarationStatus.PENDING);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("declarations", declarations);

            return ResponseEntity.ok(response);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to fetch pending declarations: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get all declarations (for advisors)
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllDeclarations(
            @AuthenticationPrincipal Object principal) {
        
        try {
            if (principal == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
            }

            User advisor = getUserFromPrincipal(principal);
            if (!advisor.isAdvisor()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only advisors can view all declarations");
            }

            List<MajorMinorDeclaration> declarations = declarationRepository.findAll();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("declarations", declarations);

            return ResponseEntity.ok(response);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to fetch declarations: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Approve a declaration
     */
    @PostMapping("/{declarationId}/approve")
    public ResponseEntity<Map<String, Object>> approveDeclaration(
            @PathVariable Long declarationId,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Object principal) {
        
        try {
            if (principal == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
            }

            User advisor = getUserFromPrincipal(principal);
            if (!advisor.isAdvisor()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only advisors can approve declarations");
            }

            String comments = (String) request.get("comments");
            
            // Use the service to approve and update student majors/minors
            MajorMinorDeclaration declaration = majorMinorDeclarationService.approveDeclaration(
                    declarationId, advisor.getUserId(), comments);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Declaration approved successfully");
            response.put("declaration", declaration);

            return ResponseEntity.ok(response);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to approve declaration: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Reject a declaration
     */
    @PostMapping("/{declarationId}/reject")
    public ResponseEntity<Map<String, Object>> rejectDeclaration(
            @PathVariable Long declarationId,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Object principal) {
        
        try {
            if (principal == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
            }

            User advisor = getUserFromPrincipal(principal);
            if (!advisor.isAdvisor()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only advisors can reject declarations");
            }

            String comments = (String) request.get("comments");
            if (comments == null || comments.trim().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comments are required for rejection");
            }

            // Use the service to reject
            MajorMinorDeclaration declaration = majorMinorDeclarationService.rejectDeclaration(
                    declarationId, advisor.getUserId(), comments);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Declaration rejected");
            response.put("declaration", declaration);

            return ResponseEntity.ok(response);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to reject declaration: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Withdraw a declaration
     */
    @PostMapping("/{declarationId}/withdraw")
    public ResponseEntity<Map<String, Object>> withdrawDeclaration(
            @PathVariable Long declarationId,
            @AuthenticationPrincipal Object principal) {
        
        try {
            if (principal == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
            }

            User student = getUserFromPrincipal(principal);
            if (!student.isStudent()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only students can withdraw declarations");
            }

            // Use the service to withdraw declaration
            majorMinorDeclarationService.withdrawDeclaration(declarationId, student.getUserId());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Declaration withdrawn successfully");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to withdraw declaration: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Extract user from authentication principal (supports both OAuth2User and UserPrincipal for bypass mode)
     */
    private User getUserFromPrincipal(Object principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        
        // Handle bypass mode authentication (UserPrincipal)
        if (principal instanceof UserPrincipal userPrincipal) {
            User user = userPrincipal.getUser();
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            return user;
        }
        
        // Handle OAuth2 authentication (OAuth2User)
        if (principal instanceof OAuth2User oauth2User) {
            String googleId = oauth2User.getName();
            Optional<User> userOpt = userRepository.findByGoogleId(googleId);
            if (userOpt.isEmpty()) {
                // Try email as fallback
                String email = oauth2User.getAttribute("email");
                if (email != null) {
                    userOpt = userRepository.findByEmail(email);
                }
            }
            if (userOpt.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }
            return userOpt.get();
        }
        
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unsupported authentication principal type");
    }
}

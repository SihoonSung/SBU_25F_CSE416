package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.MajorMinorDeclaration;
import edu.sbu.cse416.sam_system.entity.DeclarationStatus;
import edu.sbu.cse416.sam_system.entity.DeclarationType;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.service.MajorMinorDeclarationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for major/minor declaration requests.
 * Handles API endpoints for students and advisors.
 */
@RestController
@RequestMapping("/api/major-minor-declarations")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class MajorMinorDeclarationController {
    
    @Autowired
    private MajorMinorDeclarationService declarationService;
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Test endpoint to check authentication status
     */
    @GetMapping("/debug/auth-test")
    public ResponseEntity<?> testAuth(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.ok(Map.of(
                "authenticated", false,
                "message", "Not authenticated"
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "attributes", principal.getAttributes(),
                "name", principal.getAttribute("name"),
                "email", principal.getAttribute("email")
            ));
        }
    }
    
    /**
     * Simple test endpoint without database
     */
    @GetMapping("/debug/simple")
    public ResponseEntity<?> simpleTest() {
        System.out.println("DEBUG: Simple test endpoint called");
        return ResponseEntity.ok(Map.of(
            "message", "Simple test successful",
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * Submit a new major/minor declaration request (Student only)
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitDeclaration(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            // Debug: Check if principal is null
            System.out.println("DEBUG: Principal is null: " + (principal == null));
            if (principal != null) {
                System.out.println("DEBUG: Principal attributes: " + principal.getAttributes());
            }
            
            // Extract user ID from OAuth2 principal
            Long studentId;
            if (principal != null) {
                studentId = getUserIdFromPrincipal(principal);
            } else {
                // For testing purposes, use a default user ID
                System.out.println("DEBUG: Using default user ID for testing");
                
                // Check if user exists in database
                System.out.println("DEBUG: Looking for user with ID 1...");
                Optional<User> testUser = userRepository.findById(1L);
                System.out.println("DEBUG: User found: " + testUser.isPresent());
                if (testUser.isPresent()) {
                    User user = testUser.get();
                    System.out.println("DEBUG: Found user with ID 1: " + user.getFirstName() + " " + user.getLastName() + ", Role: " + user.getRole());
                    
                    // Check if user is a student, if not find a student
                    if (user.getRole() == edu.sbu.cse416.sam_system.entity.UserRole.STUDENT) {
                        studentId = 1L;
                    } else {
                        // Find a student user
                        List<User> students = userRepository.findByRole(edu.sbu.cse416.sam_system.entity.UserRole.STUDENT);
                        if (!students.isEmpty()) {
                            studentId = students.get(0).getUserId();
                            System.out.println("DEBUG: Using first student user ID: " + studentId + ", Name: " + students.get(0).getFirstName() + " " + students.get(0).getLastName());
                        } else {
                            throw new IllegalArgumentException("No student users found in database. Please import user data with STUDENT role.");
                        }
                    }
                } else {
                    // Try to find any student user
                    System.out.println("DEBUG: Looking for student users...");
                    List<User> students = userRepository.findByRole(edu.sbu.cse416.sam_system.entity.UserRole.STUDENT);
                    System.out.println("DEBUG: Found " + students.size() + " student users");
                    
                    if (!students.isEmpty()) {
                        studentId = students.get(0).getUserId();
                        System.out.println("DEBUG: Using first student user ID: " + studentId + ", Name: " + students.get(0).getFirstName() + " " + students.get(0).getLastName());
                    } else {
                        // List all users to see what roles exist
                        List<User> allUsers = userRepository.findAll();
                        System.out.println("DEBUG: Total users in database: " + allUsers.size());
                        for (User user : allUsers) {
                            System.out.println("DEBUG: User ID " + user.getUserId() + ", Name: " + user.getFirstName() + " " + user.getLastName() + ", Role: " + user.getRole());
                        }
                        throw new IllegalArgumentException("No student users found in database. Please import user data with STUDENT role.");
                    }
                }
            }
            
            String subjectCode = (String) request.get("subjectCode");
            String declarationTypeStr = (String) request.get("declarationType");
            String effectiveTerm = (String) request.get("effectiveTerm");
            
            // Validate required fields
            if (subjectCode == null || subjectCode.trim().isEmpty()) {
                throw new IllegalArgumentException("subjectCode is required");
            }
            if (declarationTypeStr == null || declarationTypeStr.trim().isEmpty()) {
                throw new IllegalArgumentException("declarationType is required");
            }
            if (effectiveTerm == null || effectiveTerm.trim().isEmpty()) {
                throw new IllegalArgumentException("effectiveTerm is required");
            }
            
            DeclarationType declarationType;
            try {
                declarationType = DeclarationType.valueOf(declarationTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid declarationType. Must be 'MAJOR' or 'MINOR'");
            }
            
            MajorMinorDeclaration declaration = declarationService.submitDeclaration(
                    studentId, subjectCode, declarationType, effectiveTerm);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Declaration submitted successfully",
                    "declarationId", declaration.getDeclarationId()
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
     * Get all declarations for the current student
     */
    @GetMapping("/my-declarations")
    public ResponseEntity<?> getMyDeclarations(@AuthenticationPrincipal OAuth2User principal) {
        try {
            Long studentId = getUserIdFromPrincipal(principal);
            List<MajorMinorDeclaration> declarations = declarationService.getStudentDeclarations(studentId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "declarations", declarations
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve declarations: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get pending declarations for advisor
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingDeclarations(@AuthenticationPrincipal OAuth2User principal) {
        try {
            Long advisorId = getUserIdFromPrincipal(principal);
            List<MajorMinorDeclaration> declarations = declarationService.getPendingDeclarationsForAdvisor(advisorId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "declarations", declarations
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve pending declarations: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Approve a declaration request (Advisor only)
     */
    @PostMapping("/{declarationId}/approve")
    public ResponseEntity<?> approveDeclaration(
            @PathVariable Long declarationId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            Long advisorId = getUserIdFromPrincipal(principal);
            String notes = request.get("notes");
            
            MajorMinorDeclaration declaration = declarationService.approveDeclaration(
                    declarationId, advisorId, notes);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Declaration approved successfully",
                    "declaration", declaration
            ));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to approve declaration: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Reject a declaration request (Advisor only)
     */
    @PostMapping("/{declarationId}/reject")
    public ResponseEntity<?> rejectDeclaration(
            @PathVariable Long declarationId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal OAuth2User principal) {
        
        try {
            Long advisorId = getUserIdFromPrincipal(principal);
            String notes = request.get("notes");
            
            MajorMinorDeclaration declaration = declarationService.rejectDeclaration(
                    declarationId, advisorId, notes);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Declaration rejected",
                    "declaration", declaration
            ));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to reject declaration: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get all declarations (for advisors)
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllDeclarations(@AuthenticationPrincipal OAuth2User principal) {
        try {
            Long advisorId = getUserIdFromPrincipal(principal);
            List<MajorMinorDeclaration> declarations = declarationService.getAllDeclarationsForAdvisor(advisorId);
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "declarations", declarations
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve declarations: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Withdraw a declaration (Student only)
     */
    @PostMapping("/{declarationId}/withdraw")
    public ResponseEntity<?> withdrawDeclaration(@PathVariable Long declarationId,
                                                @AuthenticationPrincipal OAuth2User principal) {
        try {
            Long studentId = getUserIdFromPrincipal(principal);
            MajorMinorDeclaration declaration = declarationService.withdrawDeclaration(declarationId, studentId);
            
            if (declaration == null) {
                // PENDING declaration was deleted from database
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Declaration deleted successfully"
                ));
            } else {
                // APPROVED/REJECTED declaration marked as WITHDRAWN
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Declaration withdrawn successfully",
                        "declaration", declaration
                ));
            }
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to withdraw declaration: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get declaration details by ID
     */
    @GetMapping("/{declarationId}")
    public ResponseEntity<?> getDeclaration(@PathVariable Long declarationId,
                                          @AuthenticationPrincipal OAuth2User principal) {
        try {
            Optional<MajorMinorDeclaration> declaration = declarationService.getDeclarationById(declarationId);
            
            if (declaration.isPresent()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "declaration", declaration.get()
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve declaration: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get available subjects for declaration
     */
    @GetMapping("/available-subjects")
    public ResponseEntity<?> getAvailableSubjects() {
        try {
            // Return list of available majors and minors
            // This could be fetched from degree requirements or a separate subjects table
            List<Map<String, Object>> subjects = List.of(
                    Map.of("code", "CSE", "name", "Computer Science", "type", "MAJOR", "restricted", true),
                    Map.of("code", "PSY", "name", "Psychology", "type", "MAJOR", "restricted", false),
                    Map.of("code", "BIO", "name", "Biology", "type", "MAJOR", "restricted", false),
                    Map.of("code", "AMS", "name", "Applied Mathematics and Statistics", "type", "MAJOR", "restricted", false),
                    Map.of("code", "POL", "name", "Political Science", "type", "MAJOR", "restricted", false),
                    Map.of("code", "ECO", "name", "Economics", "type", "MAJOR", "restricted", false)
            );
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "subjects", subjects
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve available subjects: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get student's current major/minor status
     */
    @GetMapping("/my-status")
    public ResponseEntity<?> getMyStatus(@AuthenticationPrincipal OAuth2User principal) {
        try {
            Long studentId = getUserIdFromPrincipal(principal);
            List<MajorMinorDeclaration> declarations = declarationService.getStudentDeclarations(studentId);
            
            // Count approved majors and minors
            long approvedMajors = declarations.stream()
                    .filter(d -> d.getDeclarationType() == DeclarationType.MAJOR)
                    .filter(MajorMinorDeclaration::isApproved)
                    .count();
            
            long approvedMinors = declarations.stream()
                    .filter(d -> d.getDeclarationType() == DeclarationType.MINOR)
                    .filter(MajorMinorDeclaration::isApproved)
                    .count();
            
            long pendingMajors = declarations.stream()
                    .filter(d -> d.getDeclarationType() == DeclarationType.MAJOR)
                    .filter(MajorMinorDeclaration::isPending)
                    .count();
            
            long pendingMinors = declarations.stream()
                    .filter(d -> d.getDeclarationType() == DeclarationType.MINOR)
                    .filter(MajorMinorDeclaration::isPending)
                    .count();
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "status", Map.of(
                            "approvedMajors", approvedMajors,
                            "approvedMinors", approvedMinors,
                            "pendingMajors", pendingMajors,
                            "pendingMinors", pendingMinors,
                            "maxMajors", 2,
                            "maxMinors", 3
                    )
            ));
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to retrieve status: " + e.getMessage()
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
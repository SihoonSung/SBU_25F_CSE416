package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.MajorMinorDeclaration;
import edu.sbu.cse416.sam_system.entity.DeclarationStatus;
import edu.sbu.cse416.sam_system.entity.DeclarationType;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.DegreeType;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.service.MajorMinorDeclarationService;
import edu.sbu.cse416.sam_system.service.DegreeRequirementService;
import edu.sbu.cse416.sam_system.security.UserPrincipal;
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
 * TP: Used cursor to implement REST controller for major/minor declaration requests.
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
    
    @Autowired
    private DegreeRequirementService degreeRequirementService;
    
    /**
     * Test endpoint to check authentication status
     */
    @GetMapping("/debug/auth-test")
    // TP: Used cursor to verify OAuth2 authentication context (supports both OAuth2User and UserPrincipal)
    public ResponseEntity<?> testAuth(@AuthenticationPrincipal Object principal) {
        if (principal == null) {
            return ResponseEntity.ok(Map.of(
                "authenticated", false,
                "message", "Not authenticated"
            ));
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", true);
            response.put("principalType", principal.getClass().getName());
            
            // Handle bypass mode authentication (UserPrincipal)
            if (principal instanceof UserPrincipal userPrincipal) {
                User user = userPrincipal.getUser();
                response.put("userId", user != null ? user.getUserId() : null);
                response.put("email", user != null ? user.getEmail() : null);
                response.put("name", user != null ? user.getFullName() : null);
                response.put("role", user != null ? user.getRole() : null);
            }
            // Handle OAuth2 authentication (OAuth2User)
            else if (principal instanceof OAuth2User oauth2User) {
                response.put("attributes", oauth2User.getAttributes());
                response.put("name", oauth2User.getAttribute("name"));
                response.put("email", oauth2User.getAttribute("email"));
            }
            
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * Simple test endpoint without database
     */
    @GetMapping("/debug/simple")
    // TP: Used cursor to provide simple debug endpoint
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
    // TP: Used cursor to submit a declaration via REST
    public ResponseEntity<?> submitDeclaration(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Object principal) {
        
        try {
            // Debug: Check if principal is null
            System.out.println("DEBUG: Principal is null: " + (principal == null));
            if (principal != null) {
                System.out.println("DEBUG: Principal type: " + principal.getClass().getName());
            }
            
            // Extract user ID from authentication principal (OAuth2User or UserPrincipal)
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
            String fromSubjectCode = (String) request.get("fromSubjectCode"); // Optional: for change requests
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
                    studentId, subjectCode, declarationType, effectiveTerm, fromSubjectCode);
            
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
    // TP: Used cursor to list current student's declarations
    public ResponseEntity<?> getMyDeclarations(@AuthenticationPrincipal Object principal) {
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
    // TP: Used cursor to list advisor's pending declarations
    public ResponseEntity<?> getPendingDeclarations(@AuthenticationPrincipal Object principal) {
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
    // TP: Used cursor to approve a declaration request
    public ResponseEntity<?> approveDeclaration(
            @PathVariable Long declarationId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Object principal) {
        
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
    // TP: Used cursor to reject a declaration request
    public ResponseEntity<?> rejectDeclaration(
            @PathVariable Long declarationId,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Object principal) {
        
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
    // TP: Used cursor to list all declarations assigned to advisor
    public ResponseEntity<?> getAllDeclarations(@AuthenticationPrincipal Object principal) {
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
    // TP: Used cursor to withdraw a student's pending declaration
    public ResponseEntity<?> withdrawDeclaration(@PathVariable Long declarationId,
                                                @AuthenticationPrincipal Object principal) {
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
    // TP: Used cursor to fetch declaration details by ID
    public ResponseEntity<?> getDeclaration(@PathVariable Long declarationId,
                                          @AuthenticationPrincipal Object principal) {
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
     * Get student's current major/minor status
     */
    @GetMapping("/my-status")
    // TP: Used cursor to summarize student's declaration status
    public ResponseEntity<?> getMyStatus(@AuthenticationPrincipal Object principal) {
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
     * Extract user ID from authentication principal (supports both OAuth2User and UserPrincipal for bypass mode)
     */
    private Long getUserIdFromPrincipal(Object principal) {
        // TP: Used cursor to resolve user id from OAuth2 principal or bypass mode UserPrincipal
        if (principal == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
        
        // Handle bypass mode authentication (UserPrincipal)
        if (principal instanceof UserPrincipal userPrincipal) {
            User user = userPrincipal.getUser();
            if (user != null && user.getUserId() != null) {
                return user.getUserId();
            }
            throw new IllegalArgumentException("User not found in system. Please contact administrator.");
        }
        
        // Handle OAuth2 authentication (OAuth2User)
        if (principal instanceof OAuth2User oauth2User) {
            // Get Google ID and email from OAuth2 principal
            String googleId = oauth2User.getAttribute("sub");
            String email = oauth2User.getAttribute("email");
            
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
        
        throw new IllegalArgumentException("Unsupported authentication principal type");
    }
    
    /**
     * Get all available subjects from degree requirements
     */
    @GetMapping("/available-subjects")
    public ResponseEntity<Map<String, Object>> getAvailableSubjects() {
        try {
            List<String> subjects = degreeRequirementService.getAllAvailableSubjects();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "subjects", subjects,
                "totalCount", subjects.size(),
                "note", "SAM supports all subjects for which degree requirements have been imported"
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
     * Get detailed course requirements for a specific subject and degree type
     */
    @GetMapping("/course-requirements/{subjectCode}/{degreeType}")
    public ResponseEntity<Map<String, Object>> getCourseRequirements(
            @PathVariable String subjectCode, 
            @PathVariable String degreeType) {
        try {
            DegreeType type = DegreeType.valueOf(degreeType.toUpperCase());
            Map<String, Object> requirements = degreeRequirementService.getDetailedCourseRequirements(subjectCode, type);
            
            if (requirements.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "requirements", requirements
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Invalid degree type: " + degreeType
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Failed to retrieve course requirements: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get all available terms from academic calendar
     */
    @GetMapping("/available-terms")
    public ResponseEntity<Map<String, Object>> getAvailableTerms() {
        try {
            List<Map<String, Object>> terms = declarationService.getAvailableTermsFromCalendar();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "terms", terms
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Failed to retrieve available terms: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get declaration dates (begin and end) for a specific term
     */
    @GetMapping("/declaration-dates/{semester}/{year}")
    public ResponseEntity<Map<String, Object>> getDeclarationDates(
            @PathVariable String semester,
            @PathVariable Integer year) {
        try {
            java.util.Optional<java.time.LocalDate> beginDate = declarationService.getMajorMinorChangesBegin(semester, year);
            java.util.Optional<java.time.LocalDate> endDate = declarationService.getMajorMinorChangesEnd(semester, year);
            
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("success", true);
            response.put("semester", semester);
            response.put("year", year);
            response.put("beginDate", beginDate.orElse(null));
            response.put("endDate", endDate.orElse(null));
            response.put("hasPeriod", beginDate.isPresent() && endDate.isPresent());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Failed to retrieve declaration dates: " + e.getMessage()
            ));
        }
    }
}
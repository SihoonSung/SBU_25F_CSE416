package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * TP: Used cursor to implement authentication-related endpoints.
 * Controller for authentication-related endpoints.
 * Handles Google OAuth2 login/logout and user session management.
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(
        origins = {"http://localhost:8000", "http://localhost:3000"},
        allowCredentials = "true"
)
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * OAuth2 login success endpoint
     */
    @GetMapping("/success")
    // TP: Used cursor to handle OAuth2 login success and return user payload
    public ResponseEntity<Map<String, Object>> loginSuccess(
            @AuthenticationPrincipal OidcUser principal,
            HttpServletRequest request) {

        if (principal == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Authentication failed"));
        }

        // Find or create user from OAuth2 data
        User user = userService.findOrCreateUserFromOAuth2(principal);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", Map.of(
                "userId", user.getUserId(),
                "email", user.getEmail(),
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "fullName", user.getFullName(),
                "role", user.getRole().toString(),
                "profilePictureUrl", user.getProfilePictureUrl(),
                "isActive", user.getIsActive()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * OAuth2 login failure endpoint
     */
    @GetMapping("/failure")
    // TP: Used cursor to report OAuth2 login failure
    public ResponseEntity<Map<String, Object>> loginFailure() {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Authentication failed"
        ));
    }

    /**
     * Get current user information
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    // TP: Used cursor to return current authenticated user's info
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal OidcUser principal) {

        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        String googleId = principal.getSubject();
        
        Optional<User> userOpt = userService.getUserByGoogleId(googleId);

        if (userOpt.isEmpty()) {
            // Try to find user by email as fallback
            String email = principal.getAttribute("email");
            
            if (email != null) {
                userOpt = userService.getUserByEmail(email);
            }
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
        }

        User user = userOpt.get();
        
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", user.getUserId());
        userData.put("email", user.getEmail());
        userData.put("firstName", user.getFirstName());
        userData.put("lastName", user.getLastName());
        userData.put("fullName", user.getFullName());
        userData.put("role", user.getRole().toString());
        userData.put("profilePictureUrl", user.getProfilePictureUrl());
        userData.put("isActive", user.getIsActive());
        userData.put("studentId", user.getStudentId());
        userData.put("major", user.getMajor());
        userData.put("gpa", user.getGpa());
        userData.put("graduationYear", user.getGraduationYear());
        userData.put("department", user.getDepartment());
        userData.put("officeLocation", user.getOfficeLocation());
        userData.put("officeHours", user.getOfficeHours());
        userData.put("sbuId", user.getSbuId()); // Add SBU ID for debugging
        userData.put("level", user.getLevel()); // Add level for advisors
        userData.put("academic_unit", user.getAcademicUnit()); // Add academic_unit for advisors

        return ResponseEntity.ok(Map.of("user", userData));
    }

    /**
     * Logout endpoint (session invalidation)
     */
    @PostMapping("/logout")
    // TP: Used cursor to invalidate session and logout
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        request.getSession().invalidate();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Logged out successfully"
        ));
    }

    /**
     * Logout success redirect endpoint
     */
    @GetMapping("/logout-success")
    // TP: Used cursor to acknowledge logout success
    public ResponseEntity<Map<String, Object>> logoutSuccess() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Logged out successfully"
        ));
    }

    /**
     * Check authentication status
     */
    @GetMapping("/status")
    // TP: Used cursor to check authentication status
    public ResponseEntity<Map<String, Object>> checkAuthStatus(
            @AuthenticationPrincipal OidcUser principal) {

        Map<String, Object> response = new HashMap<>();

        if (principal != null) {
            response.put("authenticated", true);
            response.put("user", Map.of(
                    "email", principal.getEmail(),
                    "name", principal.getFullName()
            ));
        } else {
            response.put("authenticated", false);
        }

        return ResponseEntity.ok(response);
    }
}
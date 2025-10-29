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
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal OidcUser principal) {

        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        String googleId = principal.getSubject();
        
        // Debug: Log OAuth2 principal information
        System.out.println("DEBUG: OAuth2 Principal Subject: " + googleId);
        System.out.println("DEBUG: OAuth2 Principal Name: " + principal.getName());
        System.out.println("DEBUG: OAuth2 Principal Attributes: " + principal.getAttributes());
        
        Optional<User> userOpt = userService.getUserByGoogleId(googleId);

        if (userOpt.isEmpty()) {
            // Debug: Try to find user by email as fallback
            String email = principal.getAttribute("email");
            System.out.println("DEBUG: User not found by Google ID, trying email: " + email);
            
            if (email != null) {
                userOpt = userService.getUserByEmail(email);
                if (userOpt.isPresent()) {
                    System.out.println("DEBUG: Found user by email: " + userOpt.get().getFirstName() + " " + userOpt.get().getLastName());
                }
            }
            
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }
        }

        User user = userOpt.get();
        System.out.println("DEBUG: Returning user: " + user.getFirstName() + " " + user.getLastName() + " (SBU ID: " + user.getSbuId() + ")");
        
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

        return ResponseEntity.ok(Map.of("user", userData));
    }

    /**
     * Logout endpoint (session invalidation)
     */
    @PostMapping("/logout")
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
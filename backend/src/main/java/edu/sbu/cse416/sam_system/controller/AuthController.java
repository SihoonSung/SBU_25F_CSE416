package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for authentication-related endpoints.
 * Handles Google OAuth2 login/logout and user session management.
 * 
 * TP: Used Cursor to implement authentication controller
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * Handle successful OAuth2 login
     */
    @GetMapping("/success")
    public ResponseEntity<Map<String, Object>> loginSuccess(
            @AuthenticationPrincipal OAuth2User principal,
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
     * Handle failed OAuth2 login
     */
    @GetMapping("/failure")
    public ResponseEntity<Map<String, Object>> loginFailure() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "Authentication failed");
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Get current user information
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (principal == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not authenticated"));
        }

        String googleId = principal.getName();
        var userOpt = userService.getUserByGoogleId(googleId);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();
        Map<String, Object> response = new HashMap<>();
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
        
        response.put("user", userData);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Handle logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest request, 
            HttpServletResponse response) throws IOException {
        
        // Clear session
        request.getSession().invalidate();
        
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("success", true);
        responseBody.put("message", "Logged out successfully");
        
        return ResponseEntity.ok(responseBody);
    }

    /**
     * Handle logout success redirect
     */
    @GetMapping("/logout-success")
    public ResponseEntity<Map<String, Object>> logoutSuccess() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Check authentication status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> checkAuthStatus(
            @AuthenticationPrincipal OAuth2User principal) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (principal != null) {
            response.put("authenticated", true);
            response.put("user", Map.of(
                "email", principal.getAttribute("email"),
                "name", principal.getAttribute("name")
            ));
        } else {
            response.put("authenticated", false);
        }
        
        return ResponseEntity.ok(response);
    }
}

package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for user management operations.
 * Provides endpoints for user search, management, and YAML import.
 * 
 * TP: Used Cursor to implement user management controller
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * Get all users (for registrar)
     */
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers(
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }
        
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    /**
     * Get user by ID
     */
    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserById(
            @PathVariable Long userId,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }
        
        Optional<User> user = userService.getUserById(userId);
        return user.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Search users by name
     */
    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String department,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }
        
        List<User> users;
        
        if (name != null && !name.trim().isEmpty()) {
            if (role != null && !role.trim().isEmpty()) {
                try {
                    UserRole userRole = UserRole.valueOf(role.toUpperCase());
                    users = userService.searchUsersByNameAndRole(name, userRole);
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().build();
                }
            } else {
                users = userService.searchUsersByName(name);
            }
        } else if (email != null && !email.trim().isEmpty()) {
            users = userService.searchUsersByEmailDomain(email);
        } else if (major != null && !major.trim().isEmpty()) {
            users = userService.searchStudentsByMajor(major);
        } else if (department != null && !department.trim().isEmpty()) {
            users = userService.searchInstructorsByDepartment(department);
        } else {
            users = userService.getAllUsers();
        }
        
        return ResponseEntity.ok(users);
    }

    /**
     * Get users by role
     */
    @GetMapping("/role/{role}")
    public ResponseEntity<List<User>> getUsersByRole(
            @PathVariable String role,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }
        
        try {
            UserRole userRole = UserRole.valueOf(role.toUpperCase());
            List<User> users = userService.getUsersByRole(userRole);
            return ResponseEntity.ok(users);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update user information
     */
    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUser(
            @PathVariable Long userId,
            @RequestBody User userUpdate,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }
        
        Optional<User> existingUser = userService.getUserById(userId);
        if (existingUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        User user = existingUser.get();
        
        // Update allowed fields
        if (userUpdate.getFirstName() != null) {
            user.setFirstName(userUpdate.getFirstName());
        }
        if (userUpdate.getLastName() != null) {
            user.setLastName(userUpdate.getLastName());
        }
        if (userUpdate.getRole() != null) {
            user.setRole(userUpdate.getRole());
        }
        if (userUpdate.getStudentId() != null) {
            user.setStudentId(userUpdate.getStudentId());
        }
        if (userUpdate.getMajor() != null) {
            user.setMajor(userUpdate.getMajor());
        }
        if (userUpdate.getGpa() != null) {
            user.setGpa(userUpdate.getGpa());
        }
        if (userUpdate.getGraduationYear() != null) {
            user.setGraduationYear(userUpdate.getGraduationYear());
        }
        if (userUpdate.getDepartment() != null) {
            user.setDepartment(userUpdate.getDepartment());
        }
        if (userUpdate.getOfficeLocation() != null) {
            user.setOfficeLocation(userUpdate.getOfficeLocation());
        }
        if (userUpdate.getOfficeHours() != null) {
            user.setOfficeHours(userUpdate.getOfficeHours());
        }
        
        User updatedUser = userService.updateUser(user);
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * Deactivate user
     */
    @PutMapping("/{userId}/deactivate")
    public ResponseEntity<User> deactivateUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }
        
        try {
            User user = userService.deactivateUser(userId);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Activate user
     */
    @PutMapping("/{userId}/activate")
    public ResponseEntity<User> activateUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }
        
        try {
            User user = userService.activateUser(userId);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Import users from YAML file
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importUsers(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        
        try {
            List<User> importedUsers = userService.importUsersFromYaml(file);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("importedCount", importedUsers.size());
            response.put("users", importedUsers);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to process file: " + e.getMessage()));
        }
    }

    /**
     * Get user statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getUserStatistics(
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }
        
        Map<String, Object> stats = userService.getUserStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Check if current user is registrar
     */
    private boolean isRegistrar(OAuth2User principal) {
        if (principal == null) {
            return false;
        }
        
        String googleId = principal.getName();
        var userOpt = userService.getUserByGoogleId(googleId);
        
        return userOpt.isPresent() && userOpt.get().isRegistrar();
    }
}

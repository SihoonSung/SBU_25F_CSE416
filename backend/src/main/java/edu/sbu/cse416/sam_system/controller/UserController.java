package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.dto.StudentProfileDto;
import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.entity.UserRole;
import edu.sbu.cse416.sam_system.repository.UserRepository;
import edu.sbu.cse416.sam_system.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

@CrossOrigin(
    origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:5174", "http://localhost:5175"},
    allowCredentials = "true"
)
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserService userService;

    public UserController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    /** Test endpoint to verify controller is loaded */
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("UserController is working!");
    }


    /**
     * Lookup by email or studentId from users table only.
     *   /api/users/student-lookup?email=abc@xyz.com
     *   /api/users/student-lookup?studentId=1100123456
     * If both are provided, email wins.
     */
    @GetMapping("/student-lookup")
    @PreAuthorize("permitAll()")
    public StudentProfileDto lookupStudent(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "studentId", required = false) String studentId
    ) {
        Optional<User> u;
        if (email != null && !email.isBlank()) {
            u = userRepository.findByEmailIgnoreCase(email.trim());
        } else if (studentId != null && !studentId.isBlank()) {
            u = userRepository.findByStudentId(studentId.trim());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email or studentId is required");
        }

        User user = u.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));

        return toStudentProfileDto(user);
    }

    // ---------- mapper ----------
    private static StudentProfileDto toStudentProfileDto(User u) {
        StudentProfileDto d = new StudentProfileDto();

        // internal id: try getId() -> getUserId() -> getUid(); else 0L
        d.id = safeInternalId(u);

        d.studentId = nvl(callString(u, "getStudentId"), "-");

        String first = nvl(callString(u, "getFirstName"), "");
        String last  = nvl(callString(u, "getLastName"), "");
        String name = (first + " " + last).trim();
        d.name = name.isEmpty() ? "-" : name;

        d.major = nvl(callString(u, "getMajor"), "-");
        d.email = nvl(callString(u, "getEmail"), "-");

        // Not present on User -> default values
        d.status = "-";
        d.entryYear = "-";

        return d;
    }

    // ---------- helpers (no dependency on missing getters) ----------
    private static Long safeInternalId(User u) {
        Long v = callLong(u, "getId");
        if (v != null) return v;
        v = callLong(u, "getUserId");
        if (v != null) return v;
        v = callLong(u, "getUid");
        if (v != null) return v;
        return 0L;
    }

    private static String nvl(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }

    private static String callString(Object target, String getter) {
        try {
            Method m = target.getClass().getMethod(getter);
            Object out = m.invoke(target);
            return (out == null) ? null : String.valueOf(out);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Long callLong(Object target, String getter) {
        try {
            Method m = target.getClass().getMethod(getter);
            Object out = m.invoke(target);
            if (out == null) return null;
            if (out instanceof Long) return (Long) out;
            if (out instanceof Number) return ((Number) out).longValue();
            String s = String.valueOf(out);
            return s.matches("\\d+") ? Long.parseLong(s) : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    // ================= Registrar/Admin APIs =================

    /**
     * Get all users (for registrar)
     */
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers(
            @AuthenticationPrincipal OAuth2User principal) {
        
        // Temporarily disable authorization for testing
        // if (!isRegistrar(principal)) {
        //     return ResponseEntity.status(403).build();
        // }
        
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    /** Activate user */
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

    /** Import users from YAML file */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importUsers(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal OAuth2User principal) {

        // Temporarily disable authorization for testing
        // if (!isRegistrar(principal)) {
        //     return ResponseEntity.status(403).build();
        // }

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

    /** Get user statistics */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getUserStatistics(
            @AuthenticationPrincipal OAuth2User principal) {

        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> stats = userService.getUserStatistics();
        return ResponseEntity.ok(stats);
    }

    /** Search users with multiple filters (for registrars and advisors) */
    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "major", required = false) String major,
            @RequestParam(value = "minor", required = false) String minor,
            @RequestParam(value = "graduationTerm", required = false) String graduationTerm,
            @RequestParam(value = "advisorLevel", required = false) String advisorLevel,
            @RequestParam(value = "academicUnit", required = false) String academicUnit,
            @AuthenticationPrincipal OAuth2User principal) {
        
        // Temporarily disable authorization for testing
        // if (!isRegistrar(principal) && !isAdvisor(principal)) {
        //     return ResponseEntity.status(403).build();
        // }
        
        List<User> users = userService.searchUsers(name, role, major, minor, 
                                                 graduationTerm, advisorLevel, academicUnit);
        return ResponseEntity.ok(users);
    }

    /** Export user data as YAML (for registrars only) */
    @GetMapping("/{userId}/export")
    public ResponseEntity<Map<String, Object>> exportUserData(
            @PathVariable Long userId,
            @AuthenticationPrincipal OAuth2User principal) {
        
        if (!isRegistrar(principal)) {
            return ResponseEntity.status(403).build();
        }
        
        try {
            Map<String, Object> userData = userService.exportUserToYamlFormat(userId);
            if (userData == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(userData);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to export user data: " + e.getMessage()));
        }
    }

    /** Check if current user is registrar */
    private boolean isRegistrar(OAuth2User principal) {
        if (principal == null) return false;
        String googleId = principal.getName();
        var userOpt = userService.getUserByGoogleId(googleId);
        return userOpt.isPresent() && userOpt.get().isRegistrar();
    }

    /** Check if current user is advisor */
    private boolean isAdvisor(OAuth2User principal) {
        if (principal == null) return false;
        String googleId = principal.getName();
        var userOpt = userService.getUserByGoogleId(googleId);
        return userOpt.isPresent() && userOpt.get().isAdvisor();
    }
}
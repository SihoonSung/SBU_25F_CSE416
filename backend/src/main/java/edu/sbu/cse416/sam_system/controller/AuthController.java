package edu.sbu.cse416.sam_system.controller;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.security.UserPrincipal;
import edu.sbu.cse416.sam_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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
    
    @Autowired
    private Environment environment;

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
     * Supports both OAuth2 (OidcUser) and bypass mode (UserPrincipal) authentication
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    // TP: Used cursor to return current authenticated user's info
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal Object principal) {

        User user = null;

        // Handle bypass mode authentication (UserPrincipal)
        if (principal instanceof UserPrincipal userPrincipal) {
            user = userPrincipal.getUser();
        }
        // Handle OAuth2 authentication (OidcUser)
        else if (principal instanceof OidcUser oidcUser) {
            String googleId = oidcUser.getSubject();
            Optional<User> userOpt = userService.getUserByGoogleId(googleId);

            if (userOpt.isEmpty()) {
                // Try to find user by email as fallback
                String email = oidcUser.getAttribute("email");
                
                if (email != null) {
                    userOpt = userService.getUserByEmail(email);
                }
                
                if (userOpt.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
                }
            }

            user = userOpt.get();
        }
        // Fallback: Try to get user from SecurityContext
        else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
                user = userPrincipal.getUser();
            }
        }

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        
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
     * Supports both OAuth2 (OidcUser) and bypass mode (UserPrincipal) authentication
     */
    @GetMapping("/status")
    // TP: Used cursor to check authentication status
    public ResponseEntity<Map<String, Object>> checkAuthStatus(
            @AuthenticationPrincipal Object principal) {

        Map<String, Object> response = new HashMap<>();

        if (principal == null) {
            response.put("authenticated", false);
            return ResponseEntity.ok(response);
        }

        response.put("authenticated", true);
        response.put("principalType", principal.getClass().getName());

        // Handle bypass mode authentication (UserPrincipal)
        if (principal instanceof UserPrincipal userPrincipal) {
            User user = userPrincipal.getUser();
            if (user != null) {
                response.put("user", Map.of(
                        "email", user.getEmail() != null ? user.getEmail() : "",
                        "name", user.getFullName() != null ? user.getFullName() : ""
                ));
            }
        }
        // Handle OAuth2 authentication (OidcUser)
        else if (principal instanceof OidcUser oidcUser) {
            response.put("user", Map.of(
                    "email", oidcUser.getEmail() != null ? oidcUser.getEmail() : "",
                    "name", oidcUser.getFullName() != null ? oidcUser.getFullName() : ""
            ));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Bypass mode login endpoint (demo/test only)
     * Allows login with email address only, no authentication
     * Works with users imported from YAML files
     */
    @PostMapping("/bypass-login")
    public ResponseEntity<Map<String, Object>> bypassLogin(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        // 1. 환경 변수 체크 - 우회 모드가 활성화되어 있는지 확인
        String bypassEnabled = environment.getProperty("auth.bypass.enabled", "false");
        if (!"true".equalsIgnoreCase(bypassEnabled)) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "Bypass mode is disabled"
            ));
        }
        
        // 2. 이메일 주소 추출
        String email = request.get("email");
        
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Email address is required"
            ));
        }
        
        // 3. 데이터베이스에서 사용자 조회
        // YAML import 시 저장된 사용자를 이메일로 찾음
        Optional<User> userOpt = userService.getUserByEmail(email.toLowerCase().trim());
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "User not found. Please ensure the user has been imported via YAML."
            ));
        }
        
        User user = userOpt.get();
        
        // 4. 사용자 활성 상태 확인
        if (!user.getIsActive()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "User account is inactive"
            ));
        }
        
        // 5. 인증 객체 생성 및 SecurityContext에 저장
        UserPrincipal userPrincipal = new UserPrincipal(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            userPrincipal, 
            null, 
            userPrincipal.getAuthorities()
        );
        
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(authentication);
        
        // 세션에 SecurityContext 저장
        httpRequest.getSession().setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            securityContext
        );
        
        // 6. lastLogin 업데이트
        user.updateLastLogin();
        userService.updateUser(user);
        
        // 7. 응답 반환 (일반 로그인과 동일한 형식)
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", Map.of(
            "userId", user.getUserId(),
            "email", user.getEmail(),
            "firstName", user.getFirstName(),
            "lastName", user.getLastName(),
            "fullName", user.getFullName(),
            "role", user.getRole().toString(),
            "profilePictureUrl", user.getProfilePictureUrl() != null ? user.getProfilePictureUrl() : "",
            "isActive", user.getIsActive()
        ));
        
        return ResponseEntity.ok(response);
    }
}
package edu.sbu.cse416.sam_system.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * TP: Used cursor to implement component for accessing current user information.
 */
@Component
public class CurrentUser {

    private final HttpServletRequest request;
    private static final String DEFAULT_NETID = "jsyoon";

    // TP: Used cursor to construct helper for accessing current user from request/context
    public CurrentUser(HttpServletRequest request) {
        this.request = request;
    }

    // TP: Used cursor to get current user's email from OAuth2 or headers
    public String getEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OAuth2User o2User) {
            String email = o2User.getAttribute("email");
            if (email != null && !email.isBlank()) return email;
        }
        String emailHeader = request.getHeader("X-User-Email");
        return (emailHeader != null && !emailHeader.isBlank()) ? emailHeader.trim() : null;
    }

    // TP: Used cursor to derive current user's NetID from OAuth2 or headers
    public String getNetId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OAuth2User o2User) {
            String email = o2User.getAttribute("email");
            if (email != null && email.contains("@")) {
                return email.substring(0, email.indexOf('@'));
            }
        }
        String emailHeader = request.getHeader("X-User-Email");
        if (emailHeader != null && emailHeader.contains("@")) {
            return emailHeader.substring(0, emailHeader.indexOf('@'));
        }
        String netIdHeader = request.getHeader("X-User-NetId");
        if (netIdHeader != null && !netIdHeader.isBlank()) {
            return netIdHeader.trim();
        }
        return DEFAULT_NETID;
    }
}
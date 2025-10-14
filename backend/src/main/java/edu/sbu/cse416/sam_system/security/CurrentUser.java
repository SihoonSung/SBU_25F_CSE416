package edu.sbu.cse416.sam_system.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * Utility component that resolves the currently logged-in user's netId.
 * Priority order:
 *  1) OAuth2 session (Google login)
 *  2) "X-User-Email" header (frontend dev header)
 *  3) "X-User-NetId" header (frontend dev header)
 *  4) Default fallback for local testing
 */
@Component
public class CurrentUser {

    private final HttpServletRequest request;
    private static final String DEFAULT_NETID = "jsyoon"; // fallback for local dev

    public CurrentUser(HttpServletRequest request) {
        this.request = request;
    }

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
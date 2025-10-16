package edu.sbu.cse416.sam_system.security;

import edu.sbu.cse416.sam_system.entity.User;
import edu.sbu.cse416.sam_system.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Handles successful OAuth2 login attempts.
 * Creates or updates user data in the database and redirects to frontend.
 * 
 * Note: Google OAuth2 integration was implemented with assistance from Cursor AI and ChatGPT
 * for proper authentication flow and user data handling.
 */
@Component
public class OAuth2LoginSuccessHandler
        implements org.springframework.security.web.authentication.AuthenticationSuccessHandler {

    private final UserService userService;

    public OAuth2LoginSuccessHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        OAuth2User o2 = (OAuth2User) authentication.getPrincipal();

        // 1) Create or update user in database
        User saved = userService.findOrCreateUserFromOAuth2(o2);

        // 2) Redirect to frontend
        String redirect = UriComponentsBuilder
                .fromHttpUrl("http://localhost:5173/home")
                .queryParam("success", "true")
                .build()
                .toUriString();

        response.sendRedirect(redirect);
    }
}
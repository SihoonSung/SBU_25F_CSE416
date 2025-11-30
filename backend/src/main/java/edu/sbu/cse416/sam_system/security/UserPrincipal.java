package edu.sbu.cse416.sam_system.security;

import edu.sbu.cse416.sam_system.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * UserPrincipal for bypass authentication mode.
 * Implements UserDetails to integrate with Spring Security.
 */
public class UserPrincipal implements UserDetails {
    
    private final User user;
    
    public UserPrincipal(User user) {
        this.user = user;
    }
    
    public User getUser() {
        return user;
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }
    
    @Override
    public String getPassword() {
        return null; // Not used for bypass mode
    }
    
    @Override
    public String getUsername() {
        return user.getEmail() != null ? user.getEmail() : user.getSbuId();
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return user.getIsActive() != null && user.getIsActive();
    }
}


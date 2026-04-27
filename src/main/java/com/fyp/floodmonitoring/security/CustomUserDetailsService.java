package com.fyp.floodmonitoring.security;

import com.fyp.floodmonitoring.entity.User;
import com.fyp.floodmonitoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

/**
 * Loads a {@link User} by UUID (stored as "username" in the JWT {@code sub} claim).
 * Spring Security uses this during token validation to verify the user still exists.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getId().toString())
                .password(user.getPasswordHash())
                .authorities(Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase())))
                .build();
    }
}

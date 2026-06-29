package org.workfitai.authservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks CustomUserDetailsService service;

    private User user(String username, String email) {
        return User.builder()
                .id("uid-1")
                .username(username)
                .email(email)
                .password("hashed-password")
                .roles(Set.of("CANDIDATE"))
                .build();
    }

    @Test
    void loadUserByUsername_foundByUsername_returnsUserDetails() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("alice", "alice@example.com")));

        UserDetails ud = service.loadUserByUsername("alice");

        assertThat(ud.getUsername()).isEqualTo("alice");
        assertThat(ud.getPassword()).isEqualTo("hashed-password");
        assertThat(ud.getAuthorities()).anyMatch(a -> a.getAuthority().equals("CANDIDATE"));
        assertThat(ud.isAccountNonExpired()).isTrue();
        assertThat(ud.isAccountNonLocked()).isTrue();
        assertThat(ud.isCredentialsNonExpired()).isTrue();
        assertThat(ud.isEnabled()).isTrue();
    }

    @Test
    void loadUserByUsername_notFoundByUsername_fallsBackToEmail() {
        when(userRepository.findByUsername("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user("alice", "alice@example.com")));

        UserDetails ud = service.loadUserByUsername("alice@example.com");

        assertThat(ud.getUsername()).isEqualTo("alice");
    }

    @Test
    void loadUserByUsername_notFoundAnywhere_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void loadUserByUsername_multipleRoles_allAuthoritiesPresent() {
        User u = User.builder()
                .id("uid-2")
                .username("admin")
                .email("admin@example.com")
                .password("hash")
                .roles(Set.of("ADMIN", "HR_MANAGER"))
                .build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(u));

        UserDetails ud = service.loadUserByUsername("admin");

        assertThat(ud.getAuthorities()).hasSize(2);
        assertThat(ud.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ADMIN"))
                .anyMatch(a -> a.getAuthority().equals("HR_MANAGER"));
    }
}

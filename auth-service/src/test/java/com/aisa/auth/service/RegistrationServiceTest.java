package com.aisa.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisa.auth.domain.Role;
import com.aisa.auth.domain.RoleName;
import com.aisa.auth.domain.User;
import com.aisa.auth.repository.RoleRepository;
import com.aisa.auth.repository.UserRepository;
import com.aisa.auth.web.dto.RegistrationRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link RegistrationService}: default-role assignment and
 * password hashing (Requirements 1.1, 2.2, 25.3) and duplicate rejection
 * (Requirement 1.2).
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(userRepository, roleRepository, passwordEncoder);
    }

    @Test
    void createsAccountWithGuestRoleAndHashedPassword() {
        Role guest = new Role(RoleName.GUEST);
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.GUEST)).thenReturn(Optional.of(guest));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(new RegistrationRequest("User@Example.com", "Abcdefg1!xyz"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        // Email normalized to lower-case for case-insensitive uniqueness.
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        // Default role is GUEST (Requirement 2.2).
        assertThat(saved.getRole().getName()).isEqualTo(RoleName.GUEST);
        // Password is stored as a BCrypt hash, never plaintext (Requirement 25.3).
        assertThat(saved.getPasswordHash()).isNotEqualTo("Abcdefg1!xyz");
        assertThat(passwordEncoder.matches("Abcdefg1!xyz", saved.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsDuplicateEmailWithoutCreatingAccount() {
        when(userRepository.existsByEmail("dupe@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                service.register(new RegistrationRequest("dupe@example.com", "Abcdefg1!xyz")))
                .isInstanceOf(DuplicateAccountException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void failsWhenDefaultRoleNotProvisioned() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(roleRepository.findByName(RoleName.GUEST)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.register(new RegistrationRequest("new@example.com", "Abcdefg1!xyz")))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).save(any());
    }
}

package com.aisa.auth.service;

import com.aisa.auth.domain.Role;
import com.aisa.auth.domain.RoleName;
import com.aisa.auth.domain.User;
import com.aisa.auth.repository.RoleRepository;
import com.aisa.auth.repository.UserRepository;
import com.aisa.auth.web.dto.RegistrationRequest;
import com.aisa.auth.web.dto.RegistrationResponse;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates User accounts from validated registration requests (Requirement 1.1).
 *
 * <p>Structural validation (email/password format, length, complexity) happens
 * at the web boundary via Bean Validation (Requirement 1.12). This service
 * enforces the remaining business rules: duplicate-email rejection
 * (Requirement 1.2), default-role assignment (Requirement 2.2), and secure
 * password hashing (Requirement 25.3).
 */
@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new account and returns a confirmation result.
     *
     * @param request the validated registration request
     * @return the created account's confirmation details
     * @throws DuplicateAccountException if an account already exists for the email
     */
    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateAccountException("An account with this email already exists");
        }

        Role guestRole = roleRepository
                .findByName(RoleName.GUEST)
                .orElseThrow(() -> new IllegalStateException(
                        "Default GUEST role is not provisioned"));

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(email, passwordHash, guestRole);
        User saved = userRepository.save(user);

        return RegistrationResponse.from(saved);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

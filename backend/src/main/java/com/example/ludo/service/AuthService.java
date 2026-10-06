package com.example.ludo.service;

import com.example.ludo.model.User;
import com.example.ludo.repository.UserRepository;
import com.example.ludo.security.GoogleTokenVerifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final GoogleTokenVerifier google;

    public AuthService(
            UserRepository users,
            JdbcTemplate jdbc,
            GoogleTokenVerifier google
    ) {
        this.users = users;
        this.jdbc = jdbc;
        this.google = google;
    }

    public record AuthResult(
            String token,
            String userId,
            String name,
            String email,
            String mobile,
            String character,
            String gender,
            boolean guest,
            boolean existingUser,
            boolean needsProfile
    ) {
    }

    /**
     * MOBILE LOGIN
     *
     * No OTP
     * No mobile-number validation
     *
     * Existing mobile -> Home
     * New mobile -> Profile Setup
     */
    @Transactional
    public AuthResult mobileLogin(String mobile) {

        String normalizedMobile = mobile == null
                ? ""
                : mobile.trim();

        User user = users.findByMobile(normalizedMobile);

        if (user != null) {
            return existingUserResponse(user);
        }

        return newProfileResponse(
                null,
                normalizedMobile,
                "MOBILE"
        );
    }

    /**
     * GOOGLE LOGIN
     *
     * Existing Google user -> Home
     * Existing email user -> Home
     * New Google user -> Profile Setup
     */
    @Transactional
    public AuthResult google(String credential) {

        if (credential == null || credential.isBlank()) {
            throw new IllegalArgumentException(
                    "Google credential is required"
            );
        }

        Jwt jwt = google.verify(credential);

        String googleId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String googleName = jwt.getClaimAsString("name");

        if (googleId == null || googleId.isBlank()) {
            throw new IllegalArgumentException(
                    "Google account ID is missing"
            );
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Google email is missing"
            );
        }

        /*
         * First search using Google account ID.
         */
        User user = users.findByProviderId(
                "GOOGLE",
                googleId
        );

        /*
         * If not found, search by email.
         */
        if (user == null) {
            user = users.findByEmail(email);
        }

        /*
         * Existing user -> directly Home.
         */
        if (user != null) {
            return existingUserResponse(user);
        }

        /*
         * New Google user -> Profile Setup.
         */
        return newProfileResponse(
                email,
                null,
                "GOOGLE"
        );
    }

    /**
     * SAVE PROFILE
     *
     * Called after a new Google/Mobile user completes
     * Name + Character + Gender.
     */
    @Transactional
    public AuthResult createProfile(
            String email,
            String mobile,
            String provider,
            String name,
            String character,
            String gender
    ) {

        String cleanName = cleanName(name);

        if (cleanName.isBlank()) {
            throw new IllegalArgumentException(
                    "Name is required"
            );
        }

        if (character == null || character.isBlank()) {
            throw new IllegalArgumentException(
                    "Character is required"
            );
        }

        if (gender == null || gender.isBlank()) {
            throw new IllegalArgumentException(
                    "Gender is required"
            );
        }

        String cleanEmail = cleanValue(email);
        String cleanMobile = cleanValue(mobile);
        String cleanProvider = cleanValue(provider);

        /*
         * Check if the user already exists.
         */
        User user = null;

        if (!cleanMobile.isBlank()) {
            user = users.findByMobile(cleanMobile);
        }

        if (user == null && !cleanEmail.isBlank()) {
            user = users.findByEmail(cleanEmail);
        }

        /*
         * Existing user:
         * update profile instead of creating duplicate.
         */
        if (user != null) {

            user = users.updateProfile(
                    user.id(),
                    cleanName,
                    character,
                    gender
            );

            return existingUserResponse(user);
        }

        /*
         * New user.
         */
        user = users.create(
                cleanName,
                cleanEmail.isBlank() ? null : cleanEmail,
                cleanMobile.isBlank() ? null : cleanMobile,
                cleanProvider.isBlank() ? "UNKNOWN" : cleanProvider,
                cleanProvider.equalsIgnoreCase("MOBILE")
                        ? cleanMobile
                        : null,
                character,
                gender,
                false
        );

        return newUserResponse(user);
    }

    /**
     * GUEST LOGIN
     */
    @Transactional
    public AuthResult guest(String name) {

        User user = users.create(
                cleanName(name).isBlank()
                        ? "Guest"
                        : cleanName(name),
                null,
                null,
                "GUEST",
                UUID.randomUUID().toString(),
                "dinosaur",
                "male",
                true
        );

        return existingUserResponse(user);
    }

    /**
     * Existing user response.
     */
    private AuthResult existingUserResponse(User user) {

        String token = createSession(user);

        return new AuthResult(
                token,
                user.id(),
                user.name(),
                user.email(),
                user.mobile(),
                user.character(),
                user.gender(),
                user.guest(),
                true,
                false
        );
    }

    /**
     * New user response.
     *
     * No database user is created yet.
     * Angular will display Profile Setup.
     */
    private AuthResult newProfileResponse(
            String email,
            String mobile,
            String provider
    ) {

        return new AuthResult(
                null,
                null,
                null,
                email,
                mobile,
                null,
                null,
                false,
                false,
                true
        );
    }

    /**
     * Response after creating a new profile.
     */
    private AuthResult newUserResponse(User user) {

        String token = createSession(user);

        return new AuthResult(
                token,
                user.id(),
                user.name(),
                user.email(),
                user.mobile(),
                user.character(),
                user.gender(),
                user.guest(),
                false,
                false
        );
    }

    /**
     * Create login session.
     */
    private String createSession(User user) {

        String token = UUID.randomUUID().toString();

        jdbc.update(
                """
                INSERT INTO auth_sessions
                (
                    token,
                    user_id,
                    expires_at,
                    created_at
                )
                VALUES
                (
                    ?,
                    ?,
                    DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY),
                    CURRENT_TIMESTAMP
                )
                """,
                token,
                user.id()
        );

        return token;
    }

    /**
     * Clean name.
     */
    private String cleanName(String name) {

        if (name == null) {
            return "";
        }

        String value = name.trim();

        if (value.isBlank()) {
            return "";
        }

        return value.substring(
                0,
                Math.min(value.length(), 30)
        );
    }

    /**
     * Clean nullable values.
     */
    private String cleanValue(String value) {

        if (value == null) {
            return "";
        }

        return value.trim();
    }
}
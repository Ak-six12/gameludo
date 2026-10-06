package com.example.ludo.controller;

import com.example.ludo.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /*
     * MOBILE LOGIN
     *
     * No OTP
     * No mobile-number validation
     */
    public record MobileLoginRequest(
            String mobile
    ) {
    }

    /*
     * GOOGLE LOGIN
     */
    public record GoogleLoginRequest(
            String credential
    ) {
    }

    /*
     * NEW USER PROFILE
     */
    public record ProfileRequest(
            String email,
            String mobile,
            String provider,
            String name,
            String character,
            String gender
    ) {
    }

    /*
     * GUEST LOGIN
     */
    public record GuestLoginRequest(
            String name
    ) {
    }

    /*
     * =========================
     * MOBILE LOGIN
     * =========================
     */
    @PostMapping("/mobile")
    public AuthService.AuthResult mobile(
            @RequestBody MobileLoginRequest request
    ) {

        return auth.mobileLogin(
                request == null
                        ? null
                        : request.mobile()
        );
    }

    /*
     * =========================
     * GOOGLE LOGIN
     * =========================
     */
    @PostMapping("/google")
    public AuthService.AuthResult google(
            @RequestBody GoogleLoginRequest request
    ) {

        if (request == null
                || request.credential() == null
                || request.credential().isBlank()) {

            throw new IllegalArgumentException(
                    "Google credential is required"
            );
        }

        return auth.google(
                request.credential()
        );
    }

    /*
     * =========================
     * SAVE PROFILE
     * =========================
     */
    @PostMapping("/profile")
    public AuthService.AuthResult createProfile(
            @RequestBody ProfileRequest request
    ) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Profile data is required"
            );
        }

        return auth.createProfile(
                request.email(),
                request.mobile(),
                request.provider(),
                request.name(),
                request.character(),
                request.gender()
        );
    }

    /*
     * =========================
     * GUEST LOGIN
     * =========================
     */
    @PostMapping("/guest")
    public AuthService.AuthResult guest(
            @RequestBody(required = false)
            GuestLoginRequest request
    ) {

        return auth.guest(
                request == null
                        ? null
                        : request.name()
        );
    }
}
package com.example.ludo.controller;

import com.example.ludo.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins="*")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth){this.auth=auth;}
    public record MobileOtpRequest(@NotBlank String mobile){}
    public record VerifyOtpRequest(@NotBlank String mobile,@NotBlank String otp,String name){}
    public record GoogleLoginRequest(@NotBlank String credential){}
    public record GuestLoginRequest(String name){}
    @PostMapping("/mobile/send-otp")
    public ResponseEntity<?> sendOtp(@Valid @RequestBody MobileOtpRequest r){auth.sendOtp(r.mobile());return ResponseEntity.ok(java.util.Map.of("success",true,"message","OTP sent successfully"));}
    @PostMapping("/mobile/verify-otp")
    public AuthService.AuthResult verifyOtp(@Valid @RequestBody VerifyOtpRequest r){return auth.verifyOtp(r.mobile(),r.otp(),r.name());}
    @PostMapping("/google")
    public AuthService.AuthResult google(@Valid @RequestBody GoogleLoginRequest r){return auth.google(r.credential());}
    @PostMapping("/guest")
    public AuthService.AuthResult guest(@RequestBody(required=false) GuestLoginRequest r){return auth.guest(r==null?null:r.name());}
}

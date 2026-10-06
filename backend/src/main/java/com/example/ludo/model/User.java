package com.example.ludo.model;

public record User(
        String id,
        String name,
        String email,
        String mobile,
        String provider,
        String providerId,
        String character,
        String gender,
        boolean guest
) {
}
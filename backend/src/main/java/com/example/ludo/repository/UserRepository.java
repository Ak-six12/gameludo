package com.example.ludo.repository;

import com.example.ludo.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public User findByEmail(String email) {

        if (email == null || email.isBlank()) {
            return null;
        }

        return findOne(
                """
                SELECT
                    id,
                    name,
                    email,
                    mobile,
                    provider,
                    provider_id,
                    character_name,
                    gender,
                    guest
                FROM users
                WHERE email = ?
                """,
                email
        ).orElse(null);
    }

    public User findByMobile(String mobile) {

        if (mobile == null || mobile.isBlank()) {
            return null;
        }

        return findOne(
                """
                SELECT
                    id,
                    name,
                    email,
                    mobile,
                    provider,
                    provider_id,
                    character_name,
                    gender,
                    guest
                FROM users
                WHERE mobile = ?
                """,
                mobile
        ).orElse(null);
    }

    public User findByProviderId(
            String provider,
            String providerId
    ) {

        if (provider == null || providerId == null) {
            return null;
        }

        return findOne(
                """
                SELECT
                    id,
                    name,
                    email,
                    mobile,
                    provider,
                    provider_id,
                    character_name,
                    gender,
                    guest
                FROM users
                WHERE provider = ?
                  AND provider_id = ?
                """,
                provider,
                providerId
        ).orElse(null);
    }

    public User findById(String id) {

        if (id == null || id.isBlank()) {
            return null;
        }

        return findOne(
                """
                SELECT
                    id,
                    name,
                    email,
                    mobile,
                    provider,
                    provider_id,
                    character_name,
                    gender,
                    guest
                FROM users
                WHERE id = ?
                """,
                id
        ).orElse(null);
    }

    public User create(
            String name,
            String email,
            String mobile,
            String provider,
            String providerId,
            String character,
            String gender,
            boolean guest
    ) {

        String id = UUID.randomUUID().toString();

        jdbc.update(
                """
                INSERT INTO users
                (
                    id,
                    name,
                    email,
                    mobile,
                    provider,
                    provider_id,
                    character_name,
                    gender,
                    guest,
                    created_at
                )
                VALUES
                (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
                )
                """,
                id,
                name,
                email,
                mobile,
                provider,
                providerId,
                character,
                gender,
                guest
        );

        return findById(id);
    }

    public User updateProfile(
            String id,
            String name,
            String character,
            String gender
    ) {

        jdbc.update(
                """
                UPDATE users
                SET
                    name = ?,
                    character_name = ?,
                    gender = ?
                WHERE id = ?
                """,
                name,
                character,
                gender,
                id
        );

        return findById(id);
    }

    private Optional<User> findOne(
            String sql,
            Object... args
    ) {

        return jdbc.query(
                sql,
                args,
                (rs, rowNum) -> new User(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("mobile"),
                        rs.getString("provider"),
                        rs.getString("provider_id"),
                        rs.getString("character_name"),
                        rs.getString("gender"),
                        rs.getBoolean("guest")
                )
        ).stream().findFirst();
    }
}
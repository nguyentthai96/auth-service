package com.ntt.authservice.auth.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Tests for V20 Flyway migration SQL logic.
 * Validates the prefix-addition logic is correct and idempotent.
 *
 * Note: This tests the SQL logic conceptually (string operations),
 * not the actual Flyway execution (which requires a real database).
 * For full DB migration tests, use Testcontainers with PostgreSQL.
 *
 * FR-007: Database migration thêm prefix
 */
@DisplayName("V20 Migration — Password Hash Prefix Logic")
class V20MigrationTest {

    /**
     * Simulates the UPDATE WHERE logic from V20 migration.
     */
    private fun applyMigrationLogic(hash: String): String {
        // Mirrors SQL: WHERE password_hash NOT LIKE '{%}%' AND password_hash LIKE '$2a$%'
        val needsPrefix = !hash.startsWith("{") && hash.startsWith("\$2a\$")
        return if (needsPrefix) "{bcrypt}$hash" else hash
    }

    @Test
    @DisplayName("BCrypt hash without prefix gets {bcrypt} prefix added")
    fun `adds prefix to bare bcrypt hash`() {
        val bareHash = "\$2a\$12\$LJ3m4ys3PyJR0r1yMN7Y4O4sQfrEKxWlPe8WS7iYuABhYqxGK/HqC"
        val result = applyMigrationLogic(bareHash)
        assertEquals("{bcrypt}$bareHash", result)
    }

    @Test
    @DisplayName("Already prefixed BCrypt hash is not double-prefixed")
    fun `idempotent - skips already prefixed hash`() {
        val prefixedHash = "{bcrypt}\$2a\$12\$LJ3m4ys3PyJR0r1yMN7Y4O4sQfrEKxWlPe8WS7iYuABhYqxGK/HqC"
        val result = applyMigrationLogic(prefixedHash)
        assertEquals(prefixedHash, result, "Should NOT double-prefix")
    }

    @Test
    @DisplayName("Argon2id hash is not affected")
    fun `skips argon2id hash`() {
        val argon2Hash = "{argon2id}\$argon2id\$v=19\$m=65536,t=3,p=1\$salt\$hash"
        val result = applyMigrationLogic(argon2Hash)
        assertEquals(argon2Hash, result)
    }

    @Test
    @DisplayName("Non-BCrypt hash is not affected")
    fun `skips non-bcrypt non-prefixed hash`() {
        val otherHash = "sha256:abcdef1234567890"
        val result = applyMigrationLogic(otherHash)
        assertEquals(otherHash, result)
    }

    @Test
    @DisplayName("Migration SQL file exists and has correct content")
    fun `migration file exists with correct statements`() {
        val migrationFile = File(
            "src/main/resources/db/migration/V20__add_password_hash_prefix.sql"
        )
        assertTrue(migrationFile.exists(), "V20 migration file should exist")

        val content = migrationFile.readText()
        assertTrue(content.contains("UPDATE users"), "Should update users table")
        assertTrue(content.contains("UPDATE password_history"), "Should update password_history table")
        assertTrue(content.contains("{bcrypt}"), "Should add {bcrypt} prefix")
        assertTrue(content.contains("NOT LIKE '{%}%'"), "Should have idempotency guard")
        assertTrue(content.contains("LIKE '\$2a\$%'"), "Should filter BCrypt hashes only")
    }

    @Test
    @DisplayName("Batch processing: mixed hashes correctly migrated")
    fun `handles batch of mixed hashes`() {
        val hashes = listOf(
            "\$2a\$12\$abc" to "{bcrypt}\$2a\$12\$abc",                                 // BCrypt → prefix
            "{bcrypt}\$2a\$12\$def" to "{bcrypt}\$2a\$12\$def",                          // Already prefixed → no change
            "{argon2id}\$argon2id\$v=19\$ghi" to "{argon2id}\$argon2id\$v=19\$ghi",      // Argon2id → no change
            "random_string" to "random_string"                                            // Other → no change
        )

        hashes.forEach { (input, expected) ->
            assertEquals(expected, applyMigrationLogic(input), "Failed for input: $input")
        }
    }
}

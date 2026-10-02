package com.ntt.authservice.auth.application

data class PasswordPolicyConfig(
    val minLength: Int = 8,
    val maxLength: Int = 128,
    val requireUppercase: Boolean = true,
    val requireLowercase: Boolean = true,
    val requireDigit: Boolean = true,
    val requireSpecial: Boolean = false,
    val minCharacterTypes: Int = 3,
    val historyCount: Int = 5,
    val maxAgeDays: Int = 90,
    val lockoutThreshold: Int = 5,
    val lockoutDurationMinutes: Int = 15
)

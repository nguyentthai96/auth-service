package com.ntt.authservice.auth.application

import com.ntt.authservice.rbac.adapter.out.persistence.entity.PasswordHistoryEntity
import com.ntt.authservice.rbac.adapter.out.persistence.entity.PasswordPolicyEntity
import com.ntt.authservice.rbac.adapter.out.persistence.repository.PasswordHistoryRepository
import com.ntt.authservice.rbac.adapter.out.persistence.repository.PasswordPolicyRepository
import com.ntt.authservice.rbac.adapter.out.persistence.repository.UserRepository
import com.ntt.authservice.shared.config.SecurityProperties
import com.ntt.authservice.shared.exception.*
import org.passay.*
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Domain-scoped password policy enforcement via Passay.
 * Includes password history check and change flow.
 */
@Service
class PasswordPolicyService(
    private val passwordPolicyRepository: PasswordPolicyRepository,
    private val passwordHistoryRepository: PasswordHistoryRepository,
    private val userRepository: UserRepository,
    private val securityProperties: SecurityProperties,
    private val passwordEncoder: PasswordEncoder
) {

    private val log = LoggerFactory.getLogger(PasswordPolicyService::class.java)
    private val validatorCache = ConcurrentHashMap<Long, PasswordValidator>()


    /**
     * Validate password against domain policy.
     * @return list of violation messages (empty = valid)
     */
    fun validatePasswordStrength(password: String, domainId: Long): List<String> {
        val validator = validatorCache.computeIfAbsent(domainId) {
            buildValidator(getPolicy(domainId))
        }
        val result = validator.validate(PasswordData(password))
        return if (result.isValid) emptyList()
        else validator.getMessages(result)
    }

    /**
     * Check if the new password has been used recently.
     * @return true if password is allowed (not in history)
     */
    fun checkPasswordHistory(userId: Long, newPassword: String, historyCount: Int): Boolean {
        val history = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .take(historyCount)
        return history.none { passwordEncoder.matches(newPassword, it.passwordHash) }
    }

    /**
     * Full password change flow: validate old → check strength → check history → persist.
     */
    fun changePassword(userId: Long, oldPassword: String, newPassword: String, domainId: Long) {
        val user = userRepository.findById(userId).orElseThrow {
            ResourceNotFoundException("User", userId)
        }

        // Validate old password
        if (!passwordEncoder.matches(oldPassword, user.passwordHash)) {
            throw InvalidCredentialsException()
        }

        // Check strength
        val violations = validatePasswordStrength(newPassword, domainId)
        if (violations.isNotEmpty()) {
            throw PasswordPolicyViolationException(violations.joinToString("; "))
        }

        // Check history
        val policy = getPolicy(domainId)
        if (!checkPasswordHistory(userId, newPassword, policy.historyCount)) {
            throw PasswordRecentlyUsedException()
        }

        // Persist new password
        val newHash = passwordEncoder.encode(newPassword)!!

        // Save to history
        val historyEntry = PasswordHistoryEntity().apply {
            this.userId = userId
            this.passwordHash = newHash
            this.createdAt = Instant.now()
        }
        passwordHistoryRepository.save(historyEntry)

        // Update user
        user.passwordHash = newHash
        user.passwordChangedAt = Instant.now()
        userRepository.save(user)

        // Prune old history entries
        pruneHistory(userId, policy.historyCount)

        log.info("Password changed for userId={}", userId)
    }

    /**
     * Get policy for domain (falls back to system defaults).
     */
    fun getPolicy(domainId: Long): PasswordPolicyEntity {
        return passwordPolicyRepository.findByDomainId(domainId) ?: PasswordPolicyEntity().apply {
            this.domainId = domainId
        }
    }

    /**
     * Update domain password policy and invalidate cached validator.
     */
    fun updatePolicy(domainId: Long, policy: PasswordPolicyEntity): PasswordPolicyEntity {
        policy.updatedAt = Instant.now()
        val saved = passwordPolicyRepository.save(policy)
        validatorCache.remove(domainId)
        log.info("Password policy updated for domainId={}", domainId)
        return saved
    }

    /**
     * Check if user's password has expired based on domain policy.
     */
    fun isPasswordExpired(userId: Long, domainId: Long): Boolean {
        val user = userRepository.findById(userId).orElse(null) ?: return false
        val policy = getPolicy(domainId)
        if (policy.maxAgeDays <= 0) return false
        val changedAt = user.passwordChangedAt ?: return true
        val expiresAt = changedAt.plusSeconds(policy.maxAgeDays.toLong() * 86400)
        return Instant.now().isAfter(expiresAt)
    }

    private fun buildValidator(policy: PasswordPolicyEntity): PasswordValidator {
        val rules = mutableListOf<Rule>()

        rules.add(LengthRule(policy.minLength, policy.maxLength))
        rules.add(WhitespaceRule())

        val charRules = mutableListOf<CharacterRule>()
        if (policy.requireUppercase) charRules.add(CharacterRule(EnglishCharacterData.UpperCase, 1))
        if (policy.requireLowercase) charRules.add(CharacterRule(EnglishCharacterData.LowerCase, 1))
        if (policy.requireDigit) charRules.add(CharacterRule(EnglishCharacterData.Digit, 1))
        if (policy.requireSpecial) charRules.add(CharacterRule(EnglishCharacterData.Special, 1))

        if (charRules.isNotEmpty() && policy.minCharacterTypes > 0) {
            rules.add(CharacterCharacteristicsRule(policy.minCharacterTypes, charRules))
        }

        return PasswordValidator(rules)
    }

    private fun pruneHistory(userId: Long, keepCount: Int) {
        val all = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)
        if (all.size > keepCount) {
            val toKeep = all.take(keepCount).mapNotNull { it.id }
            passwordHistoryRepository.deleteByUserIdAndIdNotIn(userId, toKeep)
        }
    }
}

package com.ntt.authservice.shared.config

import org.springframework.security.crypto.password.PasswordEncoder
import java.util.concurrent.Semaphore

/**
 * Decorator that limits concurrent password hashing operations via a counting semaphore.
 *
 * Argon2id allocates ~64MB per hash operation. Without concurrency limits, unbounded
 * Virtual Threads could cause OOM under high load. This wrapper throttles both
 * encode() and matches() (both allocate memory for Argon2id) while keeping
 * upgradeEncoding() unthrottled (O(1) prefix check).
 *
 * @property delegate The actual password encoder (typically DelegatingPasswordEncoder)
 * @property maxConcurrent Maximum concurrent hash operations (default: 20 → ~1.28GB max)
 */
class ConcurrencyLimitedPasswordEncoder(
    private val delegate: PasswordEncoder,
    maxConcurrent: Int = 20
) : PasswordEncoder {

    private val semaphore = Semaphore(maxConcurrent)

    override fun encode(rawPassword: CharSequence?): String? {
        semaphore.acquire()
        return try {
            delegate.encode(rawPassword)
        } finally {
            semaphore.release()
        }
    }

    override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
        semaphore.acquire()
        return try {
            delegate.matches(rawPassword, encodedPassword)
        } finally {
            semaphore.release()
        }
    }

    override fun upgradeEncoding(encodedPassword: String?): Boolean {
        // O(1) string prefix check — no memory allocation, no semaphore needed
        return delegate.upgradeEncoding(encodedPassword)
    }
}

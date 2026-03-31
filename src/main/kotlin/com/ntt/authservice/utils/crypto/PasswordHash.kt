package com.ntt.authservice.utils.crypto

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder


/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  03/03/2025, Monday
 **/
object PasswordHash {
    private val logger: Logger = LoggerFactory.getLogger(PasswordHash::class.java)

    private val passwordEncoder: MutableMap<Int, PasswordEncoder> = HashMap()

    init {
        passwordEncoder[1] = HashPasswordEncoder.getInstance()
        passwordEncoder[2] = Argon2PasswordEncoder.getInstance()
        passwordEncoder[3] = Pbkdf2PasswordEncoder.getInstance()
        passwordEncoder[4] = HashHmacPasswordEncoder.getInstance()
    }

    /**
     * Verify that the encoded password obtained from storage matches the submitted raw
     * password after the raw password is also encoded. Returns true if the passwords match, false if
     * they do not. The stored password itself is never decoded.
     *
     * @param password                       the password to check
     * @param encodedPassword the stored hashed password with salt
     * @return true if the password is correct, false if not
     */
    fun matches(password: String?, encodedPassword: String): Boolean {
        if (StringUtils.isEmpty(encodedPassword)) {
            logger.warn("password hash is null")
            return false
        }
        val params = encodedPassword.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val alg = params[2].toInt()

        return passwordEncoder.getOrDefault(alg, Pbkdf2PasswordEncoder.getInstance()).matches(password, encodedPassword)
    }

    /**
     * Generate an encoded password hash.
     *
     * @param password the password to hash
     * @return a salted hash of the password
     */
    fun encode(password: String?): String {
        logger.debug("Encoding password")
        return Argon2PasswordEncoder.getInstance().encode(password)
    }

    fun encode(password: String?, algorithm: Int): String {
        logger.debug("Encoding password")
        return passwordEncoder.getOrDefault(algorithm, Argon2PasswordEncoder.getInstance()).encode(password)
    }
}
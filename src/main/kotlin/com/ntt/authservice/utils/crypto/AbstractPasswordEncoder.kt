package com.ntt.authservice.utils.crypto

import com.ntt.basecore.exception.ErrorCode
import org.hibernate.service.spi.ServiceException
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException

abstract class AbstractPasswordEncoder : PasswordEncoder {
    override fun encode(passwordSequence: CharSequence): String {
        logger.debug("Encoding password")
        val password = passwordSequence.toString()
        try {
            return createHash(password.toCharArray())
        } catch (e: NoSuchAlgorithmException) {
            logger.error(ErrorCode.PASSWORD_HASH_CREATION_NOT_SUCCESSFUL.getDescription(), e)
            throw ServiceException(ErrorCode.PASSWORD_HASH_CREATION_NOT_SUCCESSFUL)
        } catch (e: java.security.InvalidKeyException) {
            logger.error(ErrorCode.PASSWORD_HASH_CREATION_NOT_SUCCESSFUL.getDescription(), e)
            throw ServiceException(ErrorCode.PASSWORD_HASH_CREATION_NOT_SUCCESSFUL)
        } catch (e: InvalidKeySpecException) {
            logger.error(ErrorCode.PASSWORD_HASH_CREATION_NOT_SUCCESSFUL.getDescription(), e)
            throw ServiceException(ErrorCode.PASSWORD_HASH_CREATION_NOT_SUCCESSFUL)
        }
    }

    /**
     * Verify that the encoded password obtained from storage matches the submitted raw
     * password after the raw password is also encoded. Returns true if the passwords match, false if
     * they do not. The stored password itself is never decoded.
     *
     * @param passwordSequence               the password to check
     * @param encodedPassword the stored hashed password with salt
     * @return true if the password is correct, false if not
     */
    override fun matches(passwordSequence: CharSequence, encodedPassword: String): Boolean {
        val password = passwordSequence.toString()
        val parts = encodedPassword.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size < 2) {
            return false
        }
        val hash = EncodingUtils.fromHex(parts[0])
        val salt = EncodingUtils.fromHex(parts[1])


        return matches(password, hash, salt)
    }


    /**
     * Compare passwords
     *
     * @param password the password to check
     * @param hash     hashed password
     * @param salt     Salt used to hash password
     * @return true if the password is correct, false if not
     */
    fun matches(password: String, hash: ByteArray?, salt: ByteArray?): Boolean {
        val result: Boolean
        try {
            result = matches(password.toCharArray(), hash, salt)
        } catch (e: java.lang.Exception) {
            logger.error(ErrorCode.PASSWORD_HASH_CREATION_NOT_SUCCESSFUL.getDescription(), e)
            throw ServiceException(ErrorCode.PASSWORD_HASH_CREATION_NOT_SUCCESSFUL)
        }

        return result
    }

    @Throws(java.lang.Exception::class)
    abstract fun matches(password: CharArray?, hash: ByteArray?, salt: ByteArray?): Boolean

    @Throws(java.security.InvalidKeyException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    abstract fun createHash(password: CharArray?): String


    companion object {
        val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(AbstractPasswordEncoder::class.java)
    }
}

package com.ntt.authservice.utils.crypto

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.AbstractPasswordEncoder
import java.security.NoSuchAlgorithmException


/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  03/03/2025, Monday
 **/
class HashPasswordEncoder : AbstractPasswordEncoder() {
    private object HashPasswordEncoderHolder {
        val instance: HashPasswordEncoder = HashPasswordEncoder()
            get() = HashPasswordEncoderHolder.field
    }

    /**
     * Returns a salted hash of the password.
     *
     * @param password the password to hash
     * @return a salted hash of the password in the form of HASH:SALT:ALGORITHM
     * @throws NoSuchAlgorithmException hash algorithm does not exist
     */
    @Throws(NoSuchAlgorithmException::class)
    fun createHash(password: CharArray): String {
        logger.debug("create hash")

        // Generate a random salt
        val salt: ByteArray = SaltGenerator.init(SALT_LENGTH).generateSalt()

        // Hash the password
        val hash = encodeWithSalt(password, salt)

        // Format hash:salt:algorithm
        return (EncodingUtils.toHex(hash) + ":" + EncodingUtils.toHex(salt)).toString() + ":" + "1"
    }


    /**
     * Validates a password using a hash.
     *
     * @param password the password to check
     * @param hash     Previously hashed password
     * @param salt     The salt used for the derivation
     * @return true if the password is correct, false if not
     */
    @Throws(Exception::class)
    fun matches(password: CharArray, hash: ByteArray?, salt: ByteArray): Boolean {
        // Compute the hash of the provided password, using the same salt

        val testHash = encodeWithSalt(password, salt)

        // Compare the hashes in constant time. The password is correct if the two hashes match.
        return EncodingUtils.slowEquals(hash, testHash)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HashPasswordEncoder::class.java)

        private const val HASH_ALGORITHM = "SHA-256"
        private const val SALT_LENGTH = 32 // bytes


        @Throws(NoSuchAlgorithmException::class)
        private fun encodeWithSalt(password: CharArray, salt: ByteArray): ByteArray {
            val passwordBytes = String(password).toByteArray()

            //byte[] saltPassword = new byte[salt.length + passwordBytes.length];
            //System.arraycopy(salt, 0, saltPassword, 0, salt.length);
            //System.arraycopy(passwordBytes, 0, saltPassword, salt.length, passwordBytes.length);
            val md: MessageDigest = MessageDigest.getInstance(HASH_ALGORITHM)
            md.update(salt)
            return md.digest(passwordBytes)
        }
    }
}
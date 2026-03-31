package com.ntt.authservice.utils.crypto

import com.util.cloud.Environment
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.InvalidKeyException
import java.security.Key
import javax.crypto.Mac

class HashHmacPasswordEncoder : AbstractPasswordEncoder() {
    private object HashHmacPasswordEncoderHolder {
        val instance: HashHmacPasswordEncoder = HashHmacPasswordEncoder()
            get() = HashHmacPasswordEncoderHolder.INSTANCE
    }

    /**
     * Returns a salted hash of the password.
     *
     * @param password the password to hash
     * @return a salted hash of the password in the form of HASH:SALT:ALGORITHM
     * @throws InvalidKeyException, NoSuchAlgorithmException
     */
    @Throws(InvalidKeyException::class, NoSuchAlgorithmException::class)
    override fun createHash(password: CharArray): String {
        logger.debug("create hash")

        // Generate a random salt
        val salt = SaltGenerator.init(SALT_LENGTH).generateSalt()

        // Hash the password
        val hash = encodeWithSalt(password, salt)

        // Format hash:salt:algorithm
        return EncodingUtils.toHex(hash) + ":" + EncodingUtils.toHex(salt) + ":" + "4"
    }

    @Throws(Exception::class)
    override fun matches(password: CharArray, hash: ByteArray, salt: ByteArray): Boolean {
        // Compute the hash of the provided password, using the same salt

        val testHash = encodeWithSalt(password, salt)

        // Compare the hashes in constant time. The password is correct if the two hashes match.
        return EncodingUtils.slowEquals(hash, testHash)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(HashHmacPasswordEncoder::class.java)

        const val SECRET_KEY: String = "hashHMACSecretKey"

        private const val HASH_ALGORITHM = "HMACSHA256"
        private const val SALT_LENGTH = 32 // bytes


        @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class)
        private fun encodeWithSalt(password: CharArray, salt: ByteArray): ByteArray {
            val sk: Key = SecretKeySpec(Environment.getProperty(SECRET_KEY, "secret").getBytes(), HASH_ALGORITHM)
            val mac = Mac.getInstance(sk.algorithm)
            mac.init(sk)
            val passwordBytes = String(password).toByteArray()
            val saltPassword = ByteArray(salt.size + passwordBytes.size)
            System.arraycopy(salt, 0, saltPassword, 0, salt.size)
            System.arraycopy(passwordBytes, 0, saltPassword, salt.size, passwordBytes.size)
            return mac.doFinal(saltPassword)
        }
    }
}

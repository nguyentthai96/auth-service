package com.ntt.authservice.utils.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Argon2PasswordEncoder : AbstractPasswordEncoder() {
    private object Argon2PasswordEncoderHolder {
        val instance: Argon2PasswordEncoder = Argon2PasswordEncoder()
            get() = Argon2PasswordEncoderHolder.instance
    }

    /**
     * Generate an encoded password hash value for storage in a user's account.
     *
     * @param password the password to hash
     * @return a hash of the password in the form of HASH:SALT:ALGORITHM
     */
    override fun createHash(password: CharArray?): String {
        logger.debug("create hash")

        // Generate a random salt
        val salt = SaltGenerator.init(ARGON2_SALT_LENGTH).generateSalt()

        // Hash the password
        val hash = encodeWithSalt(password, salt)

        // Format hash:salt:algorithm
        return EncodingUtils.toHex(hash) + ":" + EncodingUtils.toHex(salt) + ":" + "2"
    }

    override fun matches(password: CharArray?, hash: ByteArray, salt: ByteArray?): Boolean {
        // Compute the hash of the provided password, using the same salt

        val testHash = encodeWithSalt(password, salt)

        // Compare the hashes in constant time. The password is correct if the two hashes match.
        return EncodingUtils.slowEquals(hash, testHash)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(Argon2PasswordEncoder::class.java)

        private const val ARGON2_HASH_ALGORITHM = 2
        private const val ARGON2_SALT_LENGTH = 16
        private const val ARGON2_HASH_LENGTH = 32
        private const val ARGON2_PARALLELISM = 1
        private const val ARGON2_MEMORY = 65536
        private const val ARGON2_ITERATIONS = 2


        private fun encodeWithSalt(password: CharArray?, salt: ByteArray?): ByteArray {
            val hash = ByteArray(ARGON2_HASH_LENGTH)
            val params = Argon2Parameters.Builder(ARGON2_HASH_ALGORITHM)
                .withSalt(salt)
                .withParallelism(ARGON2_PARALLELISM)
                .withMemoryAsKB(ARGON2_MEMORY)
                .withIterations(ARGON2_ITERATIONS)
                .build()
            val generator = Argon2BytesGenerator()
            generator.init(params)
            generator.generateBytes(password, hash)
            return hash
        }
    }
}

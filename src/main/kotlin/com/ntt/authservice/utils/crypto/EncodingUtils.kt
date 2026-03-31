package com.ntt.authservice.utils.crypto

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.*

internal object EncodingUtils {
    private val log: Logger = LoggerFactory.getLogger(EncodingUtils::class.java)

    private val HEX = "0123456789abcdef".toByteArray(StandardCharsets.UTF_8)

    /**
     * Converts a byte array into a base64 string.
     *
     * @param array the byte array to convert
     * @return a length*2 character string encoding the byte array
     */
    @JvmStatic
    fun toBase64(array: ByteArray?): String {
        log.debug("Convert to base64")
        return Base64.getEncoder().encodeToString(array)
    }

    /**
     * Converts a string of base64 characters into a byte array.
     *
     * @param base64 the hex string
     * @return the hex string decoded into a byte array
     */
    @JvmStatic
    fun fromBase64(base64: String?): ByteArray {
        return Base64.getDecoder().decode(base64)
    }


    /**
     * Converts a byte array into a hexadecimal string.
     *
     * @param bytes the byte array to convert
     * @return a length*2 character string encoding the byte array
     */
    @JvmStatic
    fun toHex(bytes: ByteArray): String {
        val hexChars = ByteArray(bytes.size * 2)
        for (i in bytes.indices) {
            val octet =
                bytes[i].toInt() and 0xFF //“& 0xFF” effectively masks the variable so it leaves only the value in the last 8 bits, and ignores all the rest of the bits.
            hexChars[i * 2] = HEX[octet ushr 4]
            hexChars[i * 2 + 1] = HEX[octet and 0x0F]
        }
        return String(hexChars, StandardCharsets.UTF_8)
    }


    /**
     * Converts a string of hexadecimal characters into a byte array.
     *
     * @param hex the hex string
     * @return the hex string decoded into a byte array
     */
    fun fromHex(hex: String): ByteArray {
        val numberOfChars = hex.length
        require(numberOfChars % 2 == 0) { "Hex-encoded string must have an even number of characters" }
        val binary = ByteArray(numberOfChars / 2)
        for (i in binary.indices) {
            val firstDigit = hex[2 * i].digitToIntOrNull(16) ?: -1
            val secondDigit = hex[2 * i + 1].digitToIntOrNull(16) ?: -1
            require(!(firstDigit < 0 || secondDigit < 0)) { "Detected a Non-hex character at " + (2 * i) + " or " + (2 * i + 1) + " position" }

            binary[i] = ((firstDigit shl 4) or secondDigit).toByte()
        }
        return binary
    }


    /**
     * Compares two byte arrays in length-constant time. This comparison method
     * is used so that password hashes cannot be extracted from an on-line
     * system using a timing attack and then attacked off-line.
     *
     * @param a the first byte array
     * @param b the second byte array
     * @return true if both byte arrays are the same, false if not
     */
    @JvmStatic
    fun slowEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false

        var result = a.size xor b.size
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}

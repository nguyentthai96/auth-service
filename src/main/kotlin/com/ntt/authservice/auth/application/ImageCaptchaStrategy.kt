package com.ntt.authservice.auth.application

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.ntt.authservice.shared.config.SecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.random.Random

/**
 * Image-based CAPTCHA strategy using pure Java AWT (FR-014).
 * Self-hosted, zero external dependency — uses java.awt.Graphics2D for image generation.
 *
 * Flow:
 * 1. Client requests challenge → receives captchaId + Base64 image
 * 2. Client submits answer as "captchaId:answer" token
 * 3. Server verifies answer against stored text (one-time use)
 */
@Component
class ImageCaptchaStrategy(
    private val securityProperties: SecurityProperties
) : CaptchaStrategy {

    private val log = LoggerFactory.getLogger(ImageCaptchaStrategy::class.java)

    override val type: String = "image"

    /** Allowed characters — excludes ambiguous chars (0/O, 1/I/l). */
    private val charPool = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"

    /** In-memory store: captchaId → expected text (TTL-based, one-time consumption). */
    private val captchaStore: Cache<String, String> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(securityProperties.imageCaptcha.image.ttlSeconds))
        .maximumSize(10_000)
        .build()

    /**
     * Verify the CAPTCHA answer.
     * @param token Format: "captchaId:userAnswer"
     */
    override fun verify(token: String): Boolean {
        val parts = token.split(":", limit = 2)
        if (parts.size != 2) {
            log.warn("IMAGE_CAPTCHA_INVALID_FORMAT")
            return false
        }

        val (captchaId, userAnswer) = parts
        val expectedText = captchaStore.getIfPresent(captchaId)

        if (expectedText == null) {
            log.warn("IMAGE_CAPTCHA_EXPIRED_OR_USED captchaId={}", captchaId)
            return false
        }

        // One-time use — invalidate immediately
        captchaStore.invalidate(captchaId)

        val isValid = expectedText.equals(userAnswer.trim(), ignoreCase = true)
        if (!isValid) {
            log.warn("IMAGE_CAPTCHA_MISMATCH captchaId={}", captchaId)
        }
        return isValid
    }

    /**
     * Generate a new image CAPTCHA challenge.
     * @return ImageCaptchaChallenge with captchaId and Base64-encoded PNG image
     */
    fun generateChallenge(): ImageCaptchaChallenge {
        val captchaId = UUID.randomUUID().toString()
        val imageSettings = securityProperties.imageCaptcha.image
        val text = generateRandomText(imageSettings.length)
        val image = renderCaptchaImage(text, imageSettings.width, imageSettings.height)

        // Store expected text
        captchaStore.put(captchaId, text)

        // Convert image to Base64
        val imageBase64 = encodeImageToBase64(image)

        return ImageCaptchaChallenge(
            captchaId = captchaId,
            imageBase64 = imageBase64,
            ttlSeconds = imageSettings.ttlSeconds
        )
    }

    private fun generateRandomText(length: Int): String {
        return (1..length)
            .map { charPool[Random.nextInt(charPool.length)] }
            .joinToString("")
    }

    private fun renderCaptchaImage(text: String, width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g2d: Graphics2D = image.createGraphics()

        // Anti-aliasing for smoother text
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Background — light gradient
        g2d.color = Color(240, 240, 240)
        g2d.fillRect(0, 0, width, height)

        // Noise lines
        for (i in 0 until 6) {
            g2d.color = Color(
                Random.nextInt(100, 200),
                Random.nextInt(100, 200),
                Random.nextInt(100, 200)
            )
            g2d.drawLine(
                Random.nextInt(width), Random.nextInt(height),
                Random.nextInt(width), Random.nextInt(height)
            )
        }

        // Noise dots
        for (i in 0 until 30) {
            g2d.color = Color(
                Random.nextInt(150, 220),
                Random.nextInt(150, 220),
                Random.nextInt(150, 220)
            )
            val x = Random.nextInt(width)
            val y = Random.nextInt(height)
            g2d.fillOval(x, y, 3, 3)
        }

        // Draw text characters with random rotation and color
        val fontSize = (height * 0.6).toInt()
        g2d.font = Font("SansSerif", Font.BOLD, fontSize)
        val charWidth = (width - 20) / text.length

        text.forEachIndexed { index, char ->
            g2d.color = Color(
                Random.nextInt(0, 100),
                Random.nextInt(0, 100),
                Random.nextInt(0, 100)
            )

            val x = 10 + index * charWidth
            val y = height / 2 + fontSize / 3 + Random.nextInt(-5, 6)

            // Random rotation for each character
            val rotation = Math.toRadians(Random.nextDouble(-15.0, 15.0))
            val origTransform = g2d.transform
            g2d.rotate(rotation, x.toDouble() + charWidth / 2.0, y.toDouble())
            g2d.drawString(char.toString(), x, y)
            g2d.transform = origTransform
        }

        // Border
        g2d.color = Color(150, 150, 150)
        g2d.drawRect(0, 0, width - 1, height - 1)

        g2d.dispose()
        return image
    }

    private fun encodeImageToBase64(image: BufferedImage): String {
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }
}

/**
 * Image CAPTCHA challenge response.
 */
data class ImageCaptchaChallenge(
    val captchaId: String,
    val imageBase64: String,
    val ttlSeconds: Long
)

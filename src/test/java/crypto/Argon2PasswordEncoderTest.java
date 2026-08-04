package crypto;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version : 
 * @since :  03/03/2025, Monday
 **/
public class Argon2PasswordEncoderTest {

    private final Argon2PasswordEncoder argon2PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Test
    void password_should_be_equals_after_encoding() {
        String password = "password";
        String encodedPassword = argon2PasswordEncoder.encode(password);
        Assertions.assertTrue(argon2PasswordEncoder.matches(password, encodedPassword));
    }

    @Test
    void password_should_be_different_after_encoding() {
        String password = "Test123";
        String encodedPassword = argon2PasswordEncoder.encode(password);

        Assertions.assertFalse(argon2PasswordEncoder.matches("Test456", encodedPassword));
    }

}
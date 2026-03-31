package com.ntt.authservice.utils.crypto;


public interface PasswordEncoder {

    String encode(CharSequence password);

    boolean matches(CharSequence password, String encodedPassword);

}
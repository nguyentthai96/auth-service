package com.ntt.authservice.exceptions

import com.ntt.basecore.exception.base.ErrorService


/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  03/03/2025, Monday
 */
enum class AuthErrorCode(val code: String, val msgCode:String, val description: String) : ErrorService by AuthErrorBase(code,msgCode, description) {
    USER_NOT_FOUND("001", "USER_NOT_FOUND", "User not found"),
    AUTH_EXPIRE_SESSION("001", "AUTH_EXPIRE_SESSION", "User not found"),
    AUTH_WRONG_MANY_TIME("001", "AUTH_WRONG_MANY_TIME", "The number of login errors exceeds the limit, please try again in minutes."),

    ;
}

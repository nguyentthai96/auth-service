package com.ntt.authservice.exceptions

import com.ntt.basecore.exception.base.ErrorService


/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  03/03/2025, Monday
 */
enum class ErrorCode(val code: String, val msgCode: String, val description: String) :
    ErrorService by AuthErrorBase(code, msgCode, description) {
    USER_NOT_FOUND("001", "ERORRO", "User not found"), ;


}

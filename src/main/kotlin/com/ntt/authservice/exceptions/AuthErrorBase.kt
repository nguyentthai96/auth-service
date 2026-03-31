package com.ntt.authservice.exceptions

import com.ntt.basecore.exception.base.ErrorCodeBase


/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  07/03/2025, Friday
 **/
class AuthErrorBase(code: String, msgCode:String, description: String) : ErrorCodeBase(code, msgCode, description) {
    override fun getService(): String {
        return "AUTH_SERVICE" // service name of error collection
    }
}
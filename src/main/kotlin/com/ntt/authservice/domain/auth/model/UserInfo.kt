package com.ntt.authservice.domain.auth.model


/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  24/03/2025, Monday
 **/
data class UserInfo(
    val id: String,
    val name: String? = null,
    val email: String,
    val password: String,
    val roles: String
) {

    fun toRoles(): List<String> {
        return roles.split(",")
    }
}
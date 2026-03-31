package com.ntt.authservice.repository.entity

import com.ntt.basecore.model.auditing.BaseEntityPersistentAuditable

/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  24/03/2025, Monday
 **/
class UserInfoEntity: BaseEntityPersistentAuditable<String>() {

    lateinit var username: String
    lateinit var password: CharSequence
    var email: String? = null
    var phone: String? = null
    var address: String? = null
    lateinit var fullName: String
    var avatar: String? = null
}
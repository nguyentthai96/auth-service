package com.ntt.authservice.repository

import com.ntt.authservice.repository.entity.UserInfoEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  24/03/2025, Monday
 **/
@Repository
interface UserInfoRepository : JpaRepository<UserInfoEntity, String> {

    fun findByEmail(email: String): Optional<UserInfoEntity>
}
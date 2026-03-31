package com.ntt.authservice.domain.service.mapper

import com.ntt.authservice.domain.auth.model.UserInfo
import com.ntt.authservice.repository.entity.UserInfoEntity
import org.apache.ibatis.annotations.Mapper
import org.mapstruct.factory.Mappers

/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  24/03/2025, Monday
 **/
@Mapper
interface UserInfoMapper {
    companion object {
        val INSTANCE: UserInfoMapper = Mappers.getMapper(UserInfoMapper::class.java)
    }

    fun toModel(entity: UserInfoEntity): UserInfo
}
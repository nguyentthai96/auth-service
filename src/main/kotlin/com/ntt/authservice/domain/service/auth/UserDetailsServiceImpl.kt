package com.ntt.authservice.domain.service.auth

import com.ntt.authservice.domain.auth.model.UserInfoDetails
import com.ntt.authservice.domain.service.mapper.UserInfoMapper
import com.ntt.authservice.repository.UserInfoRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  24/03/2025, Monday
 **/
@Service
class UserDetailsServiceImpl(
    val encoder: PasswordEncoder,
    val repository: UserInfoRepository
) : UserDetailsService {


    override fun loadUserByUsername(username: String): UserDetails {
        val userDetail = repository.findByEmail(username); // Assuming 'email' is used as username
        // Converting UserInfo to UserDetails
        return userDetail.map(UserInfoMapper.INSTANCE::toModel)
            .map(::UserInfoDetails)
            .orElseThrow {
                UsernameNotFoundException("User not found with email: $username")
            }
    }
}
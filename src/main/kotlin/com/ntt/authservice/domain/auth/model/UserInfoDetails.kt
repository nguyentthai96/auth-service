package com.ntt.authservice.domain.auth.model

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.stream.Collectors


/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  24/03/2025, Monday
 **/
class UserInfoDetails(userInfo: UserInfo) : UserDetails {
    val username: String // Changed from 'name' to 'email' for clarity
    val password: String
    val authorities: List<GrantedAuthority>

    init {
        this.username = userInfo.email // Use email as username
        this.password = userInfo.password
        this.authorities = userInfo
            .toRoles()
            .stream()
            .map { role: String? -> SimpleGrantedAuthority(role) }
            .collect(Collectors.toList())
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return authorities
    }

    override fun getPassword(): String {
        return password
    }

    override fun getUsername(): String {
        return username
    }

    override fun isAccountNonExpired(): Boolean {
        return true
    }

    override fun isAccountNonLocked(): Boolean {
        return true
    }

    override fun isCredentialsNonExpired(): Boolean {
        return true
    }

    override fun isEnabled(): Boolean {
        return true
    }
}
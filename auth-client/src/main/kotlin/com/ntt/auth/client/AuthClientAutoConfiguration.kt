package com.ntt.auth.client

import com.ntt.auth.client.entity.UserReadModel
import com.ntt.auth.client.repository.PermissionReadRepository
import com.ntt.auth.client.repository.RoleReadRepository
import com.ntt.auth.client.repository.UserReadRepository
import com.ntt.auth.client.service.JpaPermissionChecker
import com.ntt.auth.client.service.JpaUserReader
import com.ntt.basebusiness.shared.IPermissionChecker
import com.ntt.basebusiness.shared.IUserReader
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Zero-config auto-configuration for auth-client.
 *
 * Activated by default (`app.auth.client.enabled=true`).
 * Consumer services just add the dependency — beans are registered automatically.
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "app.auth.client",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
@EntityScan(basePackageClasses = [UserReadModel::class])
@EnableJpaRepositories(basePackageClasses = [UserReadRepository::class])
class AuthClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IUserReader::class)
    fun jpaUserReader(repository: UserReadRepository): IUserReader {
        return JpaUserReader(repository)
    }

    @Bean
    @ConditionalOnMissingBean(IPermissionChecker::class)
    fun jpaPermissionChecker(
        permissionRepository: PermissionReadRepository,
        roleRepository: RoleReadRepository
    ): IPermissionChecker {
        return JpaPermissionChecker(permissionRepository, roleRepository)
    }
}

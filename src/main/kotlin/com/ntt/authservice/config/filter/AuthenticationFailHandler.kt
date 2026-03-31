package com.ntt.authservice.config.filter

import com.ntt.authservice.exceptions.AuthErrorCode
import com.ntt.basecore.domain.web.payload.ResponseMessage
import com.ntt.basecore.utils.ResponseHttpUtil
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import java.io.IOException
import java.util.concurrent.TimeUnit


/**
 *
 * @author : nguyentthai96 - nguyentthai96@gmail.com
 * @version :
 * @since :  24/03/2025, Monday
 **/
@Component
class AuthenticationFailHandler : SimpleUrlAuthenticationFailureHandler() {
    @Autowired
    private val tokenProperties: XbootTokenProperties? = null

    @Autowired
    private val redisTemplate: RedisTemplateHelper? = null

    @Throws(IOException::class, ServletException::class)
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        e: AuthenticationException
    ) {
        if (e is UsernameNotFoundException || e is BadCredentialsException) {
            val username = request.getParameter("username")
            recordLoginTime(username)
            val key = "loginTimeLimit:$username"
            var value: String = redisTemplate.get(key)
            if (!StringUtils.hasText(value)) {
                value = "0"
            }
            //获取已登录错误次数
            val loginFailTime = value.toInt()
            val restLoginTime: Int = tokenProperties.getLoginTimeLimit() - loginFailTime
            log.info("用户" + username + "登录失败，还有" + restLoginTime + "次机会")
            if (restLoginTime <= 3 && restLoginTime > 0) {
                ResponseHttpUtil.out(
                    response,
                    ResponseMessage.of(
                        AuthErrorCode.AUTH_EXPIRE_SESSION,
                        AuthErrorCode.AUTH_EXPIRE_SESSION.description
                    )
                )
                return
            }
            if (restLoginTime <= 0) {
                ResponseHttpUtil.out(
                    response,
                    ResponseMessage.of(
                        AuthErrorCode.AUTH_WRONG_MANY_TIME,
                        AuthErrorCode.AUTH_WRONG_MANY_TIME.description
                    )
                )
                return
            }

            ResponseHttpUtil.out(response, ResponseHttpUtil.resultMap(false, 500, "用户名或密码错误"))
        } else if (e is DisabledException) {
            ResponseHttpUtil.out(response, ResponseHttpUtil.resultMap(false, 500, "账户被禁用，请联系管理员"))
        } else if (e is LoginFailLimitException) {
            ResponseHttpUtil.out(
                response,
                ResponseHttpUtil.resultMap(false, 500, (e as LoginFailLimitException).getMsg())
            )
        } else {
            ResponseHttpUtil.out(response, ResponseHttpUtil.resultMap(false, 500, "登录失败，其他内部错误"))
        }
    }

    /**
     * Count the number of user login errors
     */
    fun recordLoginTime(username: String): Boolean {
        val key = "loginTimeLimit:$username"
        val flagKey = "loginFailFlag:$username"
        var value: String = redisTemplate.get(key)
        if (!StringUtils.hasText(value)) {
            value = "0"
        }
        // 获取已登录错误次数
        val loginFailTime = value.toInt() + 1
        redisTemplate.set(key, loginFailTime.toString(), tokenProperties.getLoginAfterTime(), TimeUnit.MINUTES)
        if (loginFailTime >= tokenProperties.getLoginTimeLimit()) {
            redisTemplate.set(flagKey, "fail", tokenProperties.getLoginAfterTime(), TimeUnit.MINUTES)
            return false
        }
        return true
    }
}
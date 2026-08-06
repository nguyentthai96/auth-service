package com.ntt.authservice.auth.adapter.`in`.web

import com.ntt.basecore.model.id.SnowflakeIdGenerator
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.boot.test.context.TestComponent
import org.springframework.context.annotation.EnableAspectJAutoProxy

@Aspect
@TestComponent
@EnableAspectJAutoProxy(proxyTargetClass = true)
class TestIdGeneratorAspect {
    @Around("execution(* org.springframework.data.repository.CrudRepository+.save*(..))")
    fun assignIdBeforeSave(pjp: ProceedingJoinPoint): Any? {
        val args = pjp.args
        for (arg in args) {
            if (arg is Iterable<*>) {
                arg.forEach { assignIdIfMissing(it) }
            } else {
                assignIdIfMissing(arg)
            }
        }
        return pjp.proceed(args)
    }

    private fun assignIdIfMissing(entity: Any?) {
        if (entity == null) return
        try {
            var clazz: Class<*>? = entity.javaClass
            while (clazz != null && clazz != Any::class.java) {
                try {
                    val idField = clazz.getDeclaredField("id")
                    idField.isAccessible = true
                    val currentId = idField.get(entity)
                    if (currentId == null || currentId == 0L) {
                        idField.set(entity, SnowflakeIdGenerator.nextId())
                    }
                    break
                } catch (e: NoSuchFieldException) {
                    clazz = clazz.superclass
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}

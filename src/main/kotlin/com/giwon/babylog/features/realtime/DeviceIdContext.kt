package com.giwon.babylog.features.realtime

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

object DeviceIdHolder {
    private val tl = ThreadLocal<String?>()
    fun set(v: String?) = tl.set(v)
    fun get(): String? = tl.get()
    fun clear() = tl.remove()
}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class DeviceIdFilter : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val deviceId = (request as? HttpServletRequest)?.getHeader("X-Device-Id")
        DeviceIdHolder.set(deviceId)
        try {
            chain.doFilter(request, response)
        } finally {
            DeviceIdHolder.clear()
        }
    }
}

package com.giwon.babylog.features.push

import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.util.concurrent.Executors

@Component
class ExpoPushSender {

    private val rest = RestTemplate()
    private val executor = Executors.newSingleThreadExecutor()

    fun send(tokens: List<String>, title: String, body: String, data: Map<String, Any?> = emptyMap()) {
        if (tokens.isEmpty()) return
        val messages = tokens.map { token ->
            mapOf(
                "to" to token,
                "title" to title,
                "body" to body,
                "sound" to "default",
                "data" to data,
            )
        }
        executor.submit {
            runCatching {
                val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
                rest.postForEntity(EXPO_PUSH_URL, HttpEntity(messages, headers), String::class.java)
            }.onFailure { it.printStackTrace() }
        }
    }

    companion object {
        const val EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send"
    }
}

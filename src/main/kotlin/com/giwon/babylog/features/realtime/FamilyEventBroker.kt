package com.giwon.babylog.features.realtime

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class FamilyEventBroker(private val jdbc: JdbcTemplate) {

    private val emitters = ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>()

    fun subscribe(familyId: String): SseEmitter {
        val emitter = SseEmitter(SSE_TIMEOUT_MS)
        val list = emitters.computeIfAbsent(familyId) { CopyOnWriteArrayList() }
        list.add(emitter)

        emitter.onCompletion { remove(familyId, emitter) }
        emitter.onTimeout { remove(familyId, emitter) }
        emitter.onError { remove(familyId, emitter) }

        runCatching { emitter.send(SseEmitter.event().name("ready").data("ok")) }
        return emitter
    }

    fun publish(event: FamilyEvent) {
        val list = emitters[event.familyId] ?: return
        val dead = mutableListOf<SseEmitter>()
        for (e in list) {
            try {
                e.send(SseEmitter.event().name(event.type).data(event))
            } catch (_: IOException) {
                dead += e
            } catch (_: IllegalStateException) {
                dead += e
            }
        }
        list.removeAll(dead.toSet())
    }

    fun publishForBaby(
        babyId: String,
        type: String,
        actorDeviceId: String?,
        payload: Any?,
    ) {
        val familyId = familyIdOf(babyId) ?: return
        publish(FamilyEvent(type, familyId, babyId, actorDeviceId, payload))
    }

    private fun familyIdOf(babyId: String): String? = runCatching {
        jdbc.queryForObject(
            "select family_id from bl_babies where id = ?",
            String::class.java,
            babyId,
        )
    }.getOrNull()

    private fun remove(familyId: String, emitter: SseEmitter) {
        emitters[familyId]?.remove(emitter)
    }

    companion object {
        const val SSE_TIMEOUT_MS: Long = 5 * 60 * 1000L
    }
}

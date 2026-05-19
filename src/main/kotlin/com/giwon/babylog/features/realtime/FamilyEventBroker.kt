package com.giwon.babylog.features.realtime

import com.giwon.babylog.features.baby.BabyLookupRepository
import com.giwon.babylog.features.push.ExpoPushSender
import com.giwon.babylog.features.push.PushTokenService
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class FamilyEventBroker(
    private val babyLookup: BabyLookupRepository,
    private val pushTokenService: PushTokenService,
    private val expoPushSender: ExpoPushSender,
) {

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
        payload: Any?,
        actorDeviceId: String? = null,
    ) {
        val familyId = familyIdOf(babyId) ?: return
        val resolvedDeviceId = actorDeviceId ?: DeviceIdHolder.get()
        publish(FamilyEvent(type, familyId, babyId, resolvedDeviceId, payload))
        sendPushIfNeeded(familyId, babyId, type, resolvedDeviceId)
    }

    private fun sendPushIfNeeded(familyId: String, babyId: String, type: String, actorDeviceId: String?) {
        val (title, body) = buildNotification(babyId, type) ?: return
        val tokens = pushTokenService.tokensForFamilyExcept(familyId, actorDeviceId)
        if (tokens.isEmpty()) return
        expoPushSender.send(
            tokens,
            title,
            body,
            mapOf("type" to type, "babyId" to babyId, "familyId" to familyId),
        )
    }

    private fun buildNotification(babyId: String, type: String): Pair<String, String>? {
        val (title, action) = when (type) {
            "FEED_CREATED" -> "🍼 수유" to "기록"
            "DIAPER_CREATED" -> "💧 기저귀" to "교체"
            "SLEEP_STARTED" -> "😴 수면" to "시작"
            "SLEEP_ENDED" -> "🌅 수면" to "끝"
            "GROWTH_CREATED" -> "📏 성장" to "측정"
            "HEALTH_CREATED" -> "🩺 건강" to "기록"
            else -> return null
        }
        val babyName = babyLookup.findBabyName(babyId) ?: "아기"
        return title to "$babyName $action 했어요"
    }

    private fun familyIdOf(babyId: String): String? = babyLookup.findFamilyId(babyId)

    private fun remove(familyId: String, emitter: SseEmitter) {
        emitters[familyId]?.remove(emitter)
    }

    companion object {
        const val SSE_TIMEOUT_MS: Long = 5 * 60 * 1000L
    }
}

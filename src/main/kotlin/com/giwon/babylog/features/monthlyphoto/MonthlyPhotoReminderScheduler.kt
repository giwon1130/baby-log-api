package com.giwon.babylog.features.monthlyphoto

import com.giwon.babylog.common.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*

/**
 * 매일 09:00 KST 월차 도래일 푸시 발송.
 * Application 에 @EnableScheduling 이 켜져 있어야 한다 (이미 있음).
 */
@Component
class MonthlyPhotoReminderScheduler(private val service: MonthlyPhotoReminderService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    fun runDailyAt9Am() {
        log.info("MonthlyPhotoReminderScheduler tick")
        runCatching { service.runDaily() }
            .onFailure { log.error("MonthlyPhotoReminderScheduler failed", it) }
    }
}

/** 운영 수동 트리거. */
@RestController
@RequestMapping("/api/v1/monthly-photo-reminders")
class MonthlyPhotoReminderController(private val service: MonthlyPhotoReminderService) {

    @PostMapping("/run")
    fun run(): ApiResponse<Map<String, Int>> =
        ApiResponse.ok(mapOf("sent" to service.runDaily()))

    @PostMapping("/catchup")
    fun catchup(): ApiResponse<Map<String, Int>> =
        ApiResponse.ok(mapOf("sent" to service.runCatchup()))
}

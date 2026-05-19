package com.giwon.babylog.features.summary

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 매일 저녁 21:00 KST 일일 요약 푸시 트리거.
 * Application 에 @EnableScheduling 이 켜져 있어야 한다.
 */
@Component
class DailySummaryScheduler(private val service: DailySummaryService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 21 * * *", zone = "Asia/Seoul")
    fun runDailyAt9Pm() {
        log.info("DailySummaryScheduler tick — running for all families")
        runCatching { service.runForAllFamilies() }
            .onFailure { log.error("DailySummaryScheduler failed", it) }
    }
}

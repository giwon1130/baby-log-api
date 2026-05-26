package com.giwon.babylog.features.monthlyphoto

import java.time.OffsetDateTime

/** 월 증명사진 단건 응답 (앱 슬롯 표시용). */
data class MonthlyPhotoResponse(
    val id: String,
    val babyId: String,
    val monthIndex: Int,             // 1..12
    val photoUrl: String,            // 외부 접근 URL — 앱이 그대로 사용
    val thumbnailUrl: String?,
    val storageKey: String?,         // 볼륨 내 상대 경로 (디버그·삭제용)
    val takenAt: OffsetDateTime,
    val caption: String?,
    val locationHint: String?,
    val updatedAt: OffsetDateTime,
)

/**
 * 내부 upsert 입력. Controller 가 multipart 업로드 처리 후 호출.
 */
data class MonthlyPhotoUpsertInput(
    val monthIndex: Int,             // 1..12
    val photoUrl: String,
    val thumbnailUrl: String? = null,
    val storageKey: String? = null,
    val takenAt: OffsetDateTime? = null,
    val caption: String? = null,
    val locationHint: String? = null,
)

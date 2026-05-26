package com.giwon.babylog.features.monthlyphoto

import java.time.OffsetDateTime

/** 월 증명사진 단건 응답 (앱 슬롯 표시용). */
data class MonthlyPhotoResponse(
    val id: String,
    val babyId: String,
    val monthIndex: Int,             // 1..12
    val photoUrl: String,
    val thumbnailUrl: String?,
    val cloudinaryPublicId: String?,
    val takenAt: OffsetDateTime,
    val caption: String?,
    val locationHint: String?,
    val updatedAt: OffsetDateTime,
)

/**
 * 슬롯에 사진 저장(upsert). 같은 (baby, monthIndex) 가 이미 있으면 덮어쓰기.
 * 사진 자체는 클라이언트가 Cloudinary 에 직접 업로드한 결과(secure_url, public_id) 를 들고 옴.
 */
data class MonthlyPhotoUpsertRequest(
    val monthIndex: Int,             // 1..12
    val photoUrl: String,
    val thumbnailUrl: String? = null,
    val cloudinaryPublicId: String? = null,
    val takenAt: OffsetDateTime? = null,
    val caption: String? = null,
    val locationHint: String? = null,
)

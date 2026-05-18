package com.giwon.babylog.features.realtime

data class FamilyEvent(
    val type: String,
    val familyId: String,
    val babyId: String?,
    val actorDeviceId: String?,
    val payload: Any?,
)

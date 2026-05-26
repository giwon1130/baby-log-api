package com.giwon.babylog.features.monthlyphoto

import com.giwon.babylog.common.ApiResponse
import com.giwon.babylog.features.upload.UploadStorageService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/v1/babies/{babyId}/monthly-photos")
class MonthlyPhotoController(
    private val service: MonthlyPhotoService,
    private val storage: UploadStorageService,
    @Value("\${baby-log.upload.public-base-url:}") private val publicBaseUrl: String,
) {

    @GetMapping
    fun list(@PathVariable babyId: String): ApiResponse<List<MonthlyPhotoResponse>> =
        ApiResponse.ok(service.list(babyId))

    /**
     * 슬롯에 사진 업로드 + 메타 저장 (multipart).
     * form fields:
     *   file:         이미지 바이너리 (multipart part)
     *   monthIndex:   1..12
     *   takenAt:      ISO-8601 (선택)
     *   caption:      (선택)
     *   locationHint: (선택)
     */
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable babyId: String,
        @RequestPart("file") file: MultipartFile,
        @RequestParam("monthIndex") monthIndex: Int,
        @RequestParam("takenAt", required = false) takenAt: String?,
        @RequestParam("caption", required = false) caption: String?,
        @RequestParam("locationHint", required = false) locationHint: String?,
    ): ApiResponse<MonthlyPhotoResponse> {
        require(!file.isEmpty) { "파일이 비어 있어." }
        require(monthIndex in 1..12) { "monthIndex 는 1..12 범위여야 해." }

        val storageKey = storage.save(
            subdir = "monthly-photos/$babyId",
            originalFilename = file.originalFilename,
            contentType = file.contentType,
        ) { file.inputStream }

        val photoUrl = buildPhotoUrl(storageKey)

        val input = MonthlyPhotoUpsertInput(
            monthIndex = monthIndex,
            photoUrl = photoUrl,
            storageKey = storageKey,
            takenAt = takenAt?.let { OffsetDateTime.parse(it) },
            caption = caption?.takeIf { it.isNotBlank() },
            locationHint = locationHint?.takeIf { it.isNotBlank() },
        )
        return ApiResponse.ok(service.upsert(babyId, input))
    }

    @DeleteMapping("/{monthIndex}")
    fun delete(
        @PathVariable babyId: String,
        @PathVariable monthIndex: Int,
    ): ApiResponse<Map<String, Any>> {
        service.delete(babyId, monthIndex)
        return ApiResponse.ok(mapOf("status" to "deleted", "monthIndex" to monthIndex))
    }

    private fun buildPhotoUrl(storageKey: String): String {
        val base = if (publicBaseUrl.isNotBlank()) {
            publicBaseUrl.trimEnd('/')
        } else {
            // forward-headers-strategy=framework 덕분에 X-Forwarded-* 가 자동 반영됨
            ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString().trimEnd('/')
        }
        return "$base/api/v1/uploads/$storageKey"
    }
}

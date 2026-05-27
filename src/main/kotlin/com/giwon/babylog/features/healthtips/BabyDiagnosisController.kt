package com.giwon.babylog.features.healthtips

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

data class SetTaskDoneRequest(val done: Boolean)

@RestController
@RequestMapping("/api/v1/babies/{babyId}/diagnoses")
class BabyDiagnosisController(private val service: BabyDiagnosisService) {

    @GetMapping
    fun list(
        @PathVariable babyId: String,
        @RequestParam("includeResolved", required = false) includeResolved: Boolean = false,
    ): ApiResponse<List<BabyDiagnosisResponse>> =
        ApiResponse.ok(service.list(babyId, includeResolved))

    @PostMapping
    fun create(
        @PathVariable babyId: String,
        @RequestBody req: CreateDiagnosisRequest,
    ): ApiResponse<BabyDiagnosisResponse> =
        ApiResponse.ok(service.create(babyId, req))

    @PostMapping("/{diagnosisId}/resolve")
    fun resolve(
        @PathVariable babyId: String,
        @PathVariable diagnosisId: String,
    ): ApiResponse<Map<String, String>> {
        service.resolve(diagnosisId)
        return ApiResponse.ok(mapOf("status" to "resolved", "id" to diagnosisId))
    }

    @DeleteMapping("/{diagnosisId}")
    fun delete(
        @PathVariable babyId: String,
        @PathVariable diagnosisId: String,
    ): ApiResponse<Map<String, String>> {
        service.delete(diagnosisId)
        return ApiResponse.ok(mapOf("status" to "deleted", "id" to diagnosisId))
    }

    /** 진단의 오늘 체크리스트 + 최근 7일 진행도 */
    @GetMapping("/{diagnosisId}/checklist")
    fun checklist(
        @PathVariable babyId: String,
        @PathVariable diagnosisId: String,
    ): ApiResponse<DailyChecklistResponse> =
        ApiResponse.ok(service.getChecklist(diagnosisId))

    @PostMapping("/{diagnosisId}/tasks/{taskKey}")
    fun setTaskDone(
        @PathVariable babyId: String,
        @PathVariable diagnosisId: String,
        @PathVariable taskKey: String,
        @RequestBody req: SetTaskDoneRequest,
    ): ApiResponse<Map<String, Any>> {
        service.setTaskDone(diagnosisId, taskKey, req.done)
        return ApiResponse.ok(mapOf("done" to req.done, "taskKey" to taskKey))
    }
}

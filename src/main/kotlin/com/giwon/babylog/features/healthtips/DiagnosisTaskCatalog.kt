package com.giwon.babylog.features.healthtips

/**
 * 진단된 이슈별 권장 일일 체크 task.
 *
 * HealthTipsCatalog.tip_id 를 key 로, 사전 정의된 task 리스트를 매핑.
 * 사용자가 매일 task 별로 '오늘 했음' 토글 → bl_diagnosis_task_done row.
 *
 * 의학적 처방이 아니라 일반 관리 체크리스트. 화면에서 면책 함께 노출.
 */
data class DiagnosisTask(
    val key: String,         // 'stretch', 'tummy-time' 등 — DB 의 task_key 와 매치
    val title: String,
    val hint: String,
)

object DiagnosisTaskCatalog {

    /** tip_id → 권장 task 리스트 */
    val byTip: Map<String, List<DiagnosisTask>> = mapOf(
        "torticollis" to listOf(
            DiagnosisTask("stretch", "목 스트레칭 좌·우 균등", "처방받은 동작을 좌우 각 3~5회"),
            DiagnosisTask("feeding-rotation", "수유 방향 번갈아", "짧아진 쪽 근육이 자연스럽게 늘도록"),
            DiagnosisTask("tummy-time", "터미 타임 5~10분", "깨어 있을 때만, 등·목 근력 균형"),
            DiagnosisTask("toy-opposite", "관심 자극 반대편 배치", "고개를 자연스럽게 돌리도록"),
            DiagnosisTask("sleep-direction", "잠자는 방향 바꾸기", "한쪽으로만 눕지 않도록"),
        ),
        "plagiocephaly" to listOf(
            DiagnosisTask("tummy-time", "터미 타임 5~10분", "뒤통수 압력 분산"),
            DiagnosisTask("sleep-direction", "잠자는 방향 바꾸기", "한쪽 머리만 눌리지 않도록"),
            DiagnosisTask("upright-hold", "안고 있는 시간 늘리기", "눕혀 두는 시간을 줄여 압력 분산"),
        ),
        "colic" to listOf(
            DiagnosisTask("5s-routine", "5S 진정 루틴", "속싸개·옆 자세·쉿·흔들·빨기"),
            DiagnosisTask("burp", "수유 후 트림 충분히", "5~10분, 공기 삼킴 줄이기"),
            DiagnosisTask("calm-env", "조용·어두운 환경", "자극 줄이기"),
        ),
        "diaper-rash" to listOf(
            DiagnosisTask("change-often", "기저귀 자주 갈기", "대변은 즉시"),
            DiagnosisTask("air-time", "공기 노출 5~10분", "통풍·건조"),
            DiagnosisTask("barrier", "산화아연 보호 연고", "얇게 도포"),
        ),
        "constipation" to listOf(
            DiagnosisTask("belly-massage", "배 마사지 시계 방향", "원형으로 부드럽게"),
            DiagnosisTask("bicycle-legs", "다리 자전거 운동", "장 운동 자극"),
            DiagnosisTask("hydration", "수분·수유량 점검", "이유식 시작 후 식이섬유"),
        ),
        "jaundice" to listOf(
            DiagnosisTask("feeding-frequent", "수유 자주", "빌리루빈 배설 도움"),
            DiagnosisTask("hospital-checkup", "외래 황달 수치 확인", "예약 챙기기"),
        ),
        "baby-acne" to listOf(
            DiagnosisTask("gentle-wash", "미지근한 물로 부드럽게", "비누 최소화"),
            DiagnosisTask("no-pick", "긁거나 짜지 않기", "손싸개·손톱 짧게"),
        ),
        "cradle-cap" to listOf(
            DiagnosisTask("oil-loosen", "베이비 오일 도포 15~30분", "비듬 부드럽게"),
            DiagnosisTask("gentle-comb", "부드러운 빗으로 살살", "강제로 떼지 말 것"),
        ),
        "spit-up" to listOf(
            DiagnosisTask("burp", "수유 중·후 트림", "공기 삼킴 감소"),
            DiagnosisTask("upright-20", "수유 후 20~30분 세워서", "역류 예방"),
            DiagnosisTask("small-frequent", "조금씩 자주", "한 번에 많이 X"),
        ),
        // 'fever' 는 일상 체크 task 대상 아님 — 발생 시 응급 가이드. 카탈로그 제외.
    )

    fun forTip(tipId: String): List<DiagnosisTask> = byTip[tipId] ?: emptyList()
}

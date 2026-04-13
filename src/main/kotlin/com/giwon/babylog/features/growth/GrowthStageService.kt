package com.giwon.babylog.features.growth

import org.springframework.stereotype.Service

data class GrowthStageResponse(
    val daysOld: Long,
    val stage: String,
    val title: String,
    val description: String,
    val tips: List<String>,
    val feedingGuideMl: IntRange,
    val feedingIntervalHours: ClosedFloatingPointRange<Double>,
)

@Service
class GrowthStageService {

    fun getStage(daysOld: Long): GrowthStageResponse = when {
        daysOld <= 3 -> GrowthStageResponse(
            daysOld = daysOld,
            stage = "NEWBORN_EARLY",
            title = "초기 신생아 (D+${daysOld})",
            description = "출생 직후 초유 수유 시기. 위 용량이 매우 작아 소량씩 자주 먹어요.",
            tips = listOf(
                "초유는 면역력 형성에 매우 중요해요.",
                "2~3시간마다 수유를 시도하세요.",
                "황달 여부를 주의 깊게 관찰하세요.",
                "체중이 출생 체중의 7~10% 이내로 감소하는 건 정상이에요.",
            ),
            feedingGuideMl = 5..15,
            feedingIntervalHours = 2.0..3.0,
        )
        daysOld <= 7 -> GrowthStageResponse(
            daysOld = daysOld,
            stage = "NEWBORN_WEEK1",
            title = "신생아 1주차 (D+${daysOld})",
            description = "모유 혹은 분유 수유 패턴을 잡아가는 시기예요.",
            tips = listOf(
                "하루 8~12회 수유가 일반적이에요.",
                "기저귀 교환 횟수로 수유량을 간접 확인할 수 있어요.",
                "탯줄 관리에 신경 써주세요.",
                "황달이 심해지면 즉시 병원을 방문하세요.",
            ),
            feedingGuideMl = 15..30,
            feedingIntervalHours = 2.0..3.0,
        )
        daysOld <= 14 -> GrowthStageResponse(
            daysOld = daysOld,
            stage = "NEWBORN_WEEK2",
            title = "신생아 2주차 (D+${daysOld})",
            description = "출생 체중을 회복하고 수유량이 늘어나는 시기예요.",
            tips = listOf(
                "대부분 출생 체중을 회복해요.",
                "수유 후 트림을 꼭 시켜주세요.",
                "수면 패턴이 아직 불규칙한 건 정상이에요.",
                "배꼽이 떨어지는 시기예요.",
            ),
            feedingGuideMl = 30..60,
            feedingIntervalHours = 2.0..3.0,
        )
        daysOld <= 30 -> GrowthStageResponse(
            daysOld = daysOld,
            stage = "NEWBORN_MONTH1",
            title = "신생아 3~4주차 (D+${daysOld})",
            description = "각성 시간이 늘어나고 주변을 인식하기 시작해요.",
            tips = listOf(
                "얼굴을 집중해서 바라보기 시작해요.",
                "소리에 반응하는 모습이 보여요.",
                "첫 번째 예방접종(BCG, B형 간염) 확인하세요.",
                "수유 후 역류가 있으면 상체를 약간 올려주세요.",
            ),
            feedingGuideMl = 60..90,
            feedingIntervalHours = 2.5..3.0,
        )
        daysOld <= 60 -> GrowthStageResponse(
            daysOld = daysOld,
            stage = "INFANT_MONTH2",
            title = "생후 2개월 (D+${daysOld})",
            description = "사회적 미소가 나타나고 눈 맞춤이 명확해지는 시기예요.",
            tips = listOf(
                "첫 사회적 미소가 나타날 수 있어요.",
                "소리 나는 방향을 고개 돌려 찾아요.",
                "2개월 예방접종 일정을 확인하세요.",
                "수면 시간이 조금씩 길어질 수 있어요.",
            ),
            feedingGuideMl = 90..120,
            feedingIntervalHours = 3.0..3.5,
        )
        daysOld <= 90 -> GrowthStageResponse(
            daysOld = daysOld,
            stage = "INFANT_MONTH3",
            title = "생후 3개월 (D+${daysOld})",
            description = "목 가누기가 시작되고 손발을 활발히 움직여요.",
            tips = listOf(
                "목을 가누기 시작해요 (터미 타임 연습).",
                "옹알이가 시작돼요.",
                "수면 패턴이 점차 자리 잡혀요.",
                "하루 4~6회 수유로 줄어들 수 있어요.",
            ),
            feedingGuideMl = 120..150,
            feedingIntervalHours = 3.0..4.0,
        )
        else -> GrowthStageResponse(
            daysOld = daysOld,
            stage = "INFANT_GROWING",
            title = "성장 중 (D+${daysOld})",
            description = "아기가 무럭무럭 자라고 있어요. 정기 검진을 통해 성장을 확인하세요.",
            tips = listOf(
                "정기적인 소아과 검진을 유지하세요.",
                "예방접종 일정을 놓치지 마세요.",
                "이유식 시작(4~6개월)을 준비하세요.",
            ),
            feedingGuideMl = 150..240,
            feedingIntervalHours = 3.5..4.5,
        )
    }
}

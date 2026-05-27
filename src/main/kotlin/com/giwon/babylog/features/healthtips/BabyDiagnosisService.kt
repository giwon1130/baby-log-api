package com.giwon.babylog.features.healthtips

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

data class BabyDiagnosisResponse(
    val id: String,
    val babyId: String,
    val tipId: String,
    val tipTitle: String,         // HealthTipsCatalog 에서 join
    val tipEmoji: String,
    val side: String?,
    val startedAt: String,        // ISO date
    val notes: String,
    val status: String,           // 'active' | 'resolved'
    val resolvedAt: String?,
)

data class CreateDiagnosisRequest(
    val tipId: String,
    val side: String? = null,
    val startedAt: String? = null,  // ISO date, default = today
    val notes: String? = null,
)

data class DailyChecklistTask(
    val key: String,
    val title: String,
    val hint: String,
    val doneToday: Boolean,
)

data class DailyChecklistResponse(
    val diagnosisId: String,
    val tipTitle: String,
    val date: String,
    val tasks: List<DailyChecklistTask>,
    val recentDays: List<DailyProgressItem>,    // 최근 7일 진행도
)

data class DailyProgressItem(val date: String, val done: Int, val total: Int)

@Service
class BabyDiagnosisService(private val jdbc: JdbcTemplate) {

    private val kst = ZoneId.of("Asia/Seoul")

    /** 아기의 활성/완료 진단 리스트 */
    fun list(babyId: String, includeResolved: Boolean = false): List<BabyDiagnosisResponse> {
        val sql = if (includeResolved) {
            "select * from bl_baby_diagnoses where baby_id = ? order by status, started_at desc"
        } else {
            "select * from bl_baby_diagnoses where baby_id = ? and status = 'active' order by started_at desc"
        }
        return jdbc.query(sql, { rs, _ -> rs.toResponse() }, babyId)
    }

    @Transactional
    fun create(babyId: String, req: CreateDiagnosisRequest): BabyDiagnosisResponse {
        require(HealthTipsCatalog.byId(req.tipId) != null) { "tip_id '${req.tipId}' 가 카탈로그에 없어." }
        val id = UUID.randomUUID().toString()
        val startedAt = req.startedAt?.let { LocalDate.parse(it) } ?: LocalDate.now(kst)
        jdbc.update(
            """
            insert into bl_baby_diagnoses (id, baby_id, tip_id, side, started_at, notes, status, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, 'active', now(), now())
            """.trimIndent(),
            id, babyId, req.tipId, req.side, startedAt, req.notes ?: "",
        )
        return getById(id)!!
    }

    @Transactional
    fun resolve(diagnosisId: String) {
        val updated = jdbc.update(
            "update bl_baby_diagnoses set status = 'resolved', resolved_at = now(), updated_at = now() where id = ?",
            diagnosisId,
        )
        if (updated == 0) throw IllegalArgumentException("진단을 찾을 수 없어.")
    }

    @Transactional
    fun delete(diagnosisId: String) {
        // 자식 task_done row 도 같이 삭제 (FK CASCADE 없으니 명시 제거)
        jdbc.update("delete from bl_diagnosis_task_done where diagnosis_id = ?", diagnosisId)
        val deleted = jdbc.update("delete from bl_baby_diagnoses where id = ?", diagnosisId)
        if (deleted == 0) throw IllegalArgumentException("진단을 찾을 수 없어.")
    }

    /** 진단의 오늘 체크리스트 + 최근 7일 진행도 */
    fun getChecklist(diagnosisId: String): DailyChecklistResponse {
        val diag = getById(diagnosisId) ?: throw IllegalArgumentException("진단을 찾을 수 없어.")
        val today = LocalDate.now(kst)
        val tasks = DiagnosisTaskCatalog.forTip(diag.tipId)

        val doneTodaySet: Set<String> = jdbc.query(
            "select task_key from bl_diagnosis_task_done where diagnosis_id = ? and done_date = ?",
            { rs, _ -> rs.getString("task_key") },
            diagnosisId, today,
        ).toSet()

        val checklist = tasks.map {
            DailyChecklistTask(it.key, it.title, it.hint, doneTodaySet.contains(it.key))
        }

        // 최근 7일 진행도 — 날짜별 done 카운트
        val recentRows = jdbc.query(
            """
            select done_date, count(*) as done_count
              from bl_diagnosis_task_done
             where diagnosis_id = ? and done_date >= ?
             group by done_date
             order by done_date desc
            """.trimIndent(),
            { rs, _ -> rs.getDate("done_date").toLocalDate() to rs.getInt("done_count") },
            diagnosisId, today.minusDays(6),
        ).toMap()

        val total = tasks.size
        val recent = (0..6).map { offset ->
            val d = today.minusDays(offset.toLong())
            DailyProgressItem(d.toString(), recentRows[d] ?: 0, total)
        }

        return DailyChecklistResponse(diagnosisId, diag.tipTitle, today.toString(), checklist, recent)
    }

    @Transactional
    fun setTaskDone(diagnosisId: String, taskKey: String, done: Boolean) {
        val today = LocalDate.now(kst)
        if (done) {
            jdbc.update(
                """
                insert into bl_diagnosis_task_done (diagnosis_id, task_key, done_date, done_at)
                values (?, ?, ?, now())
                on conflict (diagnosis_id, task_key, done_date) do nothing
                """.trimIndent(),
                diagnosisId, taskKey, today,
            )
        } else {
            jdbc.update(
                "delete from bl_diagnosis_task_done where diagnosis_id = ? and task_key = ? and done_date = ?",
                diagnosisId, taskKey, today,
            )
        }
    }

    private fun getById(id: String): BabyDiagnosisResponse? =
        jdbc.query(
            "select * from bl_baby_diagnoses where id = ?",
            { rs, _ -> rs.toResponse() },
            id,
        ).firstOrNull()

    private fun ResultSet.toResponse(): BabyDiagnosisResponse {
        val tipId = getString("tip_id")
        val tip = HealthTipsCatalog.byId(tipId)
        return BabyDiagnosisResponse(
            id = getString("id"),
            babyId = getString("baby_id"),
            tipId = tipId,
            tipTitle = tip?.title ?: tipId,
            tipEmoji = tip?.emoji ?: "🩺",
            side = getString("side"),
            startedAt = getDate("started_at").toLocalDate().toString(),
            notes = getString("notes"),
            status = getString("status"),
            resolvedAt = getObject("resolved_at", OffsetDateTime::class.java)?.toString(),
        )
    }
}

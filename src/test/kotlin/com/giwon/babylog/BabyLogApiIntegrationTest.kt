package com.giwon.babylog

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.giwon.babylog.bootstrap.BabyLogApiApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneOffset

@SpringBootTest(classes = [BabyLogApiApplication::class])
@AutoConfigureMockMvc
@Testcontainers
class BabyLogApiIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val jdbc: JdbcTemplate,
) {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("baby-log.db.url", postgres::getJdbcUrl)
            registry.add("baby-log.db.username", postgres::getUsername)
            registry.add("baby-log.db.password", postgres::getPassword)
        }
    }

    @BeforeEach
    fun clearDatabase() {
        jdbc.execute(
            """
            truncate table
                bl_sleep_records,
                bl_growth_records,
                bl_diaper_records,
                bl_feed_records,
                bl_babies,
                bl_families
            cascade
            """.trimIndent()
        )
    }

    @Test
    fun `family baby feed diaper sleep growth and stats APIs work together`() {
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        val morning = "${today}T01:00:00Z"
        val lateMorning = "${today}T03:00:00Z"

        val family = postJson("/api/v1/families").data()
        val familyId = family["id"].asText()
        val inviteCode = family["inviteCode"].asText()

        getJson("/api/v1/families/$familyId")
            .data()
            .also { assertThat(it["inviteCode"].asText()).isEqualTo(inviteCode) }
        getJson("/api/v1/families/join/$inviteCode")
            .data()
            .also { assertThat(it["id"].asText()).isEqualTo(familyId) }

        val baby = postJson(
            "/api/v1/families/$familyId/babies",
            """{"name":"아기","birthDate":"$today","gender":"FEMALE","birthWeightG":3200,"birthHeightCm":50.5}"""
        ).data()
        val babyId = baby["id"].asText()

        putJson(
            "/api/v1/families/$familyId/babies/$babyId",
            """{"name":"튼튼이","birthWeightG":3300,"birthHeightCm":51.0}"""
        ).data().also {
            assertThat(it["name"].asText()).isEqualTo("튼튼이")
            assertThat(it["birthWeightG"].asInt()).isEqualTo(3300)
        }

        getJson("/api/v1/families/$familyId/babies")
            .data()
            .also { assertThat(it).hasSize(1) }
        getJson("/api/v1/babies/$babyId/growth-stage?familyId=$familyId")
            .data()
            .also { assertThat(it["stage"].asText()).isEqualTo("NEWBORN_EARLY") }

        val feed = postJson(
            "/api/v1/babies/$babyId/feeds",
            """{"fedAt":"$morning","amountMl":90,"feedType":"FORMULA","note":"첫 수유"}"""
        ).data()
        val feedId = feed["id"].asText()
        assertThat(feed["nextFeedIntervalHours"].asDouble()).isEqualTo(2.5)

        putJson(
            "/api/v1/babies/$babyId/feeds/$feedId",
            """{"amountMl":120,"note":"수정된 수유"}"""
        ).data().also {
            assertThat(it["amountMl"].asInt()).isEqualTo(120)
            assertThat(it["note"].asText()).isEqualTo("수정된 수유")
        }
        getJson("/api/v1/babies/$babyId/feeds/latest")
            .data()
            .also { assertThat(it["id"].asText()).isEqualTo(feedId) }
        getJson("/api/v1/babies/$babyId/feeds?date=$today")
            .data()
            .also { assertThat(it).hasSize(1) }

        val diaper = postJson(
            "/api/v1/babies/$babyId/diapers",
            """{"changedAt":"$morning","diaperType":"WET","note":"젖음"}"""
        ).data()
        val diaperId = diaper["id"].asText()
        putJson(
            "/api/v1/babies/$babyId/diapers/$diaperId",
            """{"diaperType":"MIXED","note":"수정된 기저귀"}"""
        ).data().also {
            assertThat(it["diaperType"].asText()).isEqualTo("MIXED")
            assertThat(it["note"].asText()).isEqualTo("수정된 기저귀")
        }
        getJson("/api/v1/babies/$babyId/diapers/latest")
            .data()
            .also { assertThat(it["id"].asText()).isEqualTo(diaperId) }
        getJson("/api/v1/babies/$babyId/diapers?date=$today")
            .data()
            .also { assertThat(it).hasSize(1) }

        val sleep = postJson(
            "/api/v1/babies/$babyId/sleeps/start",
            """{"sleptAt":"$morning","note":"낮잠"}"""
        ).data()
        val sleepId = sleep["id"].asText()
        getJson("/api/v1/babies/$babyId/sleeps/active")
            .data()
            .also { assertThat(it["id"].asText()).isEqualTo(sleepId) }
        putJson(
            "/api/v1/babies/$babyId/sleeps/$sleepId",
            """{"sleptAt":"${today}T01:10:00Z","note":"수정된 낮잠"}"""
        ).data().also {
            assertThat(it["note"].asText()).isEqualTo("수정된 낮잠")
        }
        postJson(
            "/api/v1/babies/$babyId/sleeps/$sleepId/end",
            """{"wokeAt":"${today}T02:10:00Z"}"""
        ).data().also {
            assertThat(it["durationMinutes"].asLong()).isEqualTo(60)
            assertThat(it["wokeAt"].asText()).contains("02:10")
        }
        getJson("/api/v1/babies/$babyId/sleeps")
            .data()
            .also { assertThat(it).hasSize(1) }

        val growth = postJson(
            "/api/v1/babies/$babyId/growth-records",
            """{"measuredAt":"$lateMorning","weightG":3400,"heightCm":52.0,"headCm":35.0,"note":"성장"}"""
        ).data()
        val growthId = growth["id"].asText()
        getJson("/api/v1/babies/$babyId/growth-records")
            .data()
            .also { assertThat(it).hasSize(1) }

        getJson("/api/v1/babies/$babyId/stats/today")
            .data()
            .also {
                assertThat(it["feedCount"].asInt()).isEqualTo(1)
                assertThat(it["totalFeedMl"].asInt()).isEqualTo(120)
                assertThat(it["diaperCount"].asInt()).isEqualTo(1)
                assertThat(it["dirtyCount"].asInt()).isEqualTo(1)
                assertThat(it["sleepCount"].asInt()).isEqualTo(1)
                assertThat(it["totalSleepMinutes"].asLong()).isEqualTo(60)
            }
        getJson("/api/v1/babies/$babyId/stats/weekly")
            .data()
            .also {
                assertThat(it["feedStats"]).hasSize(7)
                assertThat(it["sleepStats"]).hasSize(7)
            }

        deleteJson("/api/v1/babies/$babyId/growth-records/$growthId")
        deleteJson("/api/v1/babies/$babyId/sleeps/$sleepId")
        deleteJson("/api/v1/babies/$babyId/diapers/$diaperId")
        deleteJson("/api/v1/babies/$babyId/feeds/$feedId")
    }

    @Test
    fun `delete baby cascades to all child records`() {
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        val morning = "${today}T01:00:00Z"

        val familyId = postJson("/api/v1/families").data()["id"].asText()
        val babyId = postJson(
            "/api/v1/families/$familyId/babies",
            """{"name":"삭제대상","birthDate":"2024-01-01","gender":"FEMALE"}""",
        ).data()["id"].asText()

        // 자식 레코드 생성: 수유, 기저귀, 수면, 성장, 건강
        postJson("/api/v1/babies/$babyId/feeds", """{"fedAt":"$morning","amountMl":80,"feedType":"FORMULA"}""")
        postJson("/api/v1/babies/$babyId/diapers", """{"changedAt":"$morning","diaperType":"WET"}""")
        postJson("/api/v1/babies/$babyId/sleeps/start", """{"sleptAt":"$morning"}""")
        postJson("/api/v1/babies/$babyId/growth-records", """{"measuredAt":"$morning","weightG":4500}""")

        // 아기 삭제
        deleteJson("/api/v1/families/$familyId/babies/$babyId")

        // 모든 자식 레코드와 아기 자체가 사라졌는지 확인 (DB 직접 조회)
        assertThat(jdbc.queryForObject("select count(*) from bl_babies where id = ?", Int::class.java, babyId)).isEqualTo(0)
        assertThat(jdbc.queryForObject("select count(*) from bl_feed_records where baby_id = ?", Int::class.java, babyId)).isEqualTo(0)
        assertThat(jdbc.queryForObject("select count(*) from bl_diaper_records where baby_id = ?", Int::class.java, babyId)).isEqualTo(0)
        assertThat(jdbc.queryForObject("select count(*) from bl_sleep_records where baby_id = ?", Int::class.java, babyId)).isEqualTo(0)
        assertThat(jdbc.queryForObject("select count(*) from bl_growth_records where baby_id = ?", Int::class.java, babyId)).isEqualTo(0)
    }

    private fun getJson(path: String): JsonNode =
        parse(
            mockMvc.perform(get(path))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().response.contentAsByteArray
        )

    private fun postJson(path: String, body: String = "{}"): JsonNode =
        parse(
            mockMvc.perform(post(path).json(body))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().response.contentAsByteArray
        )

    private fun putJson(path: String, body: String): JsonNode =
        parse(
            mockMvc.perform(put(path).json(body))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().response.contentAsByteArray
        )

    private fun deleteJson(path: String) {
        mockMvc.perform(delete(path))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    private fun parse(json: ByteArray): JsonNode = objectMapper.readTree(json)

    private fun JsonNode.data(): JsonNode = this["data"]

    private fun org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder.json(body: String) =
        characterEncoding("UTF-8").contentType(MediaType.APPLICATION_JSON).content(body.toByteArray(StandardCharsets.UTF_8))
}

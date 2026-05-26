package com.giwon.babylog.bootstrap

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

@Configuration
@EnableConfigurationProperties(BabyLogDbProperties::class)
class DatabaseConfig(private val props: BabyLogDbProperties) {

    @Bean
    fun dataSource(): DataSource = DriverManagerDataSource().apply {
        setDriverClassName(props.driverClassName)
        url = props.url
        username = props.username
        password = props.password
    }

    @Bean
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate = JdbcTemplate(dataSource)
}

@ConfigurationProperties(prefix = "baby-log.db")
data class BabyLogDbProperties(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val driverClassName: String = "org.postgresql.Driver",
)

@Configuration
class SchemaInitializer(private val jdbcTemplate: JdbcTemplate) {

    @PostConstruct
    fun init() {
        jdbcTemplate.execute("""
            create table if not exists bl_families (
                id varchar(36) primary key,
                invite_code varchar(16) not null unique,
                created_at timestamptz not null default now()
            )
        """.trimIndent())

        jdbcTemplate.execute("""
            create table if not exists bl_babies (
                id varchar(36) primary key,
                family_id varchar(36) not null references bl_families(id),
                name varchar(100) not null,
                birth_date date not null,
                gender varchar(10) not null,
                birth_weight_g integer,
                birth_height_cm double precision,
                created_at timestamptz not null default now()
            )
        """.trimIndent())

        jdbcTemplate.execute("""
            create table if not exists bl_feed_records (
                id varchar(36) primary key,
                baby_id varchar(36) not null references bl_babies(id),
                fed_at timestamptz not null,
                amount_ml integer not null,
                feed_type varchar(20) not null,
                note text not null default '',
                created_at timestamptz not null default now()
            )
        """.trimIndent())

        jdbcTemplate.execute("""
            create table if not exists bl_diaper_records (
                id varchar(36) primary key,
                baby_id varchar(36) not null references bl_babies(id),
                changed_at timestamptz not null,
                diaper_type varchar(20) not null,
                note text not null default '',
                created_at timestamptz not null default now()
            )
        """.trimIndent())

        jdbcTemplate.execute("""
            create table if not exists bl_growth_records (
                id varchar(36) primary key,
                baby_id varchar(36) not null references bl_babies(id),
                measured_at timestamptz not null,
                weight_g integer,
                height_cm double precision,
                head_cm double precision,
                note text not null default '',
                created_at timestamptz not null default now()
            )
        """.trimIndent())

        jdbcTemplate.execute("""
            create table if not exists bl_sleep_records (
                id varchar(36) primary key,
                baby_id varchar(36) not null references bl_babies(id),
                slept_at timestamptz not null,
                woke_at timestamptz,
                note text not null default '',
                created_at timestamptz not null default now()
            )
        """.trimIndent())

        jdbcTemplate.execute("""
            alter table bl_sleep_records
            add column if not exists note text not null default ''
        """.trimIndent())

        jdbcTemplate.execute("""
            alter table bl_sleep_records
            add column if not exists created_at timestamptz not null default now()
        """.trimIndent())

        // Health records table
        jdbcTemplate.execute("""
            create table if not exists bl_health_records (
                id varchar(36) primary key,
                baby_id varchar(36) not null references bl_babies(id),
                recorded_at timestamptz not null default now(),
                type varchar(20) not null,
                value double precision,
                name varchar(100) not null default '',
                note text not null default ''
            )
        """.trimIndent())
        jdbcTemplate.execute("create index if not exists idx_health_baby_recorded on bl_health_records(baby_id, recorded_at desc)")

        // Breastfeeding duration columns
        jdbcTemplate.execute("alter table bl_feed_records add column if not exists left_minutes double precision")
        jdbcTemplate.execute("alter table bl_feed_records add column if not exists right_minutes double precision")

        // Indexes for baby_id + timestamp lookups (main query pattern across all record tables)
        jdbcTemplate.execute("create index if not exists idx_babies_family_id on bl_babies(family_id)")
        jdbcTemplate.execute("create index if not exists idx_feed_baby_fed on bl_feed_records(baby_id, fed_at desc)")
        jdbcTemplate.execute("create index if not exists idx_diaper_baby_changed on bl_diaper_records(baby_id, changed_at desc)")
        jdbcTemplate.execute("create index if not exists idx_sleep_baby_slept on bl_sleep_records(baby_id, slept_at desc)")
        jdbcTemplate.execute("create index if not exists idx_growth_baby_measured on bl_growth_records(baby_id, measured_at desc)")

        // Cry analysis samples — audio features + context snapshot + user-confirmed ground truth.
        // This table powers the per-baby learning loop: the classifier weights its predictions
        // against past confirmed samples so accuracy improves as the parent keeps confirming.
        jdbcTemplate.execute("""
            create table if not exists bl_cry_samples (
                id varchar(36) primary key,
                baby_id varchar(36) not null references bl_babies(id),
                recorded_at timestamptz not null default now(),
                duration_sec double precision not null,
                -- audio features (from on-device extraction)
                cry_confidence_avg double precision,
                cry_confidence_max double precision,
                avg_volume_db double precision,
                peak_volume_db double precision,
                -- context snapshot at time of recording
                minutes_since_last_feed integer,
                minutes_since_last_diaper integer,
                minutes_since_last_sleep_start integer,
                minutes_since_last_sleep_end integer,
                is_during_sleep boolean not null default false,
                baby_age_days integer,
                time_of_day_hour integer,
                -- prediction + user confirmation
                predicted_label varchar(20) not null,
                predicted_confidence double precision not null,
                confirmed_label varchar(20),
                confirmed_at timestamptz,
                note text not null default ''
            )
        """.trimIndent())
        jdbcTemplate.execute("create index if not exists idx_cry_baby_recorded on bl_cry_samples(baby_id, recorded_at desc)")
        jdbcTemplate.execute("create index if not exists idx_cry_baby_confirmed on bl_cry_samples(baby_id) where confirmed_label is not null")

        jdbcTemplate.execute("""
            create table if not exists bl_push_tokens (
                device_id varchar(64) primary key,
                family_id varchar(36) not null references bl_families(id),
                expo_token text not null,
                label varchar(100) not null default '',
                platform varchar(20) not null default 'ios',
                updated_at timestamptz not null default now()
            )
        """.trimIndent())
        jdbcTemplate.execute("create index if not exists idx_push_tokens_family on bl_push_tokens(family_id)")
        // 일일 요약 수신 토글 (디바이스 단위). 기존 행은 ON 으로 — ALTER + default true.
        jdbcTemplate.execute(
            "alter table bl_push_tokens add column if not exists daily_summary_enabled boolean not null default true"
        )

        // Phase 2A — richer acoustic features (pitch, ZCR, rhythmicity).
        // Added as ALTER so existing rows stay untouched.
        listOf(
            "pitch_mean_hz double precision",
            "pitch_std_hz double precision",
            "pitch_max_hz double precision",
            "voiced_ratio double precision",
            "zcr_mean double precision",
            "rhythmicity double precision",
        ).forEach { colDef ->
            val colName = colDef.substringBefore(' ')
            jdbcTemplate.execute(
                "alter table bl_cry_samples add column if not exists $colDef"
                    .also { _ -> println("[schema] ensure column bl_cry_samples.$colName") }
            )
        }

        // 월 증명사진 슬롯 — 1~12개월. baby × month_index UNIQUE 라 재촬영은 덮어쓰기.
        // 저장소: Railway 영구 볼륨 (storage_key = 볼륨 내 상대 경로). photo_url 은 외부 접근 URL.
        jdbcTemplate.execute("""
            create table if not exists bl_monthly_photos (
                id varchar(36) primary key,
                baby_id varchar(36) not null references bl_babies(id),
                month_index smallint not null check (month_index between 1 and 12),
                photo_url text not null,
                thumbnail_url text,
                storage_key text,
                taken_at timestamptz not null,
                caption varchar(200),
                location_hint varchar(100),
                created_at timestamptz not null default now(),
                updated_at timestamptz not null default now(),
                unique (baby_id, month_index)
            )
        """.trimIndent())
        jdbcTemplate.execute(
            "create index if not exists idx_monthly_photos_baby on bl_monthly_photos(baby_id)"
        )
        // 마이그레이션 — 직전 배포에서 cloudinary_public_id 로 만들었던 컬럼을 storage_key 로 정리
        jdbcTemplate.execute(
            """
            do ${'$'}${'$'}
            begin
              if exists (
                select 1 from information_schema.columns
                where table_name = 'bl_monthly_photos' and column_name = 'cloudinary_public_id'
              ) then
                if not exists (
                  select 1 from information_schema.columns
                  where table_name = 'bl_monthly_photos' and column_name = 'storage_key'
                ) then
                  alter table bl_monthly_photos rename column cloudinary_public_id to storage_key;
                else
                  alter table bl_monthly_photos drop column cloudinary_public_id;
                end if;
              end if;
            end ${'$'}${'$'};
            """.trimIndent()
        )

        // 월차 알림 발송 기록 — (baby, month) 당 1회만 푸시하도록 idempotency.
        jdbcTemplate.execute("""
            create table if not exists bl_monthly_photo_reminders (
                baby_id varchar(36) not null references bl_babies(id),
                month_index smallint not null check (month_index between 1 and 12),
                sent_at timestamptz not null default now(),
                primary key (baby_id, month_index)
            )
        """.trimIndent())
    }
}

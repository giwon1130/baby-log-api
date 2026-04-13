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
                recorded_at timestamptz not null,
                diaper_type varchar(20) not null,
                color varchar(30) not null default '',
                consistency varchar(30) not null default '',
                note text not null default '',
                created_at timestamptz not null default now()
            )
        """.trimIndent())
    }
}

package com.giwon.babylog.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class SchemaInitializerMigrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    @Test
    fun `schema initializer adds missing sleep columns to existing tables`() {
        val dataSource = DriverManagerDataSource().apply {
            setDriverClassName(postgres.driverClassName)
            url = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
        }
        val jdbc = JdbcTemplate(dataSource)
        jdbc.execute("drop table if exists bl_sleep_records cascade")
        jdbc.execute("drop table if exists bl_babies cascade")
        jdbc.execute("drop table if exists bl_families cascade")
        jdbc.execute(
            """
            create table bl_families (
                id varchar(36) primary key,
                invite_code varchar(16) not null unique,
                created_at timestamptz not null default now()
            )
            """.trimIndent()
        )
        jdbc.execute(
            """
            create table bl_babies (
                id varchar(36) primary key,
                family_id varchar(36) not null references bl_families(id),
                name varchar(100) not null,
                birth_date date not null,
                gender varchar(10) not null
            )
            """.trimIndent()
        )
        jdbc.execute(
            """
            create table bl_sleep_records (
                id varchar(36) primary key,
                baby_id varchar(36) not null references bl_babies(id),
                slept_at timestamptz not null,
                woke_at timestamptz
            )
            """.trimIndent()
        )

        SchemaInitializer(jdbc).init()

        val columns = jdbc.queryForList(
            """
            select column_name
            from information_schema.columns
            where table_name = 'bl_sleep_records'
            """.trimIndent(),
            String::class.java
        )
        assertThat(columns).contains("note", "created_at")

        jdbc.update("insert into bl_families (id, invite_code) values ('family-1', 'ABCDEFGH')")
        jdbc.update(
            """
            insert into bl_babies (id, family_id, name, birth_date, gender)
            values ('baby-1', 'family-1', '아기', '2026-04-14', 'FEMALE')
            """.trimIndent()
        )
        jdbc.update(
            """
            insert into bl_sleep_records (id, baby_id, slept_at, note)
            values ('sleep-1', 'baby-1', '2026-04-14T01:00:00Z', '낮잠')
            """.trimIndent()
        )

        assertThat(
            jdbc.queryForObject(
                "select note from bl_sleep_records where id = 'sleep-1'",
                String::class.java
            )
        ).isEqualTo("낮잠")
    }
}

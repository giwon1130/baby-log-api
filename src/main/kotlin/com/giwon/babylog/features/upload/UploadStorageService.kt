package com.giwon.babylog.features.upload

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * 업로드 파일을 Railway 영구 볼륨에 저장.
 *
 * 저장 경로: `${baby-log.upload.dir}/{subdir}/{uuid}.{ext}`
 * storage_key (DB) = `{subdir}/{uuid}.{ext}` — 상대 경로
 * 외부 URL = `{publicBaseUrl}/api/v1/uploads/{storage_key}`
 */
@Service
class UploadStorageService(
    @Value("\${baby-log.upload.dir:/app/data/uploads}") private val uploadDirCfg: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var root: Path

    @PostConstruct
    fun init() {
        root = Paths.get(uploadDirCfg).toAbsolutePath().normalize()
        Files.createDirectories(root)
        log.info("upload storage initialized. root={}", root)
    }

    /** 입력 스트림을 받아 저장. storage_key (root 기준 상대 경로) 반환. */
    fun save(
        subdir: String,
        originalFilename: String?,
        contentType: String?,
        inputStreamFactory: () -> java.io.InputStream,
    ): String {
        val ext = extOf(originalFilename, contentType)
        require(ext.isNotBlank()) { "지원하지 않는 이미지 형식이야 (jpg/png/heic/webp)." }
        val key = "$subdir/${UUID.randomUUID()}$ext"
        val dest = root.resolve(key).normalize()
        require(dest.startsWith(root)) { "잘못된 경로." } // path traversal 방지
        Files.createDirectories(dest.parent)
        inputStreamFactory().use { input ->
            Files.copy(input, dest, StandardCopyOption.REPLACE_EXISTING)
        }
        return key
    }

    fun delete(storageKey: String) {
        if (storageKey.isBlank()) return
        val dest = root.resolve(storageKey).normalize()
        if (!dest.startsWith(root)) return
        Files.deleteIfExists(dest)
    }

    fun resolve(storageKey: String): Path {
        val dest = root.resolve(storageKey).normalize()
        require(dest.startsWith(root)) { "잘못된 경로." }
        return dest
    }

    private fun extOf(name: String?, contentType: String?): String {
        // 1) 파일명 확장자 우선
        if (!name.isNullOrBlank()) {
            val idx = name.lastIndexOf('.')
            if (idx >= 0) {
                val candidate = name.substring(idx).lowercase()
                if (candidate in ALLOWED_EXT) return candidate
            }
        }
        // 2) content-type 기반 fallback
        return when (contentType?.lowercase()) {
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "image/heic", "image/heif" -> ".heic"
            "image/webp" -> ".webp"
            else -> ""
        }
    }

    companion object {
        val ALLOWED_EXT = setOf(".jpg", ".jpeg", ".png", ".heic", ".heif", ".webp")
    }
}

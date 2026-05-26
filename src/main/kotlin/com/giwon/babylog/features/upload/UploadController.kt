package com.giwon.babylog.features.upload

import org.springframework.core.io.UrlResource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.nio.file.Files
import java.time.Duration

@RestController
@RequestMapping("/api/v1/uploads")
class UploadController(private val storage: UploadStorageService) {

    /**
     * 볼륨에 저장된 업로드 파일을 외부에 서빙.
     * URL: /api/v1/uploads/{subdir}/{filename}
     */
    @GetMapping("/**")
    fun serve(request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<UrlResource> {
        val full = request.requestURI ?: return ResponseEntity.notFound().build()
        val prefix = "/api/v1/uploads/"
        if (!full.startsWith(prefix)) return ResponseEntity.notFound().build()
        val key = full.removePrefix(prefix).trim('/')
        if (key.isBlank() || key.contains("..")) return ResponseEntity.badRequest().build()

        val file = try { storage.resolve(key) } catch (e: Exception) {
            return ResponseEntity.badRequest().build()
        }
        if (!Files.exists(file)) return ResponseEntity.notFound().build()

        val contentType = Files.probeContentType(file) ?: "application/octet-stream"
        val resource = UrlResource(file.toUri())
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
            .body(resource)
    }
}

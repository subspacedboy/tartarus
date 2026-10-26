package club.subjugated.overlord_exe.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class UrlService(
    @Value("\${overlord.base_url:http://localhost:8090}") val baseUrl: String = "http://localhost:8090",
) {
    fun generateUrl(path: String): String {
        return "${baseUrl}/$path"
    }
}

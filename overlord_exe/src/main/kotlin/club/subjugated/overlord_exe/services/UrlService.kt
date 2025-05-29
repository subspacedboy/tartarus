package club.subjugated.overlord_exe.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class UrlService {
    @Value("\${overlord.base_url}") val baseUrl: String = ""

    fun generateUrl(path: String) : String {
        return "${baseUrl}/$path"
    }
}
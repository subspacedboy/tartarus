package club.subjugated.tartarus_coordinator.filters

import club.subjugated.tartarus_coordinator.models.BotState
import club.subjugated.tartarus_coordinator.services.AdminSessionService
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.BotService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import jakarta.persistence.PersistenceException
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

class WebUserAuthenticationFilter(
    private val authorSessionService: AuthorSessionService,
    private val lockUserSessionService: LockUserSessionService,
    private val adminSessionService: AdminSessionService,
    private val botService: BotService? = null,
) : OncePerRequestFilter() {

    private val filterLogger: Logger =
        LoggerFactory.getLogger(WebUserAuthenticationFilter::class.java)

    @Throws(IOException::class, ServletException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authenticateBearerToken(authHeader.substring(7), request)
        }
        filterChain.doFilter(request, response)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun authenticateBearerToken(token: String, request: HttpServletRequest) {
        try {
            val signedJWT = SignedJWT.parse(token)
            val sessionTokenName = signedJWT.jwtClaimsSet.subject
            val (publicKey, authorities) = resolveSessionKeyAndAuthorities(sessionTokenName)

            val sessionPublicECKey = getECPublicKeyFromCompressedKeyByteArray(publicKey)
            if (signedJWT.verify(ECDSAVerifier(sessionPublicECKey))) {
                val userDetails: UserDetails =
                    User.withUsername(sessionTokenName)
                        .password("")
                        .authorities(authorities)
                        .build()

                val authentication =
                    UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication
            }
        } catch (ex: java.text.ParseException) {
            this.filterLogger.warn("Malformed JWT token in Authorization header: ${ex.message}")
        } catch (ex: PersistenceException) {
            this.filterLogger.warn("We got a token but no backing session. $ex")
        } catch (ex: Exception) {
            this.filterLogger.warn("Error processing authentication token: ${ex.message}")
        }
    }

    private fun resolveSessionKeyAndAuthorities(
        sessionTokenName: String
    ): Pair<ByteArray, List<GrantedAuthority>> {
        return when {
            sessionTokenName.startsWith("as-") -> {
                val authorSession = authorSessionService.findByName(sessionTokenName)
                authorSession.decodePublicKey() to emptyList()
            }
            sessionTokenName.startsWith("u-") -> {
                val lockUserSession = lockUserSessionService.findByName(sessionTokenName)
                lockUserSession.decodePublicKey() to emptyList()
            }
            sessionTokenName.startsWith("ad-") -> {
                val adminSession = adminSessionService.findByName(sessionTokenName)
                adminSession.decodePublicKey() to listOf(SimpleGrantedAuthority("ADMIN"))
            }
            sessionTokenName.startsWith("b-") && botService != null -> {
                val bot = botService.getByName(sessionTokenName)
                check(bot.state == BotState.ACTIVE) { "Bot is not active: $sessionTokenName" }
                bot.decodePublicKey() to listOf(SimpleGrantedAuthority("BOT"))
            }
            else ->
                throw IllegalArgumentException("Unknown session token prefix: $sessionTokenName")
        }
    }
}

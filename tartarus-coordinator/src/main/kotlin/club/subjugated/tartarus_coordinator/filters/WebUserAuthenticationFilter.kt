package club.subjugated.tartarus_coordinator.filters

import club.subjugated.tartarus_coordinator.services.AdminSessionService
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import jakarta.persistence.PersistenceException
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
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
    private val adminSessionService: AdminSessionService) :
    OncePerRequestFilter() {

    private val filterLogger: Logger = LoggerFactory.getLogger(WebUserAuthenticationFilter::class.java)

    @Throws(IOException::class, ServletException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)

            val signedJWT = SignedJWT.parse(token)
            val claimsSet = signedJWT.jwtClaimsSet

            val sessionTokenName = claimsSet.subject
            try {
                val (publicKey, authorities: List<GrantedAuthority>) = when {
                    sessionTokenName.startsWith("as-") -> {
                        val authorSession = authorSessionService.findByName(sessionTokenName)
                        authorSession.decodePublicKey() to emptyList<GrantedAuthority>()
                    }
                    sessionTokenName.startsWith("u-") -> {
                        val lockUserSession = lockUserSessionService.findByName(sessionTokenName)
                        lockUserSession.decodePublicKey() to emptyList<GrantedAuthority>()
                    }
                    sessionTokenName.startsWith("ad-") -> {
                        val adminSession = adminSessionService.findByName(sessionTokenName)
                        adminSession.decodePublicKey() to listOf<GrantedAuthority>(SimpleGrantedAuthority("ADMIN"))
                    }
                    else -> throw IllegalArgumentException("Unknown session token prefix: $sessionTokenName")
                }

                val sessionPublicECKey =
                    getECPublicKeyFromCompressedKeyByteArray(
                        publicKey
                    )
                val verifier = ECDSAVerifier(sessionPublicECKey)
                if (signedJWT.verify(verifier)) {
                    val userDetails: UserDetails =
                        User.withUsername(claimsSet.subject)
                            .password("")
                            .authorities(authorities)
                            .build()

                    val authentication =
                        UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.authorities,
                        )
                    authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                    SecurityContextHolder.getContext().authentication = authentication
                }
            } catch (ex: PersistenceException) {
                this.filterLogger.warn("We got a token but no backing session. $ex")
                filterChain.doFilter(request, response)
            }
        }

        filterChain.doFilter(request, response)
    }
}

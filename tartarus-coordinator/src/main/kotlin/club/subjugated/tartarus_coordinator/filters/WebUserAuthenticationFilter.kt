package club.subjugated.tartarus_coordinator.filters

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
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

class WebUserAuthenticationFilter(
    private val authorSessionService: AuthorSessionService,
    private val lockUserSessionService: LockUserSessionService) :
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
                val publicKey = if(sessionTokenName.startsWith("as-")) {
                    val authorSession = this.authorSessionService.findByName(sessionTokenName)
                    Base64.getDecoder().decode(authorSession.publicKey)
                } else {
                    val lockUserSession = this.lockUserSessionService.findByName(sessionTokenName)
                    Base64.getDecoder().decode(lockUserSession.publicKey)
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
                            .authorities(emptyList())
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

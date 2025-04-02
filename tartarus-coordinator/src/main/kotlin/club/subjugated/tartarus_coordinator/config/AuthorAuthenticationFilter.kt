import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.util.getECPublicKeyFromCompressedKeyByteArray
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.util.*

class AuthorAuthenticationFilter(
    private val authorSessionService: AuthorSessionService
) : OncePerRequestFilter() {

    @Throws(IOException::class, ServletException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)

            val signedJWT = SignedJWT.parse(token)
            val claimsSet = signedJWT.jwtClaimsSet

            val authorSessionTokenName = claimsSet.subject
            try {
                val authorSession = this.authorSessionService.findByName(authorSessionTokenName)

                val authorPublicECKey = getECPublicKeyFromCompressedKeyByteArray(Base64.getDecoder().decode(authorSession.publicKey))
                val verifier = ECDSAVerifier(authorPublicECKey)
                if(signedJWT.verify(verifier)) {
                    val userDetails: UserDetails = User.withUsername(claimsSet.subject)
                        .password("")
                        .authorities(emptyList())
                        .build()

                    val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
                    authentication.details = WebAuthenticationDetailsSource().buildDetails(request)

                    SecurityContextHolder.getContext().authentication = authentication
                }
            } catch (ex : Exception) {
                filterChain.doFilter(request, response)
            }
        }

        filterChain.doFilter(request, response)
    }
}

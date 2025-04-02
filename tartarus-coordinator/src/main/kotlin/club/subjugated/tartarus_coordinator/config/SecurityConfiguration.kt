package club.subjugated.tartarus_coordinator.config

import club.subjugated.tartarus_coordinator.filters.WebUserAuthenticationFilter
import club.subjugated.tartarus_coordinator.models.AdminSession
import club.subjugated.tartarus_coordinator.services.AdminSessionService
import club.subjugated.tartarus_coordinator.services.AuthorSessionService
import club.subjugated.tartarus_coordinator.services.LockUserSessionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfiguration() {
    @Autowired lateinit var lockUserSessionService: LockUserSessionService
    @Autowired lateinit var authorSessionService: AuthorSessionService
    @Autowired lateinit var adminSessionService: AdminSessionService

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        val webUserAuthenticationFilter = WebUserAuthenticationFilter(authorSessionService, lockUserSessionService, adminSessionService)

        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .authorizeHttpRequests { auth -> auth.anyRequest().permitAll() }
            .httpBasic {}
            .addFilterAfter(webUserAuthenticationFilter, BasicAuthenticationFilter::class.java)
        //            .httpBasic{
        //
        //            }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource() =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration(
                "/**",
                CorsConfiguration().apply {
                    allowedOrigins =
                        listOf(
                            "https://192.168.1.180:4200",
                            "http://localhost:4200",
                            "https://tartarus.subjugated.club",
                        )
                    allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    allowedHeaders = listOf("authorization", "content-type", "x-auth-token")
                    exposedHeaders = listOf("x-auth-token")
                    allowCredentials = true
                    maxAge = 3600L
                },
            )
        }
}

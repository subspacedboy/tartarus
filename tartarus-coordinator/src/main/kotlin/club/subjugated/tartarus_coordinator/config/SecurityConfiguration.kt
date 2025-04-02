package club.subjugated.tartarus_coordinator.config

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
class SecurityConfiguration {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors{
                it.configurationSource(corsConfigurationSource())
            }
            .csrf {
                it.disable()
            }
//            .authorizeHttpRequests(Customizer {
//                it.requestMatchers("/public/**").permitAll()
//                it.requestMatchers("/bread/**").permitAll()
//                it.anyRequest().authenticated()
//            })
//            .httpBasic{
//
//            }.addFilterAfter(JwtTokenAuthenticationFilter(usersService, keyFile), BasicAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun corsConfigurationSource() = UrlBasedCorsConfigurationSource().apply {
        registerCorsConfiguration("/**", CorsConfiguration().apply {
            allowedOrigins = listOf("https://192.168.1.180:4200", "http://localhost:4200", "https://tartarus.subjugated.club")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("authorization", "content-type", "x-auth-token")
            exposedHeaders = listOf("x-auth-token")
            allowCredentials = true
            maxAge = 3600L
        })
    }
}
package ee.parandiplaan.config;

import ee.parandiplaan.common.security.JwtAuthenticationFilter;
import ee.parandiplaan.common.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableScheduling
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/register.html", "/login.html", "/dashboard.html", "/vault.html", "/usaldusisikud.html", "/reset-password.html", "/settings.html", "/onboarding.html", "/activity.html", "/privaatsuspoliitika.html", "/tingimused.html", "/jagatud-tresor.html", "/admin.html", "/assets/**", "/favicon.ico", "/favicon.svg", "/error", "/error/**").permitAll()
                .requestMatchers("/manifest.json", "/sw.js", "/offline.html", "/robots.txt", "/sitemap.xml").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/vault/categories").permitAll()
                .requestMatchers("/api/v1/trusted-contacts/accept-invite/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/handover-requests").permitAll()
                .requestMatchers("/api/v1/handover-requests/still-here/**").permitAll()
                .requestMatchers("/api/v1/shared-vault/info").permitAll()
                .requestMatchers("/api/v1/shared-vault/entries/**").permitAll()
                .requestMatchers("/api/v1/shared-vault/entries").permitAll()
                .requestMatchers("/api/v1/shared-vault/attachments/**").permitAll()
                .requestMatchers("/api/v1/webhooks/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

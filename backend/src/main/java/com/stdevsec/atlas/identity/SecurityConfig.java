package com.stdevsec.atlas.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
class SecurityConfig {

    /** Absolute session lifetime regardless of activity (idle timeout is spring.session.timeout). */
    static final Duration ABSOLUTE_SESSION_LIFETIME = Duration.ofHours(12);

    @Bean
    PasswordEncoder passwordEncoder() {
        // Argon2id: 16-byte salt, 32-byte hash, 1 thread, 64 MiB memory, 3 iterations (OWASP baseline).
        return new Argon2PasswordEncoder(16, 32, 1, 1 << 16, 3);
    }

    @Bean
    CookieSerializer cookieSerializer(@Value("${atlas.secure-cookies}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("ATLAS_SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Strict");
        serializer.setUseSecureCookie(secure);
        serializer.setCookiePath("/");
        return serializer;
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http, @Value("${atlas.api-docs-enabled}") boolean apiDocs)
            throws Exception {
        http
            .csrf(csrf -> csrf.spa())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> {
                auth.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll();
                auth.requestMatchers("/api/v1/setup/**", "/api/v1/auth/login", "/api/v1/auth/csrf",
                        "/actuator/health", "/actuator/health/**").permitAll();
                if (apiDocs) {
                    auth.requestMatchers("/api/v1/docs/**", "/api/v1/openapi.json/**").hasRole("ADMIN");
                }
                auth.requestMatchers("/api/**").authenticated();
                auth.anyRequest().denyAll();
            })
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                    problem(res, HttpStatus.UNAUTHORIZED, "Authentication required"))
                // Written directly: a sendError(403) would be re-dispatched to /error and surface as 401.
                .accessDeniedHandler((req, res, ex) ->
                    problem(res, HttpStatus.FORBIDDEN, "Forbidden")))
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .frameOptions(f -> f.deny()))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            .addFilterAfter(new AbsoluteSessionTimeoutFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }

    private static void problem(HttpServletResponse res, HttpStatus status, String detail) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\"}");
    }

    /** Ends sessions older than {@link #ABSOLUTE_SESSION_LIFETIME} even if they stayed active. */
    static class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            HttpSession session = request.getSession(false);
            if (session != null && System.currentTimeMillis() - session.getCreationTime()
                    > ABSOLUTE_SESSION_LIFETIME.toMillis()) {
                session.invalidate();
                problem(response, HttpStatus.UNAUTHORIZED, "Session expired");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}

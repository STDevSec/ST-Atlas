package com.stdevsec.atlas.identity;

import com.stdevsec.atlas.identity.internal.AuthService;
import com.stdevsec.atlas.identity.internal.AuthService.LoginResult;
import com.stdevsec.atlas.identity.internal.LoginRateLimiter;
import com.stdevsec.atlas.shared.BadRequestException;
import com.stdevsec.atlas.shared.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    static final String PENDING_MFA_SECRET = "atlas.pendingMfaSecret";

    private final AuthService auth;
    private final LoginRateLimiter rateLimiter;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    AuthController(AuthService auth, LoginRateLimiter rateLimiter) {
        this.auth = auth;
        this.rateLimiter = rateLimiter;
    }

    record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password,
            @Size(max = 10) String totp, @Size(max = 20) String recoveryCode) {}

    /** Ensures the XSRF-TOKEN cookie exists before the SPA's first POST. */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            throw new TooManyRequestsException("Too many login attempts, try again later");
        }
        LoginResult result = auth.login(body.username().trim(), body.password(), body.totp(), body.recoveryCode());
        return switch (result) {
            case LoginResult.Success s -> {
                establishSession(s.principal(), request, response);
                yield ResponseEntity.ok(Map.of("username", s.principal().username()));
            }
            case LoginResult.MfaRequired m -> {
                ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                        "A verification code is required");
                pd.setProperty("mfaRequired", true);
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
            }
            case LoginResult.Failure f -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        };
    }

    private void establishSession(AtlasPrincipal principal, HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate(); // session fixation protection: always a fresh session
        }
        request.getSession(true);
        List<SimpleGrantedAuthority> authorities = principal.admin()
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    record PasswordChange(@NotBlank String currentPassword, @NotBlank @Size(max = 128) String newPassword) {}

    @PutMapping("/password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChange body) {
        auth.changePassword(CurrentUser.id(), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    record Preferences(@Pattern(regexp = "en|es") String locale, @Size(max = 64) String timeZone) {}

    @PutMapping("/preferences")
    ResponseEntity<Void> preferences(@Valid @RequestBody Preferences body) {
        if (body.timeZone() != null) {
            try {
                java.time.ZoneId.of(body.timeZone());
            } catch (java.time.DateTimeException e) {
                throw new BadRequestException("Unknown time zone");
            }
        }
        auth.updatePreferences(CurrentUser.id(), body.locale(), body.timeZone());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/setup")
    AuthService.MfaEnrolment mfaSetup(HttpSession session) {
        AuthService.MfaEnrolment enrolment = auth.beginMfa(CurrentUser.id());
        session.setAttribute(PENDING_MFA_SECRET, enrolment.secret());
        return enrolment;
    }

    record MfaCode(@NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    @PostMapping("/mfa/enable")
    Map<String, List<String>> mfaEnable(@Valid @RequestBody MfaCode body, HttpSession session) {
        String pending = (String) session.getAttribute(PENDING_MFA_SECRET);
        List<String> codes = auth.enableMfa(CurrentUser.id(), pending, body.code());
        session.removeAttribute(PENDING_MFA_SECRET);
        return Map.of("recoveryCodes", codes);
    }

    record MfaDisable(@NotBlank String password) {}

    @PostMapping("/mfa/disable")
    ResponseEntity<Void> mfaDisable(@Valid @RequestBody MfaDisable body) {
        auth.disableMfa(CurrentUser.id(), body.password());
        return ResponseEntity.noContent().build();
    }
}

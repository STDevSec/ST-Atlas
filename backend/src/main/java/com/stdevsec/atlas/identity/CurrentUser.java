package com.stdevsec.atlas.identity;

import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the authenticated principal of the current request. */
public final class CurrentUser {

    private CurrentUser() {}

    public static AtlasPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AtlasPrincipal p) {
            return p;
        }
        throw new AuthenticationCredentialsNotFoundException("Not authenticated");
    }

    public static UUID id() {
        return get().id();
    }
}

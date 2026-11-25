package com.stdevsec.atlas.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Instance administrators create accounts; there is no public self-registration. */
@RestController
class UserAdminController {

    private final UserDirectory users;

    UserAdminController(UserDirectory users) {
        this.users = users;
    }

    record CreateUser(@NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
            @NotBlank @Size(max = 120) String displayName, @NotBlank @Size(max = 128) String password,
            @Pattern(regexp = "en|es") String locale) {}

    @PostMapping("/api/v1/admin/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    UserSummary create(@Valid @RequestBody CreateUser body) {
        return users.createUser(body.username().trim(), body.displayName().trim(), body.password(),
                body.locale() == null ? "en" : body.locale(), "UTC", false);
    }
}

package com.stdevsec.atlas.system;

import com.stdevsec.atlas.shared.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/setup")
class SetupController {

    private final SetupService setup;
    private final InstanceSettings settings;

    SetupController(SetupService setup, InstanceSettings settings) {
        this.setup = setup;
        this.settings = settings;
    }

    /** Public: only says whether setup is pending, plus the organisation name for the login page. */
    @GetMapping("/status")
    Map<String, Object> status() {
        return Map.of("pending", setup.isPending(),
                "organizationName", settings.get(InstanceSettings.ORGANIZATION_NAME).orElse(""));
    }

    record Request(
            @NotBlank @Size(max = 64) String token,
            @NotBlank @Size(max = 200) String organizationName,
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Pattern(regexp = "en|es") String locale,
            @NotBlank @Size(max = 64) String timeZone,
            @NotBlank @Size(max = 120) String workspaceName) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> complete(@Valid @RequestBody Request r) {
        if (!setup.isPending()) {
            throw new NotFoundException("Setup");
        }
        try {
            java.time.ZoneId.of(r.timeZone());
        } catch (java.time.DateTimeException e) {
            throw new com.stdevsec.atlas.shared.BadRequestException("Unknown time zone");
        }
        var admin = setup.complete(new SetupService.SetupRequest(r.token(), r.organizationName(), r.username(),
                r.displayName(), r.password(), r.locale(), r.timeZone(), r.workspaceName()));
        return Map.of("username", admin.username());
    }
}

package com.stdevsec.atlas.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClientView(UUID id, String name, String legalName, String website, String notes,
        UUID logoAttachmentId, List<ContactView> contacts, Instant updatedAt) {

    public record ContactView(UUID id, String name, String email, String phone, String kind) {}
}

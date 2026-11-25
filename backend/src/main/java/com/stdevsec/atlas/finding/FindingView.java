package com.stdevsec.atlas.finding;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FindingView(UUID id, UUID assessmentId, String code, String title, String description,
        String technicalDetails, Severity severity, String cvss31Vector, BigDecimal cvss31Score, String cvss40Vector,
        BigDecimal cvss40Score, List<String> cwe, List<String> cve, String impact, String recommendation,
        List<String> references, List<String> tags, RemediationStatus status, LocalDate discoveredOn,
        UUID analystId, Instant createdAt, Instant updatedAt) {}

package com.stdevsec.atlas.finding;

public enum Severity {
    CRITICAL, HIGH, MEDIUM, LOW, INFORMATIONAL, NOT_APPLICABLE;

    /** CVSS v3.x qualitative rating scale. */
    public static Severity fromCvss(double score) {
        if (score >= 9.0) return CRITICAL;
        if (score >= 7.0) return HIGH;
        if (score >= 4.0) return MEDIUM;
        if (score >= 0.1) return LOW;
        return INFORMATIONAL;
    }
}

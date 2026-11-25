package com.stdevsec.atlas.report.internal;

import com.stdevsec.atlas.report.TemplateConfig;
import java.util.Map;

/** Fixed report labels in English and Spanish (the PDF follows the author's language). */
public record Labels(Map<String, String> text) {

    public static Labels forLocale(String locale) {
        return "es".equals(locale) ? ES : EN;
    }

    public String get(String key) {
        return text.getOrDefault(key, key);
    }

    public String severity(String s) {
        return get("sev." + s);
    }

    public String status(String s) {
        return get("status." + s);
    }

    public String classification(TemplateConfig.Classification c) {
        return get("class." + c.name());
    }

    static final Labels EN = new Labels(Map.ofEntries(Map.entry("assessment", "Assessment"),
            Map.entry("client", "Client"), Map.entry("period", "Period"), Map.entry("author", "Author"),
            Map.entry("version", "Version"), Map.entry("date", "Date"), Map.entry("status", "Status"),
            Map.entry("title", "Title"), Map.entry("severity", "Severity"), Map.entry("description", "Description"),
            Map.entry("technicalDetails", "Technical details"), Map.entry("impact", "Impact"),
            Map.entry("recommendation", "Recommendation"), Map.entry("references", "References"),
            Map.entry("findingMissing", "[Finding no longer exists]"),
            Map.entry("sev.CRITICAL", "Critical"), Map.entry("sev.HIGH", "High"), Map.entry("sev.MEDIUM", "Medium"),
            Map.entry("sev.LOW", "Low"), Map.entry("sev.INFORMATIONAL", "Informational"),
            Map.entry("sev.NOT_APPLICABLE", "N/A"), Map.entry("status.OPEN", "Open"),
            Map.entry("status.IN_PROGRESS", "In progress"), Map.entry("status.MITIGATED", "Mitigated"),
            Map.entry("status.FIXED", "Fixed"), Map.entry("status.RISK_ACCEPTED", "Risk accepted"),
            Map.entry("status.FALSE_POSITIVE", "False positive"), Map.entry("status.PENDING_RETEST", "Pending retest"),
            Map.entry("class.PUBLIC", "Public"), Map.entry("class.INTERNAL", "Internal use"),
            Map.entry("class.CONFIDENTIAL", "Confidential"), Map.entry("class.RESTRICTED", "Restricted")));

    static final Labels ES = new Labels(Map.ofEntries(Map.entry("assessment", "Evaluación"),
            Map.entry("client", "Cliente"), Map.entry("period", "Periodo"), Map.entry("author", "Autor"),
            Map.entry("version", "Versión"), Map.entry("date", "Fecha"), Map.entry("status", "Estado"),
            Map.entry("title", "Título"), Map.entry("severity", "Severidad"), Map.entry("description", "Descripción"),
            Map.entry("technicalDetails", "Detalles técnicos"), Map.entry("impact", "Impacto"),
            Map.entry("recommendation", "Recomendación"), Map.entry("references", "Referencias"),
            Map.entry("findingMissing", "[El hallazgo ya no existe]"),
            Map.entry("sev.CRITICAL", "Crítica"), Map.entry("sev.HIGH", "Alta"), Map.entry("sev.MEDIUM", "Media"),
            Map.entry("sev.LOW", "Baja"), Map.entry("sev.INFORMATIONAL", "Informativa"),
            Map.entry("sev.NOT_APPLICABLE", "N/A"), Map.entry("status.OPEN", "Abierto"),
            Map.entry("status.IN_PROGRESS", "En progreso"), Map.entry("status.MITIGATED", "Mitigado"),
            Map.entry("status.FIXED", "Corregido"), Map.entry("status.RISK_ACCEPTED", "Riesgo aceptado"),
            Map.entry("status.FALSE_POSITIVE", "Falso positivo"), Map.entry("status.PENDING_RETEST", "Pendiente de retest"),
            Map.entry("class.PUBLIC", "Público"), Map.entry("class.INTERNAL", "Uso interno"),
            Map.entry("class.CONFIDENTIAL", "Confidencial"), Map.entry("class.RESTRICTED", "Restringido")));
}

package com.stdevsec.atlas.report.internal;

import com.stdevsec.atlas.assessment.AssessmentRef;
import com.stdevsec.atlas.finding.FindingView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Entry points into the module's internals for {@code ReportService}. */
public final class ReportSupport {

    private ReportSupport() {}

    public static JsonNode initialDocument(AssessmentRef a, boolean spanish) {
        return InitialDocuments.forAssessment(a, spanish);
    }

    public static JsonNode snapshot(JsonNode doc, Map<UUID, FindingView> byId, List<FindingView> all) {
        return Snapshotter.snapshot(doc, byId, all);
    }

    public static Integer countPages(byte[] pdf) {
        return GotenbergClient.countPages(pdf);
    }

    /** Visible text of a document, for the search index. */
    public static String plainText(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        collect(node, sb);
        return sb.toString();
    }

    private static void collect(JsonNode node, StringBuilder sb) {
        JsonNode text = node.get("text");
        if (text != null && text.isString()) {
            sb.append(text.stringValue()).append(' ');
        }
        JsonNode caption = node.path("attrs").get("caption");
        if (caption != null && caption.isString()) {
            sb.append(caption.stringValue()).append(' ');
        }
        for (JsonNode child : node.path("content")) {
            collect(child, sb);
        }
        if (node.has("content") && !"text".equals(DocumentValidator.type(node))) {
            sb.append('\n');
        }
    }

    public static String fileName(String code, String title, int version) {
        String base = (code + " " + title).replaceAll("[^\\p{L}\\p{N} ._-]", "").trim().replace(' ', '_');
        return base.substring(0, Math.min(base.length(), 120)) + "_v" + version + ".pdf";
    }

    public static String typeLabel(String typeCode, String locale) {
        String[] labels = TYPE_LABELS.get(typeCode);
        if (labels == null) {
            return typeCode;
        }
        return "es".equals(locale) ? labels[1] : labels[0];
    }

    private static final Map<String, String[]> TYPE_LABELS = Map.ofEntries(
            Map.entry("WEB_PENTEST", new String[] {"Web application penetration test", "Prueba de penetración de aplicación web"}),
            Map.entry("API_PENTEST", new String[] {"API penetration test", "Prueba de penetración de API"}),
            Map.entry("NETWORK_PENTEST", new String[] {"Network penetration test", "Prueba de penetración de red"}),
            Map.entry("ACTIVE_DIRECTORY", new String[] {"Active Directory assessment", "Evaluación de Active Directory"}),
            Map.entry("MOBILE_SECURITY", new String[] {"Mobile application security assessment", "Evaluación de seguridad de aplicación móvil"}),
            Map.entry("WIRELESS", new String[] {"Wireless security assessment", "Evaluación de seguridad inalámbrica"}),
            Map.entry("VULNERABILITY_ASSESSMENT", new String[] {"Vulnerability assessment", "Evaluación de vulnerabilidades"}),
            Map.entry("DIGITAL_FORENSICS", new String[] {"Digital forensics investigation", "Investigación forense digital"}),
            Map.entry("INCIDENT_RESPONSE", new String[] {"Incident response", "Respuesta a incidentes"}),
            Map.entry("SOC_INVESTIGATION", new String[] {"SOC investigation", "Investigación SOC"}),
            Map.entry("NOC_ASSESSMENT", new String[] {"NOC assessment", "Evaluación NOC"}),
            Map.entry("INFRASTRUCTURE_SECURITY", new String[] {"Infrastructure security assessment", "Evaluación de seguridad de infraestructura"}),
            Map.entry("SYSTEM_HARDENING", new String[] {"System hardening review", "Revisión de hardening de sistemas"}),
            Map.entry("CLOUD_SECURITY", new String[] {"Cloud security assessment", "Evaluación de seguridad en la nube"}),
            Map.entry("SECURE_CODE_REVIEW", new String[] {"Secure code review", "Revisión de código seguro"}),
            Map.entry("COMPLIANCE_AUDIT", new String[] {"Compliance audit", "Auditoría de cumplimiento"}),
            Map.entry("CTF_WRITEUP", new String[] {"CTF write-up", "Write-up de CTF"}),
            Map.entry("CYBERSECURITY_LAB", new String[] {"Cybersecurity lab", "Laboratorio de ciberseguridad"}),
            Map.entry("TECHNICAL_RESEARCH", new String[] {"Technical research", "Investigación técnica"}),
            Map.entry("CUSTOM", new String[] {"Custom assessment", "Evaluación personalizada"}));
}

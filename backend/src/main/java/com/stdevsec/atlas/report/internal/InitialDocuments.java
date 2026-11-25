package com.stdevsec.atlas.report.internal;

import com.stdevsec.atlas.assessment.AssessmentRef;
import java.util.List;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Starting structure of a new report, chosen by assessment family. Forensic, IR, SOC/NOC,
 * hardening, CTF and research reports do not get vulnerability-centric sections (spec §8).
 */
final class InitialDocuments {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private InitialDocuments() {}

    static ObjectNode forAssessment(AssessmentRef a, boolean spanish) {
        ObjectNode doc = F.objectNode().put("type", "doc");
        ArrayNode content = doc.putArray("content");
        for (String[] section : sections(a.typeCode(), spanish)) {
            heading(content, section[0]);
            switch (section[1]) {
                case "objectives" -> paragraph(content, a.objectives());
                case "scope" -> paragraph(content, a.scope());
                case "methodology" -> paragraph(content, a.methodology());
                case "findings" -> content.addObject().put("type", "findingsTable");
                default -> paragraph(content, null);
            }
        }
        return doc;
    }

    private static List<String[]> sections(String type, boolean es) {
        return switch (type) {
            case "DIGITAL_FORENSICS" -> List.of(s(es ? "Resumen del caso" : "Case summary", ""),
                    s(es ? "Alcance y autorización" : "Scope and authorization", "scope"),
                    s(es ? "Evidencia y cadena de custodia" : "Evidence and chain of custody", ""),
                    s(es ? "Metodología de adquisición y análisis" : "Acquisition and analysis methodology", "methodology"),
                    s(es ? "Línea de tiempo" : "Timeline", ""), s(es ? "Análisis" : "Analysis", ""),
                    s(es ? "Conclusiones" : "Conclusions", ""));
            case "INCIDENT_RESPONSE", "SOC_INVESTIGATION" -> List.of(s(es ? "Resumen ejecutivo" : "Executive summary", ""),
                    s(es ? "Detección" : "Detection", ""), s(es ? "Línea de tiempo" : "Timeline", ""),
                    s(es ? "Contención, erradicación y recuperación" : "Containment, eradication and recovery", ""),
                    s(es ? "Indicadores de compromiso" : "Indicators of compromise", ""),
                    s(es ? "Lecciones aprendidas" : "Lessons learned", ""));
            case "CTF_WRITEUP", "CYBERSECURITY_LAB" -> List.of(s(es ? "Reto" : "Challenge", "objectives"),
                    s(es ? "Reconocimiento" : "Reconnaissance", ""), s(es ? "Explotación" : "Exploitation", ""),
                    s(es ? "Solución" : "Solution", ""), s(es ? "Lecciones aprendidas" : "Lessons learned", ""));
            case "NOC_ASSESSMENT", "SYSTEM_HARDENING", "COMPLIANCE_AUDIT", "TECHNICAL_RESEARCH", "CUSTOM" -> List.of(
                    s(es ? "Resumen ejecutivo" : "Executive summary", ""), s(es ? "Objetivos" : "Objectives", "objectives"),
                    s(es ? "Alcance" : "Scope", "scope"), s(es ? "Metodología" : "Methodology", "methodology"),
                    s(es ? "Resultados" : "Results", ""), s(es ? "Recomendaciones" : "Recommendations", ""),
                    s(es ? "Conclusiones" : "Conclusions", ""));
            default -> List.of(s(es ? "Resumen ejecutivo" : "Executive summary", ""),
                    s(es ? "Objetivos" : "Objectives", "objectives"), s(es ? "Alcance" : "Scope", "scope"),
                    s(es ? "Metodología" : "Methodology", "methodology"),
                    s(es ? "Resumen de hallazgos" : "Findings summary", "findings"),
                    s(es ? "Hallazgos técnicos" : "Technical findings", ""),
                    s(es ? "Conclusiones" : "Conclusions", ""));
        };
    }

    private static String[] s(String title, String source) {
        return new String[] {title, source};
    }

    private static void heading(ArrayNode content, String text) {
        ObjectNode h = content.addObject().put("type", "heading");
        h.putObject("attrs").put("level", 1);
        h.putArray("content").addObject().put("type", "text").put("text", text);
    }

    private static void paragraph(ArrayNode content, String text) {
        ObjectNode p = content.addObject().put("type", "paragraph");
        if (text != null && !text.isBlank()) {
            p.putArray("content").addObject().put("type", "text").put("text", text);
        }
    }
}

package com.stdevsec.atlas.report.internal;

import com.stdevsec.atlas.finding.FindingView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Freezes live references into a self-contained document: {@code findingRef} becomes
 * {@code findingSnapshot} with the finding's content, and {@code findingsTable} gets its rows.
 * A report version stores this snapshot, so it never changes when findings change later (spec §12).
 */
final class Snapshotter {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private Snapshotter() {}

    static JsonNode snapshot(JsonNode doc, Map<UUID, FindingView> findings, List<FindingView> allFindings) {
        return copy(doc, findings, allFindings);
    }

    private static JsonNode copy(JsonNode node, Map<UUID, FindingView> findings, List<FindingView> all) {
        String type = DocumentValidator.type(node);
        if ("findingRef".equals(type)) {
            UUID id = UUID.fromString(node.path("attrs").path("findingId").asString(""));
            FindingView f = findings.get(id);
            ObjectNode snap = F.objectNode().put("type", "findingSnapshot");
            ObjectNode attrs = snap.putObject("attrs");
            if (f == null) {
                attrs.put("missing", true);
            } else {
                attrs.set("finding", toJson(f));
            }
            return snap;
        }
        ObjectNode out = (ObjectNode) node.deepCopy();
        if ("findingsTable".equals(type)) {
            ObjectNode attrs = out.get("attrs") instanceof ObjectNode existing ? existing : out.putObject("attrs");
            ArrayNode rows = attrs.putArray("rows");
            all.forEach(f -> rows.add(toJson(f)));
        }
        if (node.has("content")) {
            ArrayNode content = out.putArray("content");
            for (JsonNode child : node.path("content")) {
                content.add(copy(child, findings, all));
            }
        }
        return out;
    }

    static ObjectNode toJson(FindingView f) {
        ObjectNode o = F.objectNode();
        o.put("id", f.id().toString());
        o.put("code", f.code());
        o.put("title", f.title());
        o.put("severity", f.severity().name());
        o.put("status", f.status().name());
        putIfNotNull(o, "description", f.description());
        putIfNotNull(o, "technicalDetails", f.technicalDetails());
        putIfNotNull(o, "impact", f.impact());
        putIfNotNull(o, "recommendation", f.recommendation());
        putIfNotNull(o, "cvss31Vector", f.cvss31Vector());
        putIfNotNull(o, "cvss31Score", f.cvss31Score() == null ? null : f.cvss31Score().toPlainString());
        putIfNotNull(o, "cvss40Vector", f.cvss40Vector());
        putIfNotNull(o, "cvss40Score", f.cvss40Score() == null ? null : f.cvss40Score().toPlainString());
        ArrayNode cwe = o.putArray("cwe");
        f.cwe().forEach(cwe::add);
        ArrayNode cve = o.putArray("cve");
        f.cve().forEach(cve::add);
        ArrayNode refs = o.putArray("references");
        f.references().forEach(refs::add);
        return o;
    }

    private static void putIfNotNull(ObjectNode o, String k, String v) {
        if (v != null) {
            o.put(k, v);
        }
    }
}

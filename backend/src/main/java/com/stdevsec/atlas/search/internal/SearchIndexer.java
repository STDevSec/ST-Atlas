package com.stdevsec.atlas.search.internal;

import com.stdevsec.atlas.shared.Ids;
import com.stdevsec.atlas.shared.SearchDocument;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Maintains {@code search_entry} synchronously inside the publishing transaction, so the
 * index can never show a record whose change was rolled back, nor miss a committed one.
 */
@Component
class SearchIndexer {

    private static final int MAX_BODY = 200_000;

    private final JdbcClient jdbc;

    SearchIndexer(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void on(SearchDocument.Indexed event) {
        SearchDocument d = event.document();
        String body = d.body() == null ? "" : d.body();
        if (body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY);
        }
        jdbc.sql("""
                insert into search_entry (id, workspace_id, entity_type, entity_id, assessment_id, title, body, tags,
                                          severity, status, type_code, client_id, updated_at)
                values (:id, :ws, :type, :entity, :assessment, :title, :body, :tags, :severity, :status, :typeCode,
                        :client, now())
                on conflict (entity_type, entity_id) do update set
                    title = excluded.title, body = excluded.body, tags = excluded.tags,
                    assessment_id = excluded.assessment_id, severity = excluded.severity, status = excluded.status,
                    type_code = excluded.type_code, client_id = excluded.client_id, updated_at = now()""")
                .param("id", Ids.newId()).param("ws", d.workspaceId()).param("type", d.entityType())
                .param("entity", d.entityId()).param("assessment", d.assessmentId()).param("title", d.title())
                .param("body", body).param("tags", d.tags() == null ? new String[0] : d.tags().toArray(String[]::new))
                .param("severity", d.severity()).param("status", d.status()).param("typeCode", d.typeCode())
                .param("client", d.clientId())
                .update();
    }

    @EventListener
    void on(SearchDocument.Removed event) {
        jdbc.sql("delete from search_entry where entity_type = :type and entity_id = :id")
                .param("type", event.entityType()).param("id", event.entityId()).update();
    }
}

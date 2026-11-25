package com.stdevsec.atlas.audit.internal;

import com.stdevsec.atlas.shared.AuditRecord;
import com.stdevsec.atlas.shared.Ids;
import com.stdevsec.atlas.shared.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persists audit records synchronously, in the transaction of the change itself:
 * if the change rolls back, so does its audit entry; if auditing fails, the change fails.
 */
@Component
public class AuditStore {

    public record AuditView(UUID id, Instant at, UUID actorId, String action, String entityType,
            UUID entityId, String ip) {}

    private final JdbcClient jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    AuditStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    public void on(AuditRecord record) {
        jdbc.sql("""
                insert into audit_event (id, workspace_id, actor_id, action, entity_type, entity_id, ip, details)
                values (:id, :ws, :actor, :action, :type, :entity, :ip, cast(:details as jsonb))""")
                .param("id", Ids.newId())
                .param("ws", record.workspaceId())
                .param("actor", record.actorId())
                .param("action", record.action())
                .param("type", record.entityType())
                .param("entity", record.entityId())
                .param("ip", clientIp())
                .param("details", json.writeValueAsString(record.details()))
                .update();
    }

    public PageResponse<AuditView> page(UUID workspaceId, int page, int size) {
        List<AuditView> items = jdbc.sql("""
                select id, at, actor_id, action, entity_type, entity_id, ip from audit_event
                where workspace_id = :ws order by at desc limit :limit offset :offset""")
                .param("ws", workspaceId).param("limit", size).param("offset", page * size)
                .query((rs, n) -> new AuditView(rs.getObject("id", UUID.class),
                        rs.getObject("at", Timestamp.class).toInstant(), rs.getObject("actor_id", UUID.class),
                        rs.getString("action"), rs.getString("entity_type"), rs.getObject("entity_id", UUID.class),
                        rs.getString("ip")))
                .list();
        long total = jdbc.sql("select count(*) from audit_event where workspace_id = :ws").param("ws", workspaceId)
                .query(Long.class).single();
        return new PageResponse<>(items, page, size, total);
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}

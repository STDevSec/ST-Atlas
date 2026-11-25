package com.stdevsec.atlas.dashboard;

import com.stdevsec.atlas.workspace.WorkspaceAccess;
import com.stdevsec.atlas.workspace.WorkspaceRole.Permission;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard figures straight from SQL (spec §9: never hard-coded in the frontend).
 * Every query is filtered by the workspace the caller was authorized for.
 */
@RestController
class DashboardController {

    record Deadline(UUID id, String code, String title, String status, LocalDate dueDate) {}

    record Activity(String action, String entityType, UUID entityId, Instant at) {}

    record RecentReport(UUID id, String title, UUID assessmentId, Instant updatedAt) {}

    record Hours(UUID assessmentId, String code, String title, double hours) {}

    record Dashboard(Map<String, Long> assessmentsByStatus, Map<String, Long> assessmentsByType,
            Map<String, Long> openFindingsBySeverity, Map<String, Long> findingsByStatus, long activeAssessments,
            long completedAssessments, long underReview, long openFindings, double totalHours,
            List<Hours> hoursByAssessment, List<Deadline> upcomingDeadlines, List<Activity> recentActivity,
            List<RecentReport> recentReports, LocalDate from, LocalDate to) {}

    private final JdbcClient jdbc;
    private final WorkspaceAccess access;

    DashboardController(JdbcClient jdbc, WorkspaceAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/dashboard")
    @Transactional(readOnly = true)
    Dashboard dashboard(@PathVariable UUID workspaceId, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        access.require(workspaceId, Permission.READ);
        LocalDate f = from == null ? LocalDate.now().minusDays(90) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        Timestamp fromTs = Timestamp.valueOf(f.atStartOfDay());
        Timestamp toTs = Timestamp.valueOf(t.plusDays(1).atStartOfDay());

        Map<String, Long> byStatus = counts("select status as k, count(*) as n from assessment where workspace_id = :ws group by status", workspaceId);
        Map<String, Long> byType = counts("select type_code as k, count(*) as n from assessment where workspace_id = :ws group by type_code", workspaceId);
        Map<String, Long> openBySeverity = counts("""
                select severity as k, count(*) as n from finding where workspace_id = :ws
                and status in ('OPEN','IN_PROGRESS','PENDING_RETEST') group by severity""", workspaceId);
        Map<String, Long> findingsByStatus = counts("select status as k, count(*) as n from finding where workspace_id = :ws group by status", workspaceId);

        long active = sum(byStatus, "PLANNED", "IN_PROGRESS", "RETESTING");
        long completed = sum(byStatus, "COMPLETED");
        long review = sum(byStatus, "TECHNICAL_REVIEW");
        long open = openBySeverity.values().stream().mapToLong(Long::longValue).sum();

        List<Hours> hours = jdbc.sql("""
                select a.id, a.code, a.title, sum(coalesce(t.duration_seconds, 0)) / 3600.0 as h
                from time_entry t join assessment a on a.id = t.assessment_id
                where t.workspace_id = :ws and t.started_at >= :from and t.started_at < :to
                group by a.id, a.code, a.title order by h desc limit 10""")
                .param("ws", workspaceId).param("from", fromTs).param("to", toTs)
                .query((rs, n) -> new Hours(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        Math.round(rs.getDouble(4) * 100) / 100.0)).list();
        double total = jdbc.sql("""
                select coalesce(sum(duration_seconds), 0) / 3600.0 from time_entry
                where workspace_id = :ws and started_at >= :from and started_at < :to""")
                .param("ws", workspaceId).param("from", fromTs).param("to", toTs).query(Double.class).single();

        List<Deadline> deadlines = jdbc.sql("""
                select id, code, title, status, due_date from assessment
                where workspace_id = :ws and due_date is not null
                  and status not in ('COMPLETED','ARCHIVED','DELIVERED') and due_date <= current_date + 30
                order by due_date limit 8""").param("ws", workspaceId)
                .query((rs, n) -> new Deadline(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getObject(5, Date.class).toLocalDate())).list();
        List<Activity> activity = jdbc.sql("""
                select action, entity_type, entity_id, at from audit_event where workspace_id = :ws
                order by at desc limit 10""").param("ws", workspaceId)
                .query((rs, n) -> new Activity(rs.getString(1), rs.getString(2), rs.getObject(3, UUID.class),
                        rs.getTimestamp(4).toInstant())).list();
        List<RecentReport> reports = jdbc.sql("""
                select id, title, assessment_id, updated_at from report where workspace_id = :ws
                order by updated_at desc limit 5""").param("ws", workspaceId)
                .query((rs, n) -> new RecentReport(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, UUID.class), rs.getTimestamp(4).toInstant())).list();

        return new Dashboard(byStatus, byType, openBySeverity, findingsByStatus, active, completed, review, open,
                Math.round(total * 100) / 100.0, hours, deadlines, activity, reports, f, t);
    }

    private Map<String, Long> counts(String sql, UUID workspaceId) {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.sql(sql).param("ws", workspaceId).query((rs, n) -> out.put(rs.getString("k"), rs.getLong("n"))).list();
        return out;
    }

    private static long sum(Map<String, Long> m, String... keys) {
        long s = 0;
        for (String k : keys) {
            s += m.getOrDefault(k, 0L);
        }
        return s;
    }
}

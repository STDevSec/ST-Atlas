package com.stdevsec.atlas.timetracking;

import com.stdevsec.atlas.assessment.AssessmentRef;
import com.stdevsec.atlas.assessment.AssessmentService;
import com.stdevsec.atlas.shared.AuditRecord;
import com.stdevsec.atlas.shared.BadRequestException;
import com.stdevsec.atlas.shared.ConflictException;
import com.stdevsec.atlas.shared.Ids;
import com.stdevsec.atlas.shared.NotFoundException;
import com.stdevsec.atlas.workspace.WorkspaceAccess;
import com.stdevsec.atlas.workspace.WorkspaceContext;
import com.stdevsec.atlas.workspace.WorkspaceRole.Permission;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeTrackingService {

    static final Duration MAX_ENTRY = Duration.ofHours(24);

    private final JdbcClient jdbc;
    private final AssessmentService assessments;
    private final WorkspaceAccess access;
    private final ApplicationEventPublisher events;

    TimeTrackingService(JdbcClient jdbc, AssessmentService assessments, WorkspaceAccess access,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.assessments = assessments;
        this.access = access;
        this.events = events;
    }

    public record ManualEntry(UUID assessmentId, Instant startedAt, Instant endedAt, boolean billable,
            String description) {}

    @Transactional
    public TimeEntryView addManual(UUID workspaceId, ManualEntry in) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        AssessmentRef a = assessments.ref(workspaceId, in.assessmentId());
        validateRange(in.startedAt(), in.endedAt());
        UUID id = Ids.newId();
        jdbc.sql("""
                insert into time_entry (id, workspace_id, assessment_id, user_id, started_at, ended_at, duration_seconds,
                                        billable, description)
                values (:id, :ws, :a, :u, :s, :e, :d, :b, :desc)""")
                .param("id", id).param("ws", workspaceId).param("a", a.id()).param("u", ctx.userId())
                .param("s", Timestamp.from(in.startedAt())).param("e", Timestamp.from(in.endedAt()))
                .param("d", Duration.between(in.startedAt(), in.endedAt()).toSeconds()).param("b", in.billable())
                .param("desc", blank(in.description())).update();
        events.publishEvent(AuditRecord.of(workspaceId, ctx.userId(), "TIME_ENTRY_CREATED", "time_entry", id));
        return get(workspaceId, id);
    }

    @Transactional
    public TimeEntryView start(UUID workspaceId, UUID assessmentId, boolean billable, String description) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        AssessmentRef a = assessments.ref(workspaceId, assessmentId);
        if (running(ctx.userId()) != null) {
            throw new ConflictException("A timer is already running; stop it first");
        }
        UUID id = Ids.newId();
        jdbc.sql("""
                insert into time_entry (id, workspace_id, assessment_id, user_id, started_at, billable, description)
                values (:id, :ws, :a, :u, now(), :b, :desc)""")
                .param("id", id).param("ws", workspaceId).param("a", a.id()).param("u", ctx.userId())
                .param("b", billable).param("desc", blank(description)).update();
        return get(workspaceId, id);
    }

    @Transactional
    public TimeEntryView stop(UUID workspaceId) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        UUID id = running(ctx.userId());
        if (id == null) {
            throw new ConflictException("No timer is running");
        }
        jdbc.sql("""
                update time_entry set ended_at = greatest(now(), started_at),
                       duration_seconds = least(extract(epoch from (greatest(now(), started_at) - started_at))::bigint, :max)
                where id = :id and workspace_id = :ws""")
                .param("id", id).param("ws", workspaceId).param("max", MAX_ENTRY.toSeconds()).update();
        return get(workspaceId, id);
    }

    @Transactional(readOnly = true)
    public TimeEntryView current(UUID workspaceId) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.READ);
        UUID id = running(ctx.userId());
        return id == null ? null : jdbc.sql(SELECT + " where t.id = :id").param("id", id).query(this::map).optional()
                .orElse(null);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID id) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        int n = jdbc.sql("delete from time_entry where id = :id and workspace_id = :ws and user_id = :u")
                .param("id", id).param("ws", workspaceId).param("u", ctx.userId()).update();
        if (n == 0) {
            throw new NotFoundException("Time entry");
        }
        events.publishEvent(AuditRecord.of(workspaceId, ctx.userId(), "TIME_ENTRY_DELETED", "time_entry", id));
    }

    @Transactional(readOnly = true)
    public List<TimeEntryView> list(UUID workspaceId, UUID assessmentId, LocalDate from, LocalDate to) {
        access.require(workspaceId, Permission.READ);
        Instant fromTs = (from == null ? LocalDate.now().minusDays(30) : from).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toTs = (to == null ? LocalDate.now() : to).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return jdbc.sql(SELECT + " " + """
                 where t.workspace_id = :ws and t.started_at >= :from and t.started_at < :to
                   and (cast(:a as uuid) is null or t.assessment_id = cast(:a as uuid))
                 order by t.started_at desc limit 1000""")
                .param("ws", workspaceId).param("from", Timestamp.from(fromTs)).param("to", Timestamp.from(toTs))
                .param("a", assessmentId).query(this::map).list();
    }

    /** RFC 4180 CSV; cells starting with = + - @ are prefixed to prevent spreadsheet formula injection. */
    @Transactional(readOnly = true)
    public String csv(UUID workspaceId, UUID assessmentId, LocalDate from, LocalDate to) {
        StringBuilder sb = new StringBuilder("assessment,title,started_at,ended_at,hours,billable,description\r\n");
        for (TimeEntryView e : list(workspaceId, assessmentId, from, to)) {
            sb.append(cell(e.assessmentCode())).append(',').append(cell(e.assessmentTitle())).append(',')
                    .append(e.startedAt()).append(',').append(e.endedAt() == null ? "" : e.endedAt()).append(',')
                    .append(String.format(java.util.Locale.ROOT, "%.2f", e.durationSeconds() / 3600.0)).append(',')
                    .append(e.billable()).append(',').append(cell(e.description())).append("\r\n");
        }
        return sb.toString();
    }

    static String cell(String v) {
        if (v == null) {
            return "";
        }
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static final String SELECT = """
            select t.id, t.assessment_id, a.code, a.title, t.user_id, t.started_at, t.ended_at,
                   coalesce(t.duration_seconds, extract(epoch from (now() - t.started_at))::bigint) as seconds,
                   t.billable, t.description
            from time_entry t join assessment a on a.id = t.assessment_id""";

    private TimeEntryView get(UUID workspaceId, UUID id) {
        return jdbc.sql(SELECT + " where t.id = :id and t.workspace_id = :ws").param("id", id).param("ws", workspaceId)
                .query(this::map).single();
    }

    private UUID running(UUID userId) {
        return jdbc.sql("select id from time_entry where user_id = :u and ended_at is null").param("u", userId)
                .query(UUID.class).optional().orElse(null);
    }

    private TimeEntryView map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Timestamp ended = rs.getTimestamp("ended_at");
        return new TimeEntryView(rs.getObject("id", UUID.class), rs.getObject("assessment_id", UUID.class),
                rs.getString("code"), rs.getString("title"), rs.getObject("user_id", UUID.class),
                rs.getTimestamp("started_at").toInstant(), ended == null ? null : ended.toInstant(),
                Math.max(0, rs.getLong("seconds")), ended == null, rs.getBoolean("billable"), rs.getString("description"));
    }

    private static void validateRange(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BadRequestException("End must be after start");
        }
        if (Duration.between(start, end).compareTo(MAX_ENTRY) > 0) {
            throw new BadRequestException("A single entry cannot exceed 24 hours");
        }
        if (end.isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
            throw new BadRequestException("Entries cannot end in the future");
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

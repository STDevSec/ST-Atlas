package com.stdevsec.atlas.assessment;

import com.stdevsec.atlas.assessment.internal.Assessment;
import com.stdevsec.atlas.assessment.internal.AssessmentRepository;
import com.stdevsec.atlas.assessment.internal.AssessmentTypeRepository;
import com.stdevsec.atlas.client.ClientService;
import com.stdevsec.atlas.client.ClientView;
import com.stdevsec.atlas.shared.AuditRecord;
import com.stdevsec.atlas.shared.BadRequestException;
import com.stdevsec.atlas.shared.ConflictException;
import com.stdevsec.atlas.shared.ForbiddenException;
import com.stdevsec.atlas.shared.Ids;
import com.stdevsec.atlas.shared.NotFoundException;
import com.stdevsec.atlas.shared.PageResponse;
import com.stdevsec.atlas.shared.SearchDocument;
import com.stdevsec.atlas.workspace.WorkspaceAccess;
import com.stdevsec.atlas.workspace.WorkspaceContext;
import com.stdevsec.atlas.workspace.WorkspaceRole.Permission;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentService {

    public record AssessmentInput(String title, String description, String typeCode, UUID clientId,
            LocalDate startDate, LocalDate dueDate, String objectives, String scope, String outOfScope,
            String rulesOfEngagement, String methodology, List<String> tags) {}

    public record Filter(String query, AssessmentStatus status, String typeCode, UUID clientId, String tag) {}

    /** API sort keys → SQL columns (allow-list: user input never reaches ORDER BY directly). */
    static final Map<String, String> SORTABLE = Map.of("title", "title", "code", "code", "status", "status",
            "dueDate", "due_date", "startDate", "start_date", "updatedAt", "updated_at");

    private final AssessmentRepository assessments;
    private final AssessmentTypeRepository types;
    private final WorkspaceAccess access;
    private final ClientService clients;
    private final ApplicationEventPublisher events;

    AssessmentService(AssessmentRepository assessments, AssessmentTypeRepository types, WorkspaceAccess access,
            ClientService clients, ApplicationEventPublisher events) {
        this.assessments = assessments;
        this.types = types;
        this.access = access;
        this.clients = clients;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssessmentView> list(UUID workspaceId, Filter f, int page, int size, String sort, boolean asc) {
        access.require(workspaceId, Permission.READ);
        String sortField = SORTABLE.getOrDefault(sort, "updated_at");
        PageRequest pr = PageRequest.of(page, size, asc ? Sort.by(sortField).ascending() : Sort.by(sortField).descending());
        Page<Assessment> result = assessments.search(workspaceId, f.status(), blank(f.typeCode()), f.clientId(),
                blank(f.tag()), likePattern(f.query()), pr);
        return PageResponse.of(result, a -> toView(workspaceId, a));
    }

    @Transactional(readOnly = true)
    public AssessmentView get(UUID workspaceId, UUID id) {
        access.require(workspaceId, Permission.READ);
        return toView(workspaceId, load(workspaceId, id));
    }

    /** For other modules, after their own access check. */
    @Transactional(readOnly = true)
    public AssessmentRef ref(UUID workspaceId, UUID id) {
        access.require(workspaceId, Permission.READ);
        Assessment a = load(workspaceId, id);
        return new AssessmentRef(a.getId(), a.getWorkspaceId(), a.getCode(), a.getTitle(), a.getTypeCode(),
                usesCvss(a.getTypeCode()), a.getClientId(), a.getStatus(), a.getStartDate(), a.getDueDate(),
                a.getCompletedDate(), a.getObjectives(), a.getScope(), a.getMethodology(), a.getTags());
    }

    @Transactional
    public AssessmentView create(UUID workspaceId, AssessmentInput in) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        validate(workspaceId, in);
        Assessment a = new Assessment(Ids.newId(), workspaceId, nextCode(workspaceId), ctx.userId());
        a.update(in);
        a.setAnalysts(List.of(ctx.userId()));
        assessments.save(a);
        publish(ctx, a, "ASSESSMENT_CREATED");
        return toView(workspaceId, a);
    }

    @Transactional
    public AssessmentView update(UUID workspaceId, UUID id, AssessmentInput in) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        validate(workspaceId, in);
        Assessment a = load(workspaceId, id);
        if (a.getStatus() == AssessmentStatus.ARCHIVED) {
            throw new ConflictException("Archived assessments are read-only");
        }
        a.update(in);
        publish(ctx, a, "ASSESSMENT_UPDATED");
        return toView(workspaceId, a);
    }

    @Transactional
    public AssessmentView transition(UUID workspaceId, UUID id, AssessmentStatus target) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        Assessment a = load(workspaceId, id);
        AssessmentStatus from = a.getStatus();
        if (!from.canMoveTo(target)) {
            throw new ConflictException("Cannot move from " + from + " to " + target);
        }
        if (from == AssessmentStatus.TECHNICAL_REVIEW && target == AssessmentStatus.APPROVED
                && !ctx.role().allows(Permission.REVIEW)) {
            throw new ForbiddenException("Only reviewers can approve");
        }
        a.moveTo(target, LocalDate.now());
        events.publishEvent(new AuditRecord(workspaceId, ctx.userId(), "ASSESSMENT_STATUS_CHANGED", "assessment", id,
                Map.of("from", from.name(), "to", target.name())));
        publishIndex(workspaceId, a);
        return toView(workspaceId, a);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID id) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.DELETE);
        Assessment a = load(workspaceId, id);
        if (a.getStatus() != AssessmentStatus.DRAFT) {
            throw new ConflictException("Only draft assessments can be deleted; archive it instead");
        }
        assessments.delete(a);
        events.publishEvent(new SearchDocument.Removed("assessment", id));
        events.publishEvent(AuditRecord.of(workspaceId, ctx.userId(), "ASSESSMENT_DELETED", "assessment", id));
    }

    public record TypeView(String code, boolean usesCvss) {}

    @Transactional(readOnly = true)
    public List<TypeView> types() {
        return types.findByEnabledTrueOrderBySortOrder().stream().map(t -> new TypeView(t.getCode(), t.isUsesCvss()))
                .toList();
    }

    private void validate(UUID workspaceId, AssessmentInput in) {
        if (types.findById(in.typeCode()).filter(t -> t.isEnabled()).isEmpty()) {
            throw new BadRequestException("Unknown assessment type");
        }
        if (in.clientId() != null && clients.find(workspaceId, in.clientId()).isEmpty()) {
            throw new BadRequestException("Unknown client");
        }
        if (in.startDate() != null && in.dueDate() != null && in.dueDate().isBefore(in.startDate())) {
            throw new BadRequestException("Due date must not be before the start date");
        }
    }

    private boolean usesCvss(String typeCode) {
        return types.findById(typeCode).map(t -> t.isUsesCvss()).orElse(false);
    }

    private String nextCode(UUID workspaceId) {
        String prefix = "ATL-" + Year.now() + "-";
        long n = assessments.countByWorkspaceIdAndCodeStartingWith(workspaceId, prefix) + 1;
        String code;
        do {
            code = prefix + String.format("%04d", n++);
        } while (assessments.existsByWorkspaceIdAndCode(workspaceId, code));
        return code;
    }

    private Assessment load(UUID workspaceId, UUID id) {
        return assessments.findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> new NotFoundException("Assessment"));
    }

    private AssessmentView toView(UUID workspaceId, Assessment a) {
        String clientName = a.getClientId() == null ? null
                : clients.find(workspaceId, a.getClientId()).map(ClientView::name).orElse(null);
        return new AssessmentView(a.getId(), a.getCode(), a.getTitle(), a.getDescription(), a.getTypeCode(),
                usesCvss(a.getTypeCode()), a.getClientId(), clientName, a.getStatus(), a.getStatus().next(),
                a.getStartDate(), a.getDueDate(), a.getCompletedDate(), a.getObjectives(), a.getScope(),
                a.getOutOfScope(), a.getRulesOfEngagement(), a.getMethodology(), a.getTags(), a.getAnalysts(),
                a.getCreatedAt(), a.getUpdatedAt());
    }

    private void publish(WorkspaceContext ctx, Assessment a, String action) {
        publishIndex(ctx.workspaceId(), a);
        events.publishEvent(AuditRecord.of(ctx.workspaceId(), ctx.userId(), action, "assessment", a.getId()));
    }

    private void publishIndex(UUID workspaceId, Assessment a) {
        String body = String.join("\n", nz(a.getCode()), nz(a.getDescription()), nz(a.getObjectives()),
                nz(a.getScope()), nz(a.getOutOfScope()), nz(a.getRulesOfEngagement()), nz(a.getMethodology()));
        events.publishEvent(new SearchDocument.Indexed(new SearchDocument(workspaceId, "assessment", a.getId(),
                a.getId(), a.getTitle(), body, a.getTags(), null, a.getStatus().name(), a.getTypeCode(),
                a.getClientId())));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}

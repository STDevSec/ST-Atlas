package com.stdevsec.atlas.finding;

import com.stdevsec.atlas.assessment.AssessmentRef;
import com.stdevsec.atlas.assessment.AssessmentService;
import com.stdevsec.atlas.finding.internal.Finding;
import com.stdevsec.atlas.finding.internal.FindingRepository;
import com.stdevsec.atlas.shared.AuditRecord;
import com.stdevsec.atlas.shared.BadRequestException;
import com.stdevsec.atlas.shared.Ids;
import com.stdevsec.atlas.shared.NotFoundException;
import com.stdevsec.atlas.shared.SearchDocument;
import com.stdevsec.atlas.workspace.WorkspaceAccess;
import com.stdevsec.atlas.workspace.WorkspaceContext;
import com.stdevsec.atlas.workspace.WorkspaceRole.Permission;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindingService {

    private static final Pattern CVSS40 = Pattern.compile("CVSS:4\\.0(/[A-Z]{1,3}:[A-Z])+");
    private static final Pattern CWE = Pattern.compile("CWE-\\d{1,5}");
    private static final Pattern CVE = Pattern.compile("CVE-\\d{4}-\\d{4,7}");

    public record FindingInput(String title, String description, String technicalDetails, Severity severity,
            String cvss31Vector, String cvss40Vector, BigDecimal cvss40Score, List<String> cwe, List<String> cve,
            String impact, String recommendation, List<String> references, List<String> tags,
            RemediationStatus status, LocalDate discoveredOn) {}

    private final FindingRepository findings;
    private final AssessmentService assessments;
    private final WorkspaceAccess access;
    private final ApplicationEventPublisher events;

    FindingService(FindingRepository findings, AssessmentService assessments, WorkspaceAccess access,
            ApplicationEventPublisher events) {
        this.findings = findings;
        this.assessments = assessments;
        this.access = access;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<FindingView> listForAssessment(UUID workspaceId, UUID assessmentId) {
        access.require(workspaceId, Permission.READ);
        assessments.ref(workspaceId, assessmentId);
        return findings.findByWorkspaceIdAndAssessmentIdOrderBySeverityRankAscCodeAsc(workspaceId, assessmentId)
                .stream().map(Finding::toView).toList();
    }

    @Transactional(readOnly = true)
    public FindingView get(UUID workspaceId, UUID id) {
        access.require(workspaceId, Permission.READ);
        return load(workspaceId, id).toView();
    }

    @Transactional
    public FindingView create(UUID workspaceId, UUID assessmentId, FindingInput in) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        AssessmentRef assessment = assessments.ref(workspaceId, assessmentId);
        Finding f = new Finding(Ids.newId(), workspaceId, assessmentId, nextCode(assessmentId), ctx.userId());
        apply(f, in, assessment);
        findings.save(f);
        publish(ctx, f, assessment, "FINDING_CREATED");
        return f.toView();
    }

    @Transactional
    public FindingView update(UUID workspaceId, UUID id, FindingInput in) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        Finding f = load(workspaceId, id);
        AssessmentRef assessment = assessments.ref(workspaceId, f.getAssessmentId());
        apply(f, in, assessment);
        publish(ctx, f, assessment, "FINDING_UPDATED");
        return f.toView();
    }

    @Transactional
    public void delete(UUID workspaceId, UUID id) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.DELETE);
        Finding f = load(workspaceId, id);
        findings.delete(f);
        events.publishEvent(new SearchDocument.Removed("finding", id));
        events.publishEvent(AuditRecord.of(workspaceId, ctx.userId(), "FINDING_DELETED", "finding", id));
    }

    public record FindingListItem(FindingView finding, String assessmentCode, String assessmentTitle) {}

    @Transactional(readOnly = true)
    public com.stdevsec.atlas.shared.PageResponse<FindingListItem> search(UUID workspaceId, Severity severity,
            RemediationStatus status, String query, int page, int size) {
        access.require(workspaceId, Permission.READ);
        String q = query == null || query.isBlank() ? null
                : "%" + query.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        var result = findings.search(workspaceId, severity, status, q,
                org.springframework.data.domain.PageRequest.of(page, size));
        java.util.Map<UUID, AssessmentRef> refs = new java.util.HashMap<>();
        return com.stdevsec.atlas.shared.PageResponse.of(result, f -> {
            AssessmentRef a = refs.computeIfAbsent(f.getAssessmentId(), id -> assessments.ref(workspaceId, id));
            return new FindingListItem(f.toView(), a.code(), a.title());
        });
    }

    /** Findings of an assessment for rendering (report module). */
    @Transactional(readOnly = true)
    public List<FindingView> forReport(UUID workspaceId, UUID assessmentId) {
        return listForAssessment(workspaceId, assessmentId);
    }

    private void apply(Finding f, FindingInput in, AssessmentRef assessment) {
        BigDecimal cvss31Score = null;
        String cvss31 = blank(in.cvss31Vector());
        String cvss40 = blank(in.cvss40Vector());
        Severity severity = in.severity();
        if (!assessment.usesCvss() && (cvss31 != null || cvss40 != null)) {
            throw new BadRequestException("This assessment type does not use CVSS");
        }
        if (cvss31 != null) {
            if (!Cvss31.isValid(cvss31)) {
                throw new BadRequestException("Invalid CVSS 3.1 vector");
            }
            double score = Cvss31.baseScore(cvss31);
            cvss31Score = Cvss31.asDecimal(score);
            if (severity == null) {
                severity = Severity.fromCvss(score);
            }
        }
        if (cvss40 != null && !CVSS40.matcher(cvss40).matches()) {
            throw new BadRequestException("Invalid CVSS 4.0 vector");
        }
        if (in.cvss40Score() != null && cvss40 == null) {
            throw new BadRequestException("A CVSS 4.0 score needs a CVSS 4.0 vector");
        }
        checkAll(in.cwe(), CWE, "CWE");
        checkAll(in.cve(), CVE, "CVE");
        if (severity == null) {
            throw new BadRequestException("Severity is required");
        }
        f.update(in, severity, cvss31, cvss31Score, cvss40);
    }

    private static void checkAll(List<String> values, Pattern pattern, String label) {
        if (values != null) {
            for (String v : values) {
                if (!pattern.matcher(v.trim().toUpperCase()).matches()) {
                    throw new BadRequestException("Invalid " + label + " identifier: use " + label + "-NNNN");
                }
            }
        }
    }

    private String nextCode(UUID assessmentId) {
        long n = findings.countByAssessmentId(assessmentId) + 1;
        String code;
        do {
            code = "FND-" + String.format("%03d", n++);
        } while (findings.existsByAssessmentIdAndCode(assessmentId, code));
        return code;
    }

    private Finding load(UUID workspaceId, UUID id) {
        return findings.findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> new NotFoundException("Finding"));
    }

    private void publish(WorkspaceContext ctx, Finding f, AssessmentRef a, String action) {
        String body = String.join("\n", f.getCode(), nz(f.getDescription()), nz(f.getTechnicalDetails()),
                nz(f.getImpact()), nz(f.getRecommendation()), String.join(" ", f.getCwe()), String.join(" ", f.getCve()),
                a.code() + " " + a.title());
        events.publishEvent(new SearchDocument.Indexed(new SearchDocument(ctx.workspaceId(), "finding", f.getId(),
                a.id(), f.getTitle(), body, f.getTags(), f.getSeverity().name(), f.getStatus().name(), a.typeCode(),
                a.clientId())));
        events.publishEvent(AuditRecord.of(ctx.workspaceId(), ctx.userId(), action, "finding", f.getId()));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

package com.stdevsec.atlas.report;

import com.stdevsec.atlas.assessment.AssessmentRef;
import com.stdevsec.atlas.assessment.AssessmentService;
import com.stdevsec.atlas.client.ClientService;
import com.stdevsec.atlas.client.ClientView;
import com.stdevsec.atlas.finding.FindingService;
import com.stdevsec.atlas.finding.FindingView;
import com.stdevsec.atlas.identity.UserDirectory;
import com.stdevsec.atlas.identity.UserSummary;
import com.stdevsec.atlas.report.ReportViews.ReportDetail;
import com.stdevsec.atlas.report.ReportViews.ReportSummary;
import com.stdevsec.atlas.report.ReportViews.VersionView;
import com.stdevsec.atlas.report.internal.DocumentValidator;
import com.stdevsec.atlas.report.internal.GotenbergClient;
import com.stdevsec.atlas.report.internal.Labels;
import com.stdevsec.atlas.report.internal.Report;
import com.stdevsec.atlas.report.internal.ReportRenderer;
import com.stdevsec.atlas.report.internal.ReportRepository;
import com.stdevsec.atlas.report.internal.ReportSupport;
import com.stdevsec.atlas.report.internal.ReportVersion;
import com.stdevsec.atlas.report.internal.ReportVersionRepository;
import com.stdevsec.atlas.shared.AuditRecord;
import com.stdevsec.atlas.shared.BadRequestException;
import com.stdevsec.atlas.shared.ConflictException;
import com.stdevsec.atlas.shared.Ids;
import com.stdevsec.atlas.shared.NotFoundException;
import com.stdevsec.atlas.shared.PageResponse;
import com.stdevsec.atlas.shared.SearchDocument;
import com.stdevsec.atlas.storage.AttachmentInfo;
import com.stdevsec.atlas.storage.AttachmentService;
import com.stdevsec.atlas.storage.UploadKind;
import com.stdevsec.atlas.system.InstanceSettings;
import com.stdevsec.atlas.workspace.WorkspaceAccess;
import com.stdevsec.atlas.workspace.WorkspaceContext;
import com.stdevsec.atlas.workspace.WorkspaceRole.Permission;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ReportService {

    static final Set<String> CLASSIFICATIONS = Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED");

    private final ReportRepository reports;
    private final ReportVersionRepository versions;
    private final TemplateService templates;
    private final AssessmentService assessments;
    private final FindingService findings;
    private final ClientService clients;
    private final AttachmentService attachments;
    private final UserDirectory users;
    private final InstanceSettings settings;
    private final GotenbergClient gotenberg;
    private final WorkspaceAccess access;
    private final ApplicationEventPublisher events;
    private final JsonMapper json = JsonMapper.builder().build();

    ReportService(ReportRepository reports, ReportVersionRepository versions, TemplateService templates,
            AssessmentService assessments, FindingService findings, ClientService clients,
            AttachmentService attachments, UserDirectory users, InstanceSettings settings, GotenbergClient gotenberg,
            WorkspaceAccess access, ApplicationEventPublisher events) {
        this.reports = reports;
        this.versions = versions;
        this.templates = templates;
        this.assessments = assessments;
        this.findings = findings;
        this.clients = clients;
        this.attachments = attachments;
        this.users = users;
        this.settings = settings;
        this.gotenberg = gotenberg;
        this.access = access;
        this.events = events;
    }

    @Transactional
    public ReportDetail create(UUID workspaceId, UUID assessmentId, String title, UUID templateId) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        AssessmentRef a = assessments.ref(workspaceId, assessmentId);
        boolean spanish = "es".equals(users.get(ctx.userId()).locale());
        UUID template = templateId == null ? defaultTemplate(a.typeCode()) : templates.get(workspaceId, templateId).id();
        String doc = json.writeValueAsString(ReportSupport.initialDocument(a, spanish));
        Report r = reports.save(new Report(Ids.newId(), workspaceId, assessmentId,
                title == null || title.isBlank() ? a.title() : title.trim(), template, doc, "CONFIDENTIAL",
                ctx.userId()));
        index(workspaceId, r, a);
        events.publishEvent(AuditRecord.of(workspaceId, ctx.userId(), "REPORT_CREATED", "report", r.getId()));
        return detail(r);
    }

    private static UUID defaultTemplate(String typeCode) {
        return switch (typeCode) {
            case "DIGITAL_FORENSICS", "INCIDENT_RESPONSE", "SOC_INVESTIGATION" -> TemplateService.FORENSIC;
            case "CTF_WRITEUP", "CYBERSECURITY_LAB", "TECHNICAL_RESEARCH" -> TemplateService.WRITEUP;
            default -> TemplateService.STANDARD;
        };
    }

    @Transactional(readOnly = true)
    public List<ReportSummary> listForAssessment(UUID workspaceId, UUID assessmentId) {
        access.require(workspaceId, Permission.READ);
        return reports.findByWorkspaceIdAndAssessmentIdOrderByUpdatedAtDesc(workspaceId, assessmentId).stream()
                .map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportSummary> recent(UUID workspaceId, int page, int size) {
        access.require(workspaceId, Permission.READ);
        return PageResponse.of(reports.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId, PageRequest.of(page, size)),
                this::summary);
    }

    @Transactional(readOnly = true)
    public ReportDetail get(UUID workspaceId, UUID id) {
        access.require(workspaceId, Permission.READ);
        return detail(load(workspaceId, id));
    }

    /** Autosave with optimistic concurrency: the caller must send the revision it edited. */
    @Transactional
    public ReportDetail save(UUID workspaceId, UUID id, String title, String document, int expectedRevision,
            UUID templateId, String classification) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        Report r = load(workspaceId, id);
        if (r.getRevision() != expectedRevision) {
            throw new ConflictException("The report was changed elsewhere; reload to continue");
        }
        if (!CLASSIFICATIONS.contains(classification)) {
            throw new BadRequestException("Unknown classification");
        }
        DocumentValidator.Result result = DocumentValidator.validate(document, json);
        for (UUID attachmentId : result.attachmentIds()) {
            attachments.info(workspaceId, attachmentId); // 404 if not in this workspace
        }
        Set<UUID> assessmentFindings = findings.listForAssessment(workspaceId, r.getAssessmentId()).stream()
                .map(FindingView::id).collect(Collectors.toSet());
        if (!assessmentFindings.containsAll(result.findingIds())) {
            throw new BadRequestException("Reports can only reference findings of their assessment");
        }
        UUID template = templateId == null ? r.getTemplateId() : templates.get(workspaceId, templateId).id();
        r.save(title.trim(), document, template, classification);
        index(workspaceId, r, assessments.ref(workspaceId, r.getAssessmentId()));
        return detail(r);
    }

    /** Renders the current document to PDF and stores it as a new version. */
    @Transactional
    public VersionView render(UUID workspaceId, UUID id, boolean finalize) {
        WorkspaceContext ctx = access.require(workspaceId, finalize ? Permission.REVIEW : Permission.WRITE);
        Report r = load(workspaceId, id);
        AssessmentRef a = assessments.ref(workspaceId, r.getAssessmentId());
        List<FindingView> fs = findings.forReport(workspaceId, a.id());
        Map<UUID, FindingView> byId = fs.stream().collect(Collectors.toMap(FindingView::id, f -> f));
        JsonNode snapshot = ReportSupport.snapshot(json.readTree(r.getDocument()), byId, fs);

        TemplateConfig template = templates.configFor(workspaceId, r.getTemplateId());
        template = withClassification(template, r.getClassification());
        UserSummary author = users.get(ctx.userId());
        ClientView client = a.clientId() == null ? null : clients.find(workspaceId, a.clientId()).orElse(null);
        int number = versions.maxNumber(r.getId()) + 1;

        Map<String, String> vars = new HashMap<>();
        vars.put("organization.name", settings.get(InstanceSettings.ORGANIZATION_NAME).orElse(""));
        vars.put("client.name", client == null ? "" : client.name());
        vars.put("assessment.title", a.title());
        vars.put("assessment.code", a.code());
        vars.put("assessment.type", ReportSupport.typeLabel(a.typeCode(), author.locale()));
        vars.put("assessment.startDate", a.startDate() == null ? "" : a.startDate().toString());
        vars.put("assessment.endDate", a.completedDate() != null ? a.completedDate().toString()
                : a.dueDate() == null ? "" : a.dueDate().toString());
        vars.put("report.author", author.displayName());
        vars.put("report.version", Integer.toString(number));
        vars.put("report.classification", Labels.forLocale(author.locale()).classification(template.classification()));
        vars.put("report.date", LocalDate.now().toString());
        vars.put("client.logo", "");
        vars.put("organization.logo", "");

        Function<UUID, ReportRenderer.Image> images = attachmentId -> image(workspaceId, attachmentId);
        ReportRenderer.Bundle bundle = ReportRenderer.render(new ReportRenderer.Input(snapshot, template, vars, images,
                template.organizationLogoAttachmentId() == null ? null
                        : image(workspaceId, template.organizationLogoAttachmentId()),
                client == null || client.logoAttachmentId() == null ? null : image(workspaceId, client.logoAttachmentId()),
                Labels.forLocale(author.locale())));
        byte[] pdf;
        try {
            pdf = gotenberg.convert(bundle, template, Map.of("Title", r.getTitle(), "Author", author.displayName(),
                    "Subject", a.code() + " " + a.title(), "Creator", "ST Atlas", "Keywords", String.join(", ", a.tags())));
        } catch (GotenbergClient.RenderFailedException e) {
            throw new ConflictException("PDF rendering service is not available");
        }
        AttachmentInfo stored = attachments.storeGenerated(workspaceId, UploadKind.PDF,
                ReportSupport.fileName(a.code(), r.getTitle(), number), pdf);
        ReportVersion v = versions.save(new ReportVersion(Ids.newId(), workspaceId, r.getId(), number,
                json.writeValueAsString(snapshot), json.writeValueAsString(template), stored.id(),
                ReportSupport.countPages(pdf), finalize, ctx.userId()));
        events.publishEvent(new AuditRecord(workspaceId, ctx.userId(), finalize ? "REPORT_FINALIZED" : "REPORT_RENDERED",
                "report", r.getId(), Map.of("version", number, "sha256", stored.sha256())));
        return view(v);
    }

    @Transactional(readOnly = true)
    public List<VersionView> versions(UUID workspaceId, UUID id) {
        access.require(workspaceId, Permission.READ);
        load(workspaceId, id);
        return versions.findByWorkspaceIdAndReportIdOrderByNumberDesc(workspaceId, id).stream().map(ReportService::view)
                .toList();
    }

    private static TemplateConfig withClassification(TemplateConfig t, String classification) {
        return new TemplateConfig(t.title(), t.subtitle(), t.paperSize(), t.orientation(), t.margins(), t.primaryColor(),
                t.accentColor(), t.fontFamily(), t.baseFontSizePt(), t.showCover(), t.showOrganizationLogo(),
                t.showClientLogo(), t.organizationLogoAttachmentId(), t.logoPosition(), t.headerText(), t.footerText(),
                t.pageNumbers(), TemplateConfig.Classification.valueOf(classification), t.watermarkText(),
                t.showFindingsSummary(), t.showCvss());
    }

    private ReportRenderer.Image image(UUID workspaceId, UUID attachmentId) {
        try {
            AttachmentInfo info = attachments.info(workspaceId, attachmentId);
            if (!info.mimeType().startsWith("image/")) {
                return null;
            }
            return new ReportRenderer.Image(attachments.readAll(workspaceId, attachmentId), info.mimeType());
        } catch (NotFoundException e) {
            return null;
        }
    }

    private void index(UUID workspaceId, Report r, AssessmentRef a) {
        String text = ReportSupport.plainText(json.readTree(r.getDocument()));
        events.publishEvent(new SearchDocument.Indexed(new SearchDocument(workspaceId, "report", r.getId(), a.id(),
                r.getTitle(), a.code() + " " + a.title() + "\n" + text, a.tags(), null, a.status().name(), a.typeCode(),
                a.clientId())));
    }

    private Report load(UUID workspaceId, UUID id) {
        return reports.findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> new NotFoundException("Report"));
    }

    private ReportSummary summary(Report r) {
        List<ReportVersion> vs = versions.findByWorkspaceIdAndReportIdOrderByNumberDesc(r.getWorkspaceId(), r.getId());
        return new ReportSummary(r.getId(), r.getAssessmentId(), r.getTitle(), r.getTemplateId(), r.getRevision(),
                r.getClassification(), r.getUpdatedAt(), vs.isEmpty() ? null : vs.getFirst().getNumber());
    }

    private static ReportDetail detail(Report r) {
        return new ReportDetail(r.getId(), r.getAssessmentId(), r.getTitle(), r.getTemplateId(), r.getRevision(),
                r.getClassification(), r.getDocument(), r.getUpdatedAt());
    }

    private static VersionView view(ReportVersion v) {
        return new VersionView(v.getId(), v.getNumber(), v.getPdfAttachmentId(), v.getPageCount(), v.isFinalized(),
                v.getCreatedBy(), v.getCreatedAt());
    }
}

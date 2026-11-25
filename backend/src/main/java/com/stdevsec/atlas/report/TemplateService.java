package com.stdevsec.atlas.report;

import com.stdevsec.atlas.report.ReportViews.TemplateView;
import com.stdevsec.atlas.report.internal.ReportTemplate;
import com.stdevsec.atlas.report.internal.ReportTemplateRepository;
import com.stdevsec.atlas.shared.AuditRecord;
import com.stdevsec.atlas.shared.BadRequestException;
import com.stdevsec.atlas.shared.ForbiddenException;
import com.stdevsec.atlas.shared.Ids;
import com.stdevsec.atlas.shared.NotFoundException;
import com.stdevsec.atlas.storage.AttachmentService;
import com.stdevsec.atlas.workspace.WorkspaceAccess;
import com.stdevsec.atlas.workspace.WorkspaceContext;
import com.stdevsec.atlas.workspace.WorkspaceRole.Permission;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TemplateService {

    /** Built-in templates have fixed ids so they survive restarts and upgrades. */
    public static final UUID STANDARD = UUID.fromString("00000000-0000-7000-8000-000000000001");
    static final UUID FORENSIC = UUID.fromString("00000000-0000-7000-8000-000000000002");
    static final UUID WRITEUP = UUID.fromString("00000000-0000-7000-8000-000000000003");

    private final ReportTemplateRepository templates;
    private final WorkspaceAccess access;
    private final AttachmentService attachments;
    private final Validator validator;
    private final ApplicationEventPublisher events;
    private final JsonMapper json = JsonMapper.builder().build();

    TemplateService(ReportTemplateRepository templates, WorkspaceAccess access, AttachmentService attachments,
            Validator validator, ApplicationEventPublisher events) {
        this.templates = templates;
        this.access = access;
        this.attachments = attachments;
        this.validator = validator;
        this.events = events;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedBuiltIns() {
        TemplateConfig std = TemplateConfig.standard();
        seed(STANDARD, "ST Atlas — Standard", std);
        seed(FORENSIC, "ST Atlas — Forensic / IR", new TemplateConfig("{{assessment.title}}",
                "{{assessment.type}} — {{client.name}}", std.paperSize(), std.orientation(), std.margins(), "#2D3748",
                "#4A5568", TemplateConfig.FontFamily.SERIF, 10, true, true, true, null, std.logoPosition(),
                std.headerText(), std.footerText(), true, TemplateConfig.Classification.RESTRICTED, null, false, false));
        seed(WRITEUP, "ST Atlas — Write-up", new TemplateConfig("{{assessment.title}}", "{{report.author}}",
                std.paperSize(), std.orientation(), new TemplateConfig.Margins(18, 18, 18, 18), "#0F766E", "#14B8A6",
                TemplateConfig.FontFamily.SANS, 10, true, false, false, null, std.logoPosition(), "{{assessment.title}}",
                "", true, TemplateConfig.Classification.PUBLIC, null, false, false));
    }

    private void seed(UUID id, String name, TemplateConfig config) {
        if (!templates.existsById(id)) {
            templates.save(new ReportTemplate(id, null, name, json.writeValueAsString(config)));
        }
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list(UUID workspaceId) {
        access.require(workspaceId, Permission.READ);
        return templates.visibleIn(workspaceId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public TemplateView get(UUID workspaceId, UUID id) {
        access.require(workspaceId, Permission.READ);
        return view(load(workspaceId, id));
    }

    /** Resolves the config to render with; falls back to the standard template. */
    @Transactional(readOnly = true)
    public TemplateConfig configFor(UUID workspaceId, UUID templateId) {
        UUID id = templateId == null ? STANDARD : templateId;
        return templates.findById(id)
                .filter(t -> t.getWorkspaceId() == null || t.getWorkspaceId().equals(workspaceId))
                .map(t -> json.readValue(t.getConfig(), TemplateConfig.class))
                .orElseGet(TemplateConfig::standard);
    }

    @Transactional
    public TemplateView create(UUID workspaceId, String name, TemplateConfig config) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        validate(workspaceId, config);
        ReportTemplate t = templates.save(new ReportTemplate(Ids.newId(), workspaceId, name.trim(),
                json.writeValueAsString(config)));
        events.publishEvent(AuditRecord.of(workspaceId, ctx.userId(), "TEMPLATE_CREATED", "template", t.getId()));
        return view(t);
    }

    @Transactional
    public TemplateView update(UUID workspaceId, UUID id, String name, TemplateConfig config) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.WRITE);
        ReportTemplate t = load(workspaceId, id);
        if (t.getWorkspaceId() == null) {
            throw new ForbiddenException("Built-in templates cannot be changed; duplicate it instead");
        }
        validate(workspaceId, config);
        t.update(name.trim(), json.writeValueAsString(config));
        events.publishEvent(AuditRecord.of(workspaceId, ctx.userId(), "TEMPLATE_UPDATED", "template", id));
        return view(t);
    }

    @Transactional
    public TemplateView duplicate(UUID workspaceId, UUID id) {
        TemplateView source = get(workspaceId, id);
        return create(workspaceId, (source.name() + " (copy)").substring(0, Math.min(120, source.name().length() + 7)),
                source.config());
    }

    @Transactional
    public void delete(UUID workspaceId, UUID id) {
        WorkspaceContext ctx = access.require(workspaceId, Permission.DELETE);
        ReportTemplate t = load(workspaceId, id);
        if (t.getWorkspaceId() == null) {
            throw new ForbiddenException("Built-in templates cannot be deleted");
        }
        templates.delete(t);
        events.publishEvent(AuditRecord.of(workspaceId, ctx.userId(), "TEMPLATE_DELETED", "template", id));
    }

    private void validate(UUID workspaceId, TemplateConfig config) {
        Set<ConstraintViolation<TemplateConfig>> violations = validator.validate(config);
        if (!violations.isEmpty()) {
            ConstraintViolation<TemplateConfig> v = violations.iterator().next();
            throw new BadRequestException("Invalid template: " + v.getPropertyPath() + " " + v.getMessage());
        }
        if (config.organizationLogoAttachmentId() != null) {
            attachments.info(workspaceId, config.organizationLogoAttachmentId());
        }
    }

    private ReportTemplate load(UUID workspaceId, UUID id) {
        return templates.findById(id)
                .filter(t -> t.getWorkspaceId() == null || t.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new NotFoundException("Template"));
    }

    private TemplateView view(ReportTemplate t) {
        return new TemplateView(t.getId(), t.getName(), t.getWorkspaceId() == null,
                json.readValue(t.getConfig(), TemplateConfig.class), t.getUpdatedAt());
    }
}

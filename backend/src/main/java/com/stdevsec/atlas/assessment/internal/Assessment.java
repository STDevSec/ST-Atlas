package com.stdevsec.atlas.assessment.internal;

import com.stdevsec.atlas.assessment.AssessmentService.AssessmentInput;
import com.stdevsec.atlas.assessment.AssessmentStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "assessment")
public class Assessment {

    @Id
    private UUID id;
    @Column(name = "workspace_id")
    private UUID workspaceId;
    private String code;
    private String title;
    private String description;
    @Column(name = "type_code")
    private String typeCode;
    @Column(name = "client_id")
    private UUID clientId;
    @Enumerated(EnumType.STRING)
    private AssessmentStatus status = AssessmentStatus.DRAFT;
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(name = "completed_date")
    private LocalDate completedDate;
    private String objectives;
    private String scope;
    @Column(name = "out_of_scope")
    private String outOfScope;
    @Column(name = "rules_of_engagement")
    private String rulesOfEngagement;
    private String methodology;
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> tags = new ArrayList<>();
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "assessment_analyst", joinColumns = @JoinColumn(name = "assessment_id"))
    @Column(name = "user_id")
    private List<UUID> analysts = new ArrayList<>();

    protected Assessment() {}

    public Assessment(UUID id, UUID workspaceId, String code, UUID createdBy) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.code = code;
        this.createdBy = createdBy;
    }

    public void update(AssessmentInput in) {
        this.title = in.title().trim();
        this.description = in.description();
        this.typeCode = in.typeCode();
        this.clientId = in.clientId();
        this.startDate = in.startDate();
        this.dueDate = in.dueDate();
        this.objectives = in.objectives();
        this.scope = in.scope();
        this.outOfScope = in.outOfScope();
        this.rulesOfEngagement = in.rulesOfEngagement();
        this.methodology = in.methodology();
        this.tags = normalizeTags(in.tags());
        this.updatedAt = Instant.now();
    }

    public void moveTo(AssessmentStatus target, LocalDate today) {
        this.status = target;
        if (target == AssessmentStatus.COMPLETED && completedDate == null) {
            completedDate = today;
        }
        this.updatedAt = Instant.now();
    }

    public void setAnalysts(List<UUID> userIds) {
        analysts.clear();
        analysts.addAll(userIds);
    }

    /** Tags are case-insensitive labels: stored lower-case, without leading '#', de-duplicated. */
    static List<String> normalizeTags(List<String> raw) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String t : raw) {
            String s = t.trim().toLowerCase();
            if (s.startsWith("#")) {
                s = s.substring(1);
            }
            if (!s.isEmpty()) {
                set.add(s);
            }
        }
        return new ArrayList<>(set);
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTypeCode() { return typeCode; }
    public UUID getClientId() { return clientId; }
    public AssessmentStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getCompletedDate() { return completedDate; }
    public String getObjectives() { return objectives; }
    public String getScope() { return scope; }
    public String getOutOfScope() { return outOfScope; }
    public String getRulesOfEngagement() { return rulesOfEngagement; }
    public String getMethodology() { return methodology; }
    public List<String> getTags() { return List.copyOf(tags); }
    public List<UUID> getAnalysts() { return List.copyOf(analysts); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

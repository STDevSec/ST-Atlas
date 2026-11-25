package com.stdevsec.atlas.finding.internal;

import com.stdevsec.atlas.finding.FindingService.FindingInput;
import com.stdevsec.atlas.finding.FindingView;
import com.stdevsec.atlas.finding.RemediationStatus;
import com.stdevsec.atlas.finding.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "finding")
public class Finding {

    @Id
    private UUID id;
    @Column(name = "workspace_id")
    private UUID workspaceId;
    @Column(name = "assessment_id")
    private UUID assessmentId;
    private String code;
    private String title;
    private String description;
    @Column(name = "technical_details")
    private String technicalDetails;
    @Enumerated(EnumType.STRING)
    private Severity severity;
    @Column(name = "cvss31_vector")
    private String cvss31Vector;
    @Column(name = "cvss31_score")
    private BigDecimal cvss31Score;
    @Column(name = "cvss40_vector")
    private String cvss40Vector;
    @Column(name = "cvss40_score")
    private BigDecimal cvss40Score;
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> cwe = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> cve = new ArrayList<>();
    private String impact;
    private String recommendation;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "\"references\"")
    private List<String> references = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> tags = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    private RemediationStatus status = RemediationStatus.OPEN;
    @Column(name = "discovered_on")
    private LocalDate discoveredOn;
    @Column(name = "analyst_id")
    private UUID analystId;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    /** Sort key: most severe first. */
    @Formula("case severity when 'CRITICAL' then 0 when 'HIGH' then 1 when 'MEDIUM' then 2 when 'LOW' then 3 "
            + "when 'INFORMATIONAL' then 4 else 5 end")
    private int severityRank;

    protected Finding() {}

    public Finding(UUID id, UUID workspaceId, UUID assessmentId, String code, UUID analystId) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.assessmentId = assessmentId;
        this.code = code;
        this.analystId = analystId;
    }

    public void update(FindingInput in, Severity severity, String cvss31Vector, BigDecimal cvss31Score,
            String cvss40Vector) {
        this.title = in.title().trim();
        this.description = in.description();
        this.technicalDetails = in.technicalDetails();
        this.severity = severity;
        this.cvss31Vector = cvss31Vector;
        this.cvss31Score = cvss31Score;
        this.cvss40Vector = cvss40Vector;
        this.cvss40Score = cvss40Vector == null ? null : in.cvss40Score();
        this.cwe = upper(in.cwe());
        this.cve = upper(in.cve());
        this.impact = in.impact();
        this.recommendation = in.recommendation();
        this.references = new ArrayList<>(in.references());
        this.tags = tags(in.tags());
        this.status = in.status();
        this.discoveredOn = in.discoveredOn();
        this.updatedAt = Instant.now();
    }

    private static List<String> upper(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values.stream().map(v -> v.trim().toUpperCase()).toList()));
    }

    private static List<String> tags(List<String> raw) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String t : raw) {
            String s = t.trim().toLowerCase();
            set.add(s.startsWith("#") ? s.substring(1) : s);
        }
        set.remove("");
        return new ArrayList<>(set);
    }

    public FindingView toView() {
        return new FindingView(id, assessmentId, code, title, description, technicalDetails, severity, cvss31Vector,
                cvss31Score, cvss40Vector, cvss40Score, List.copyOf(cwe), List.copyOf(cve), impact, recommendation,
                List.copyOf(references), List.copyOf(tags), status, discoveredOn, analystId, createdAt, updatedAt);
    }

    public UUID getId() { return id; }
    public UUID getAssessmentId() { return assessmentId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getTechnicalDetails() { return technicalDetails; }
    public String getImpact() { return impact; }
    public String getRecommendation() { return recommendation; }
    public Severity getSeverity() { return severity; }
    public RemediationStatus getStatus() { return status; }
    public List<String> getCwe() { return List.copyOf(cwe); }
    public List<String> getCve() { return List.copyOf(cve); }
    public List<String> getTags() { return List.copyOf(tags); }
}

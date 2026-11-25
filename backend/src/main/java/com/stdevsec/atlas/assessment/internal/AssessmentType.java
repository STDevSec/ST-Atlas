package com.stdevsec.atlas.assessment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assessment_type")
public class AssessmentType {

    @Id
    private String code;
    @Column(name = "uses_cvss")
    private boolean usesCvss;
    @Column(name = "sort_order")
    private int sortOrder;
    private boolean enabled;

    protected AssessmentType() {}

    public String getCode() { return code; }
    public boolean isUsesCvss() { return usesCvss; }
    public boolean isEnabled() { return enabled; }
}

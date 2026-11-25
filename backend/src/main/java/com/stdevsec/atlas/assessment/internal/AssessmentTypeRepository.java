package com.stdevsec.atlas.assessment.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentTypeRepository extends JpaRepository<AssessmentType, String> {

    List<AssessmentType> findByEnabledTrueOrderBySortOrder();
}

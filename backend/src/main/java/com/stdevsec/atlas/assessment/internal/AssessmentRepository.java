package com.stdevsec.atlas.assessment.internal;

import com.stdevsec.atlas.assessment.AssessmentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    Optional<Assessment> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    long countByWorkspaceIdAndCodeStartingWith(UUID workspaceId, String prefix);

    boolean existsByWorkspaceIdAndCode(UUID workspaceId, String code);

    @Query(value = """
            select * from assessment a
            where a.workspace_id = :ws
              and (cast(:status as text) is null or a.status = cast(:status as text))
              and (cast(:type as text) is null or a.type_code = cast(:type as text))
              and (cast(:client as uuid) is null or a.client_id = cast(:client as uuid))
              and (cast(:tag as text) is null or cast(:tag as text) = any(a.tags))
              and (cast(:q as text) is null or lower(a.title) like cast(:q as text) or lower(a.code) like cast(:q as text))
            """,
            countQuery = """
            select count(*) from assessment a
            where a.workspace_id = :ws
              and (cast(:status as text) is null or a.status = cast(:status as text))
              and (cast(:type as text) is null or a.type_code = cast(:type as text))
              and (cast(:client as uuid) is null or a.client_id = cast(:client as uuid))
              and (cast(:tag as text) is null or cast(:tag as text) = any(a.tags))
              and (cast(:q as text) is null or lower(a.title) like cast(:q as text) or lower(a.code) like cast(:q as text))
            """,
            nativeQuery = true)
    Page<Assessment> searchNative(UUID ws, String status, String type, UUID client, String tag, String q,
            Pageable pageable);

    default Page<Assessment> search(UUID ws, AssessmentStatus status, String type, UUID client, String tag, String q,
            Pageable pageable) {
        return searchNative(ws, status == null ? null : status.name(), type, client, tag, q, pageable);
    }
}

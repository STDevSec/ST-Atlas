package com.stdevsec.atlas.client.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    Page<Client> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Page<Client> findByWorkspaceIdAndNameContainingIgnoreCase(UUID workspaceId, String name, Pageable pageable);

    Optional<Client> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}

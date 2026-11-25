package com.stdevsec.atlas.search;

import com.stdevsec.atlas.search.internal.SearchQueries;
import com.stdevsec.atlas.shared.PageResponse;
import com.stdevsec.atlas.workspace.WorkspaceAccess;
import com.stdevsec.atlas.workspace.WorkspaceRole.Permission;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchService {

    public enum TagMode { AND, OR }

    public record Query(String text, List<String> tags, TagMode tagMode, String entityType, String typeCode,
            String severity, String status, UUID clientId, LocalDate from, LocalDate to) {}

    private final SearchQueries queries;
    private final WorkspaceAccess access;

    SearchService(SearchQueries queries, WorkspaceAccess access) {
        this.queries = queries;
        this.access = access;
    }

    /** Searches one workspace; the access check happens before any query runs (T-07). */
    @Transactional(readOnly = true)
    public PageResponse<SearchHit> search(UUID workspaceId, Query q, int page, int size) {
        access.require(workspaceId, Permission.READ);
        return queries.search(workspaceId, q, page, size);
    }

    @Transactional(readOnly = true)
    public List<SearchQueries.TagCount> tags(UUID workspaceId, String prefix) {
        access.require(workspaceId, Permission.READ);
        return queries.tags(workspaceId, prefix);
    }
}

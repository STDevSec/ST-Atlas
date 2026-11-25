package com.stdevsec.atlas.search;

import com.stdevsec.atlas.search.internal.SearchQueries;
import com.stdevsec.atlas.shared.PageResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SearchController {

    private final SearchService service;

    SearchController(SearchService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/search")
    PageResponse<SearchHit> search(@PathVariable UUID workspaceId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "AND") SearchService.TagMode tagMode,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (q != null && q.length() > 200) {
            q = q.substring(0, 200);
        }
        return service.search(workspaceId, new SearchService.Query(q, tags == null ? List.of() : tags, tagMode,
                entityType, type, severity, status, clientId, from, to), Math.max(page, 0),
                Math.min(Math.max(size, 1), 50));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/tags")
    List<SearchQueries.TagCount> tags(@PathVariable UUID workspaceId, @RequestParam(required = false) String prefix) {
        return service.tags(workspaceId, prefix);
    }
}

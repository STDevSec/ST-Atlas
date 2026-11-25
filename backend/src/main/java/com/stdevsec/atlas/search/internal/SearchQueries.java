package com.stdevsec.atlas.search.internal;

import com.stdevsec.atlas.search.SearchHit;
import com.stdevsec.atlas.search.SearchService.Query;
import com.stdevsec.atlas.search.SearchService.TagMode;
import com.stdevsec.atlas.shared.PageResponse;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Full-text query. The workspace filter is the first predicate, and totals, snippets and
 * ranks are computed on the same filtered rows, so nothing from another workspace can
 * influence the response (T-07). User text only ever reaches PostgreSQL as a bound parameter.
 */
@Component
public class SearchQueries {

    /** Highlight marker: invisible in plain text, never valid HTML. */
    public static final String MARK = "⁣";

    public record TagCount(String tag, long count) {}

    private final NamedParameterJdbcTemplate jdbc;

    SearchQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<SearchHit> search(UUID workspaceId, Query q, int page, int size) {
        MapSqlParameterSource p = new MapSqlParameterSource("ws", workspaceId);
        List<String> where = new ArrayList<>(List.of("s.workspace_id = :ws"));
        String text = q.text() == null ? "" : q.text().trim();
        String rank = "0";
        String snippet = "left(s.body, 220)";
        if (!text.isEmpty()) {
            p.addValue("q", text);
            p.addValue("prefix", prefixQuery(text));
            p.addValue("like", "%" + text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            where.add("""
                    (s.tsv_en @@ websearch_to_tsquery('english', :q)
                     or s.tsv_es @@ websearch_to_tsquery('spanish', :q)
                     or (:prefix <> '' and (s.tsv_en @@ to_tsquery('english', :prefix)
                                            or s.tsv_es @@ to_tsquery('spanish', :prefix)))
                     or lower(s.title) like :like
                     or s.title % :q)""");
            rank = """
                    greatest(ts_rank_cd(s.tsv_en, websearch_to_tsquery('english', :q)),
                             ts_rank_cd(s.tsv_es, websearch_to_tsquery('spanish', :q))) * 2
                    + similarity(s.title, :q)""";
            snippet = """
                    ts_headline('english', s.body, websearch_to_tsquery('english', :q),
                      'StartSel=%1$s, StopSel=%1$s, MaxWords=35, MinWords=12, MaxFragments=2, FragmentDelimiter=" … "')"""
                    .formatted(MARK);
        }
        List<String> tags = q.tags().stream().map(t -> t.trim().toLowerCase().replaceFirst("^#", ""))
                .filter(t -> !t.isEmpty()).distinct().toList();
        if (!tags.isEmpty()) {
            p.addValue("tags", tags.toArray(String[]::new));
            where.add(q.tagMode() == TagMode.OR ? "s.tags && cast(:tags as text[])" : "s.tags @> cast(:tags as text[])");
        }
        addEq(where, p, "s.entity_type", "entityType", q.entityType());
        addEq(where, p, "s.type_code", "typeCode", q.typeCode());
        addEq(where, p, "s.severity", "severity", q.severity());
        addEq(where, p, "s.status", "status", q.status());
        if (q.clientId() != null) {
            p.addValue("client", q.clientId());
            where.add("s.client_id = :client");
        }
        if (q.from() != null) {
            p.addValue("from", q.from());
            where.add("s.updated_at >= cast(:from as date)");
        }
        if (q.to() != null) {
            p.addValue("to", q.to().plusDays(1));
            where.add("s.updated_at < cast(:to as date)");
        }
        String filter = String.join(" and ", where);
        p.addValue("limit", size).addValue("offset", page * size);
        String sql = """
                select s.entity_type, s.entity_id, s.assessment_id, s.title, %s as snippet, s.tags, s.severity,
                       s.status, s.type_code, s.updated_at, %s as rank
                from search_entry s
                where %s
                order by rank desc, s.updated_at desc
                limit :limit offset :offset""".formatted(snippet, rank, filter);
        List<SearchHit> hits = jdbc.query(sql, p, (rs, n) -> new SearchHit(rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class), rs.getObject("assessment_id", UUID.class),
                rs.getString("title"), rs.getString("snippet"), toList(rs.getArray("tags")),
                rs.getString("severity"), rs.getString("status"), rs.getString("type_code"),
                rs.getObject("updated_at", Timestamp.class).toInstant(), rs.getDouble("rank")));
        Long total = jdbc.queryForObject("select count(*) from search_entry s where " + filter, p, Long.class);
        return new PageResponse<>(hits, page, size, total == null ? 0 : total);
    }

    public List<TagCount> tags(UUID workspaceId, String prefix) {
        MapSqlParameterSource p = new MapSqlParameterSource("ws", workspaceId)
                .addValue("prefix", (prefix == null ? "" : prefix.trim().toLowerCase().replaceFirst("^#", "")
                        .replace("%", "").replace("_", "")) + "%");
        return jdbc.query("""
                select t.tag, count(*) as n
                from search_entry s, unnest(s.tags) as t(tag)
                where s.workspace_id = :ws and t.tag like :prefix
                group by t.tag order by n desc, t.tag limit 50""", p,
                (rs, n) -> new TagCount(rs.getString("tag"), rs.getLong("n")));
    }

    /** "ram dum" → "ram:* & dum:*" (only letters/digits survive, so it is always valid tsquery syntax). */
    static String prefixQuery(String text) {
        return Arrays.stream(text.toLowerCase().split("[^\\p{L}\\p{N}]+"))
                .filter(t -> t.length() >= 2).limit(8).map(t -> t + ":*").collect(Collectors.joining(" & "));
    }

    private static void addEq(List<String> where, MapSqlParameterSource p, String column, String name, String value) {
        if (value != null && !value.isBlank()) {
            p.addValue(name, value.trim());
            where.add(column + " = :" + name);
        }
    }

    private static List<String> toList(Array array) throws SQLException {
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }

}

package com.stdevsec.atlas.shared;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Stable paging envelope for list endpoints. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.map(mapper).getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements());
    }
}

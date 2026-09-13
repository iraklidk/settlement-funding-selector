package ge.kursi.settlement.api.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable pagination envelope, decoupled from Spring Data's {@link Page} serialisation. */
public record PageResponse<T>(List<T> content,
                              int page,
                              int size,
                              long totalElements,
                              int totalPages,
                              boolean hasNext) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}

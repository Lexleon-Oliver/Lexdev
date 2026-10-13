package net.ddns.lexdev.systempro_api.dto.support;

import java.util.List;

public record DeletedRecordPageResponseDto(
    List<DeletedRecordResponseDto> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static DeletedRecordPageResponseDto of(
        List<DeletedRecordResponseDto> content,
        int page,
        int size,
        long totalElements
    ) {
        int totalPages = totalElements == 0
            ? 0
            : (int) Math.ceil((double) totalElements / size);
        return new DeletedRecordPageResponseDto(
            content,
            page,
            size,
            totalElements,
            totalPages
        );
    }
}
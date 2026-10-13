package net.ddns.lexdev.systempro_api.dto.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DeletedRecordPageResponseDtoTest {

    @Test
    void shouldCalculateTotalPages() {
        DeletedRecordPageResponseDto response = DeletedRecordPageResponseDto.of(
            List.of(), 1, 20, 41
        );

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(41);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void shouldReturnZeroPagesWhenThereAreNoRecords() {
        DeletedRecordPageResponseDto response = DeletedRecordPageResponseDto.of(
            List.of(), 0, 20, 0
        );

        assertThat(response.totalPages()).isZero();
    }
}
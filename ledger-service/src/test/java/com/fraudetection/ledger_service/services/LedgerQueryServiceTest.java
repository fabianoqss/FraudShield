package com.fraudetection.ledger_service.services;

import com.fraudetection.ledger_service.repositories.LedgerEntryRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerQueryServiceTest {

    private final LedgerEntryRepository repository = mock(LedgerEntryRepository.class);
    private final LedgerQueryService service = new LedgerQueryService(repository);

    @ParameterizedTest
    @CsvSource({
            "0, 20, 0, 20",
            "2, 100, 2, 100",
            "0, 10000000, 0, 100",
            "0, 0, 0, 1",
            "-5, -1, 0, 1"
    })
    void pageRequestIsClampedToSafeBounds(int page, int size, int expectedPage, int expectedSize) {
        when(repository.findByAccountId(anyString(), any())).thenReturn(Page.empty());

        service.getEntriesForAccount(UUID.randomUUID(), page, size);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByAccountId(anyString(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(expectedPage);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(expectedSize);
    }
}

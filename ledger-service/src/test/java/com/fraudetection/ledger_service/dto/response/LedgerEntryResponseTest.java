package com.fraudetection.ledger_service.dto.response;

import com.fraudetection.ledger_service.documents.LedgerEntry;
import org.bson.types.Decimal128;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerEntryResponseTest {
    static Stream<Object> amounts() {
        return Stream.of(12.34d, "12.34", new Decimal128(new BigDecimal("12.34")), new BigDecimal("12.34"));
    }

    @ParameterizedTest
    @MethodSource("amounts")
    void convertsMongoAmountsWithoutBinaryFloatingPointArtifacts(Object amount) {
        UUID source = UUID.randomUUID();
        var entry = new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), "TRANSACTION_FLAGGED",
                Map.of("sourceAccountId", source.toString(), "amount", amount, "reason", "Review required"),
                "transaction.flagged", 1, Instant.now());
        var outgoing = LedgerEntryResponse.from(entry, source);
        assertThat(outgoing.amount()).isEqualByComparingTo("12.34");
        assertThat(outgoing.direction()).isEqualTo("OUTGOING");
        assertThat(outgoing.reason()).isEqualTo("Review required");
        assertThat(LedgerEntryResponse.from(entry, UUID.randomUUID()).reason()).isNull();
    }
}

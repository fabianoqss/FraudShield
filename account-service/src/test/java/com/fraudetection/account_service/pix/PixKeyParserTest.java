package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.pix.exceptions.InvalidPixKeyFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PixKeyParserTest {

    @Test
    void emailIsTrimmedAndLowerCased() {
        assertThat(PixKeyParser.parse("  Ana.Souza@Example.COM "))
                .isEqualTo(new ParsedPixKey(PixKeyType.EMAIL, "ana.souza@example.com"));
    }

    @Test
    void cpfKeepsOnlyDigits() {
        assertThat(PixKeyParser.parse(" 529.982.247-25 "))
                .isEqualTo(new ParsedPixKey(PixKeyType.CPF, "52998224725"));
        assertThat(PixKeyParser.parse("52998224725"))
                .isEqualTo(new ParsedPixKey(PixKeyType.CPF, "52998224725"));
    }

    @Test
    void randomKeyIsCanonicalLowerCaseUuid() {
        assertThat(PixKeyParser.parse("3F2504E0-4F89-11D3-9A0C-0305E82C3301"))
                .isEqualTo(new ParsedPixKey(PixKeyType.RANDOM, "3f2504e0-4f89-11d3-9a0c-0305e82c3301"));
    }

    @Test
    void allDigitUuidIsARandomKeyNotACpf() {
        assertThat(PixKeyParser.parse("12345678-1234-1234-1234-123456789012").type()).isEqualTo(PixKeyType.RANDOM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ana", "ana@", "@example.com", "ana@example", "1234567890", "123456789012",
            "529.982.247-2x", "+5511999999999"})
    void invalidFormatsAreRejected(String raw) {
        assertThatThrownBy(() -> PixKeyParser.parse(raw)).isInstanceOf(InvalidPixKeyFormatException.class);
    }

    @Test
    void rejectionTellsWhichFormatsAreAcceptedWithoutEchoingTheKey() {
        assertThatThrownBy(() -> PixKeyParser.parse("ana-secret-typo"))
                .hasMessage("Invalid PIX key format. Use a CPF (11 digits), an e-mail or a random key (UUID).")
                .message().doesNotContain("ana-secret-typo");
    }

    @Test
    void nullIsRejected() {
        assertThatThrownBy(() -> PixKeyParser.parse(null)).isInstanceOf(InvalidPixKeyFormatException.class);
    }
}

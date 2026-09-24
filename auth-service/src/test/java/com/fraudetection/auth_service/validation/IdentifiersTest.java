package com.fraudetection.auth_service.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifiersTest {

    @Test
    void emailIsTrimmedAndLowerCased() {
        assertThat(Identifiers.email("  Ana.Souza@Example.COM ")).isEqualTo("ana.souza@example.com");
    }

    @Test
    void cpfKeepsOnlyDigits() {
        assertThat(Identifiers.cpf("529.982.247-25")).isEqualTo("52998224725");
        assertThat(Identifiers.cpf(" 52998224725 ")).isEqualTo("52998224725");
    }

    @Test
    void nullStaysNull() {
        assertThat(Identifiers.email(null)).isNull();
        assertThat(Identifiers.cpf(null)).isNull();
    }
}

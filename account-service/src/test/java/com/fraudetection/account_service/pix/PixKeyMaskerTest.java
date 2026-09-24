package com.fraudetection.account_service.pix;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PixKeyMaskerTest {

    @Test
    void cpfShowsOnlyTheMiddleSixDigits() {
        assertThat(PixKeyMasker.maskCpf("52998224725")).isEqualTo("***.982.247-**");
        assertThat(PixKeyMasker.maskCpf("529.982.247-25")).isEqualTo("***.982.247-**");
    }

    @Test
    void malformedCpfIsFullyHidden() {
        assertThat(PixKeyMasker.maskCpf("123")).isEqualTo("***");
        assertThat(PixKeyMasker.maskCpf(null)).isEqualTo("***");
    }

    @Test
    void emailKeepsFirstLetterAndDomain() {
        assertThat(PixKeyMasker.mask(PixKeyType.EMAIL, "ana@mail.com")).isEqualTo("a***@mail.com");
    }

    @Test
    void randomKeyIsNotPersonalDataAndStaysVisible() {
        assertThat(PixKeyMasker.mask(PixKeyType.RANDOM, "3f2504e0-4f89-11d3-9a0c-0305e82c3301"))
                .isEqualTo("3f2504e0-4f89-11d3-9a0c-0305e82c3301");
    }

    @Test
    void cpfKeyUsesTheCpfMask() {
        assertThat(PixKeyMasker.mask(PixKeyType.CPF, "52998224725")).isEqualTo("***.982.247-**");
    }
}

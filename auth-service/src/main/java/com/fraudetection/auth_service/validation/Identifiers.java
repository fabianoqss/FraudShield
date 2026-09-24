package com.fraudetection.auth_service.validation;

import java.util.Locale;

/**
 * Canonical forms of the user identifiers, so that the same person always maps to the same
 * stored value: e-mail is trimmed and lower-cased, CPF keeps only its digits.
 */
public final class Identifiers {

    private Identifiers() {
    }

    public static String email(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String cpf(String cpf) {
        return cpf == null ? null : cpf.replaceAll("[^0-9]", "");
    }
}

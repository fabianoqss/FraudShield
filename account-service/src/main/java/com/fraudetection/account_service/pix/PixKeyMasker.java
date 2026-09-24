package com.fraudetection.account_service.pix;

/**
 * Masks PIX keys and CPFs for API responses and logs. Random keys are not personal data and stay visible.
 */
public final class PixKeyMasker {

    private static final String HIDDEN = "***";

    private PixKeyMasker() {
    }

    public static String maskCpf(String cpf) {
        if (cpf == null) {
            return HIDDEN;
        }
        String digits = PixKeyParser.normalizeCpf(cpf);
        if (digits.length() != 11) {
            return HIDDEN;
        }
        return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
    }

    public static String mask(PixKeyType type, String value) {
        return switch (type) {
            case CPF -> maskCpf(value);
            case EMAIL -> maskEmail(value);
            case RANDOM -> value;
        };
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return HIDDEN;
        }
        return email.charAt(0) + HIDDEN + email.substring(at);
    }
}

package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.pix.exceptions.InvalidPixKeyFormatException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detects the type of a PIX key typed by a user and returns its canonical value, so the same key always
 * maps to the same stored value: e-mail trimmed and lower-cased, CPF digits only, random key as a
 * lower-case UUID. Uses the same e-mail and CPF rules as auth-service.
 */
public final class PixKeyParser {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern CPF_FORMAT = Pattern.compile("^[0-9.\\-]+$");
    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private PixKeyParser() {
    }

    public static ParsedPixKey parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidPixKeyFormatException();
        }
        String key = raw.trim();

        if (key.contains("@")) {
            String email = normalizeEmail(key);
            if (!EMAIL_FORMAT.matcher(email).matches()) {
                throw new InvalidPixKeyFormatException();
            }
            return new ParsedPixKey(PixKeyType.EMAIL, email);
        }
        if (UUID_FORMAT.matcher(key).matches()) {
            return new ParsedPixKey(PixKeyType.RANDOM, key.toLowerCase(Locale.ROOT));
        }
        if (CPF_FORMAT.matcher(key).matches()) {
            String cpf = normalizeCpf(key);
            if (cpf.length() == 11) {
                return new ParsedPixKey(PixKeyType.CPF, cpf);
            }
        }
        throw new InvalidPixKeyFormatException();
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeCpf(String cpf) {
        return cpf.replaceAll("[^0-9]", "");
    }
}

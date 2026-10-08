package io.github.ericksoares95.kipay.accounts.account;

import io.github.ericksoares95.kipay.accounts.account.InvalidCpfException.Reason;

/**
 * Brazilian CPF value object. Holds only the 11 normalized digits; {@link #toString()} is masked so the
 * full number never reaches logs. The status at Receita Federal is not checked.
 */
public record Cpf(String value) {

    private static final int LENGTH = 11;

    public Cpf {
        // Re-validates so no invalid instance exists, even via new Cpf(...); Cpf.of also normalizes punctuation.
        validate(value);
    }

    public static Cpf of(String raw) {
        if (raw == null) {
            throw new InvalidCpfException(Reason.FORMAT);
        }
        String digits = raw.strip().replace(".", "").replace("-", "");
        return new Cpf(digits);
    }

    public String masked() {
        return "***." + value.substring(3, 6) + "." + value.substring(6, 9) + "-**";
    }

    @Override
    public String toString() {
        return masked();
    }

    private static void validate(String digits) {
        if (digits == null || digits.length() != LENGTH || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new InvalidCpfException(Reason.FORMAT);
        }
        if (digits.chars().distinct().count() == 1
                || checkDigit(digits, 9) != digits.charAt(9) - '0'
                || checkDigit(digits, 10) != digits.charAt(10) - '0') {
            throw new InvalidCpfException(Reason.CHECK_DIGITS);
        }
    }

    private static int checkDigit(String digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (length + 1 - i);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}

package io.github.ericksoares95.kipay.accounts.account;

/**
 * Raised when a CPF is rejected. The message never carries the CPF (LGPD).
 */
public class InvalidCpfException extends RuntimeException {

    /**
     * Why the CPF was rejected: {@code FORMAT} is a wrong length, absent or non-numeric value;
     * {@code CHECK_DIGITS} is a repeated-digit CPF or wrong check digits.
     */
    public enum Reason {
        FORMAT,
        CHECK_DIGITS
    }

    private final Reason reason;

    public InvalidCpfException(Reason reason) {
        super("Invalid CPF: " + reason);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}

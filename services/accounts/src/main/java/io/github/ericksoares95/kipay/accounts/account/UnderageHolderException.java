package io.github.ericksoares95.kipay.accounts.account;

/**
 * Raised when the holder has not reached the minimum age. The message never carries personal data (LGPD).
 */
public class UnderageHolderException extends RuntimeException {

    public UnderageHolderException() {
        super("Account holder is under the minimum age");
    }
}

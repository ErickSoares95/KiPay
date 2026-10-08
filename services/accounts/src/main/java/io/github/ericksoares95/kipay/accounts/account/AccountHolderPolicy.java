package io.github.ericksoares95.kipay.accounts.account;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

/**
 * Acceptance rules for an account holder. The civil date of the request is taken in {@link #CIVIL_ZONE}, so the
 * day changes at midnight in Brasília and not in UTC.
 */
@Component
public class AccountHolderPolicy {

    public static final int MINIMUM_AGE = 18;
    public static final ZoneId CIVIL_ZONE = ZoneId.of("America/Sao_Paulo");

    private final Clock clock;

    public AccountHolderPolicy(Clock clock) {
        this.clock = clock.withZone(CIVIL_ZONE);
    }

    /** Civil date of "today" in {@link #CIVIL_ZONE}. */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(CIVIL_ZONE));
    }

    /**
     * @throws UnderageHolderException if the holder has not completed {@link #MINIMUM_AGE} years on the request date
     */
    public void requireMinimumAge(LocalDate birthDate) {
        if (today(clock).isBefore(eighteenthBirthday(birthDate))) {
            throw new UnderageHolderException();
        }
    }

    /** Someone born on 29/02 completes the years of a non-leap year on 01/03 (not on 28/02). */
    static LocalDate eighteenthBirthday(LocalDate birthDate) {
        LocalDate birthday = birthDate.plusYears(MINIMUM_AGE);
        boolean bornOnLeapDay = birthDate.getMonth() == Month.FEBRUARY && birthDate.getDayOfMonth() == 29;
        if (bornOnLeapDay && !birthday.isLeapYear()) {
            return LocalDate.of(birthday.getYear(), Month.MARCH, 1);
        }
        return birthday;
    }
}

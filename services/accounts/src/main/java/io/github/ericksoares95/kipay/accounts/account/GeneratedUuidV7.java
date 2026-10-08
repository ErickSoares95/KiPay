package io.github.ericksoares95.kipay.accounts.account;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.hibernate.annotations.IdGeneratorType;

/** Marks an entity id to be generated as a UUID v7 by the application. */
@IdGeneratorType(UuidV7Generator.class)
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface GeneratedUuidV7 {
}

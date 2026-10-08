package io.github.ericksoares95.kipay.accounts.account;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link Cpf} as its 11 normalized digits. */
@Converter
public class CpfConverter implements AttributeConverter<Cpf, String> {

    @Override
    public String convertToDatabaseColumn(Cpf attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Cpf convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new Cpf(dbData);
    }
}

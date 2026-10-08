package io.github.ericksoares95.kipay.accounts.account;

import java.util.EnumSet;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

/** Hibernate id generator behind {@link GeneratedUuidV7}. */
public class UuidV7Generator implements BeforeExecutionGenerator {

    public UuidV7Generator(GeneratedUuidV7 annotation) {
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
            EventType eventType) {
        return UuidV7.generate();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }
}

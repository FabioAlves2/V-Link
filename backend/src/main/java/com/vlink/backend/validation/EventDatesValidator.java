package com.vlink.backend.validation;

import com.vlink.backend.model.Event;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EventDatesValidator implements ConstraintValidator<ValidEventDates, Event> {

    @Override
    public boolean isValid(Event event, ConstraintValidatorContext context) {
        if (event.getStartDate() == null || event.getEndDate() == null) {
            return true; // @NotNull on the individual fields already reports this
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        // A regra "não pode começar no passado" só se aplica à CRIAÇÃO de um evento —
        // é validada explicitamente em EventController.create(), não aqui, porque este
        // constraint corre em todos os @Valid binds, incluindo o PUT usado para editar/encerrar
        // um evento já a decorrer ou já terminado (cujo startDate é, nesse ponto, sempre passado).
        if (event.getEndDate().isBefore(event.getStartDate())) {
            context.buildConstraintViolationWithTemplate("A data de fim não pode ser anterior à data de início.")
                .addPropertyNode("endDate")
                .addConstraintViolation();
            valid = false;
        }
        return valid;
    }
}

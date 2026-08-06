package com.vlink.backend.validation;

import com.vlink.backend.model.Event;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDateTime;

public class EventDatesValidator implements ConstraintValidator<ValidEventDates, Event> {

    @Override
    public boolean isValid(Event event, ConstraintValidatorContext context) {
        if (event.getStartDate() == null || event.getEndDate() == null) {
            return true; // @NotNull on the individual fields already reports this
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if (event.getStartDate().isBefore(LocalDateTime.now())) {
            context.buildConstraintViolationWithTemplate("A data de início não pode ser no passado.")
                .addPropertyNode("startDate")
                .addConstraintViolation();
            valid = false;
        }
        if (event.getEndDate().isBefore(event.getStartDate())) {
            context.buildConstraintViolationWithTemplate("A data de fim não pode ser anterior à data de início.")
                .addPropertyNode("endDate")
                .addConstraintViolation();
            valid = false;
        }
        return valid;
    }
}

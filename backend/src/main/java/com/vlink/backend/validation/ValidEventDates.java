package com.vlink.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EventDatesValidator.class)
public @interface ValidEventDates {
    String message() default "Datas do evento inválidas.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private QueueRequest queue(String name) {
        QueueRequest q = new QueueRequest();
        q.setName(name);
        return q;
    }

    @Test
    void colaConNombreVacioEsInvalida() {
        assertFalse(validator.validate(queue("")).isEmpty());
        assertFalse(validator.validate(queue("   ")).isEmpty());
        assertFalse(validator.validate(queue(null)).isEmpty());
    }

    @Test
    void colaConCaracteresInvalidosEsInvalida() {
        assertFalse(validator.validate(queue("mi cola")).isEmpty());
        assertFalse(validator.validate(queue("cola/../x")).isEmpty());
        assertFalse(validator.validate(queue(".oculta")).isEmpty());
    }

    @Test
    void colaValidaPasa() {
        assertTrue(validator.validate(queue("pedidos360.reportes.q")).isEmpty());
    }

    @Test
    void ttlYMaxLengthDebenSerPositivos() {
        QueueRequest q = queue("ok");
        q.setMessageTtlMs(0L);
        q.setMaxLength(-5);
        assertEquals2(2, validator.validate(q).size());
    }

    @Test
    void exchangeRequiereTipo() {
        ExchangeRequest e = new ExchangeRequest();
        e.setName("mi.exchange");
        assertFalse(validator.validate(e).isEmpty());
        e.setType(ExchangeType.TOPIC);
        assertTrue(validator.validate(e).isEmpty());
    }

    @Test
    void bindingRequiereExchangeYCola() {
        assertFalse(validator.validate(new BindingRequest("", "", "")).isEmpty());
        assertTrue(validator.validate(new BindingRequest("ex", "q", "a.b")).isEmpty());
    }

    private static void assertEquals2(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}

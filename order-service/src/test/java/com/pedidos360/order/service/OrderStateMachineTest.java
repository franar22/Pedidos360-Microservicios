package com.pedidos360.order.service;

import com.pedidos360.order.model.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStateMachineTest {

    @Test
    void flujoNormalEsValido() {
        assertDoesNotThrow(() -> OrderStateMachine.assertTransition(OrderStatus.CREADO, OrderStatus.ACEPTADO));
        assertDoesNotThrow(() -> OrderStateMachine.assertTransition(OrderStatus.ACEPTADO, OrderStatus.EN_PREPARACION));
        assertDoesNotThrow(() -> OrderStateMachine.assertTransition(OrderStatus.EN_PREPARACION, OrderStatus.DESPACHADO));
        assertDoesNotThrow(() -> OrderStateMachine.assertTransition(OrderStatus.DESPACHADO, OrderStatus.ENTREGADO));
    }

    @Test
    void noSePuedeDespacharSinAceptar() {
        assertThrows(IllegalStateException.class,
                () -> OrderStateMachine.assertTransition(OrderStatus.CREADO, OrderStatus.DESPACHADO));
    }

    @Test
    void estadosFinalesNoTienenSalida() {
        assertThrows(IllegalStateException.class,
                () -> OrderStateMachine.assertTransition(OrderStatus.ENTREGADO, OrderStatus.CANCELADO));
        assertThrows(IllegalStateException.class,
                () -> OrderStateMachine.assertTransition(OrderStatus.CANCELADO, OrderStatus.CREADO));
    }

    @Test
    void stockSeDescuentaSoloAlAceptar() {
        assertTrue(OrderStateMachine.reservesStock(OrderStatus.ACEPTADO));
        assertFalse(OrderStateMachine.reservesStock(OrderStatus.EN_PREPARACION));
    }

    @Test
    void stockSeDevuelveSoloSiSeCancelaDespuesDeAceptar() {
        assertTrue(OrderStateMachine.releasesStock(OrderStatus.ACEPTADO, OrderStatus.CANCELADO));
        assertTrue(OrderStateMachine.releasesStock(OrderStatus.DESPACHADO, OrderStatus.CANCELADO));
        assertFalse(OrderStateMachine.releasesStock(OrderStatus.CREADO, OrderStatus.CANCELADO));
        assertFalse(OrderStateMachine.releasesStock(OrderStatus.ACEPTADO, OrderStatus.EN_PREPARACION));
    }
}

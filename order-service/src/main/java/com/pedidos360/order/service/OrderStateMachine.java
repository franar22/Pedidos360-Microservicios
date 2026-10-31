package com.pedidos360.order.service;

import com.pedidos360.order.model.OrderStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Maquina de estados del pedido (identica a la del monolito):
 *   CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO
 *   con CANCELADO posible desde cualquier estado previo a DESPACHADO inclusive (no desde ENTREGADO).
 * Garantiza la regla "no se puede DESPACHAR sin ACEPTAR".
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.CREADO, EnumSet.of(OrderStatus.ACEPTADO, OrderStatus.CANCELADO));
        ALLOWED.put(OrderStatus.ACEPTADO, EnumSet.of(OrderStatus.EN_PREPARACION, OrderStatus.CANCELADO));
        ALLOWED.put(OrderStatus.EN_PREPARACION, EnumSet.of(OrderStatus.DESPACHADO, OrderStatus.CANCELADO));
        ALLOWED.put(OrderStatus.DESPACHADO, EnumSet.of(OrderStatus.ENTREGADO));
        ALLOWED.put(OrderStatus.ENTREGADO, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELADO, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static void assertTransition(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowedNext = ALLOWED.getOrDefault(from, EnumSet.noneOf(OrderStatus.class));
        if (!allowedNext.contains(to)) {
            throw new IllegalStateException("Transicion invalida: no se puede pasar de " + from + " a " + to);
        }
    }

    /** El stock se descuenta al ACEPTAR el pedido. */
    public static boolean reservesStock(OrderStatus to) {
        return to == OrderStatus.ACEPTADO;
    }

    /** Si se cancela un pedido que ya habia descontado stock, hay que devolverlo. */
    public static boolean releasesStock(OrderStatus from, OrderStatus to) {
        boolean stockYaDescontado = from == OrderStatus.ACEPTADO
                || from == OrderStatus.EN_PREPARACION
                || from == OrderStatus.DESPACHADO;
        return to == OrderStatus.CANCELADO && stockYaDescontado;
    }
}

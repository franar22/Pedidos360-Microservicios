package com.pedidos360.rabbitadmin.service;

import com.pedidos360.rabbitadmin.config.RabbitNamesProperties;
import com.pedidos360.rabbitadmin.dto.QueueRequest;
import com.pedidos360.rabbitadmin.exception.ConflictException;
import com.pedidos360.rabbitadmin.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceTest {

    @Mock
    private RabbitAdmin rabbitAdmin;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private RabbitManagementClient management;

    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        RabbitNamesProperties names = new RabbitNamesProperties(
                new RabbitNamesProperties.Exchanges("ex.direct", "ex.topic", "ex.dlx"),
                new RabbitNamesProperties.Queues("q.notif", "q.tickets", "q.inv"),
                new RabbitNamesProperties.RoutingKeys("ticket", "pedido.#", "pedido.creado", "pedido.estado.", "inv.#", "inv.bajo"),
                ".dlq");
        service = new RabbitAdminService(rabbitAdmin, rabbitTemplate, management, names);
    }

    private QueueRequest request(String name) {
        QueueRequest r = new QueueRequest();
        r.setName(name);
        return r;
    }

    @Test
    void createQueue_conDlq_declaraColaConArgumentosDeDeadLetter() {
        when(rabbitAdmin.getQueueInfo("reportes.q"))
                .thenReturn(null, new QueueInformation("reportes.q", 0, 0));

        service.createQueue(request("reportes.q"));

        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin, org.mockito.Mockito.times(2)).declareQueue(captor.capture());
        Queue main = captor.getAllValues().stream()
                .filter(q -> q.getName().equals("reportes.q")).findFirst().orElseThrow();
        assertEquals("ex.dlx", main.getArguments().get("x-dead-letter-exchange"));
        assertEquals("reportes.q", main.getArguments().get("x-dead-letter-routing-key"));
        assertEquals(true, captor.getAllValues().stream().anyMatch(q -> q.getName().equals("reportes.q.dlq")));
    }

    @Test
    void createQueue_existente_lanzaConflict() {
        when(rabbitAdmin.getQueueInfo("existe")).thenReturn(new QueueInformation("existe", 0, 0));

        assertThrows(ConflictException.class, () -> service.createQueue(request("existe")));
        verify(rabbitAdmin, never()).declareQueue(any(Queue.class));
    }

    @Test
    void createQueue_nombreReservado_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.createQueue(request("amq.interna")));
    }

    @Test
    void deleteQueue_inexistente_lanzaNotFound() {
        when(rabbitAdmin.getQueueInfo("nada")).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.deleteQueue("nada", false, false, false));
        verify(rabbitAdmin, never()).deleteQueue(any(String.class), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }
}

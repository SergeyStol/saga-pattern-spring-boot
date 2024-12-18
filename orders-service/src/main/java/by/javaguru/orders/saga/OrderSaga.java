package by.javaguru.orders.saga;

import by.javaguru.core.dto.commands.RejectReservedProductCommand;
import by.javaguru.core.dto.events.PaymentFailedEvent;
import by.javaguru.core.dto.events.ProductReservationFailedEvent;
import by.javaguru.core.dto.events.ReserveProductRejectedEvent;
import by.javaguru.orders.service.OrderHistoryService;
import by.javaguru.core.dto.commands.ApproveOrderCommand;
import by.javaguru.core.dto.commands.ProcessPaymentCommand;
import by.javaguru.core.dto.commands.ReserveProductCommand;
import by.javaguru.core.dto.events.OrderApprovedEvent;
import by.javaguru.core.dto.events.OrderCreatedEvent;
import by.javaguru.core.dto.events.PaymentProcessedEvent;
import by.javaguru.core.dto.events.ProductReservedEvent;
import by.javaguru.core.types.OrderStatus;
import by.javaguru.core.dto.commands.RejectOrderCommand;
import by.javaguru.core.dto.events.OrderRejectedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@KafkaListener(topics = {
        "${orders.events.topic.name}",
        "${products.events.topic.name}",
        "${payments.events.topic.name}"
})
public class OrderSaga {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String productsCommandsTopicName;
    private final OrderHistoryService orderHistoryService;
    private final String paymentsCommandsTopicName;
    private final String ordersCommandsTopicName;

    public OrderSaga(KafkaTemplate<String, Object> kafkaTemplate,
                     @Value("${products.commands.topic.name}") String productsCommandsTopicName,
                     OrderHistoryService orderHistoryService,
                     @Value("${payments.commands.topic.name}") String paymentsCommandsTopicName,
                     @Value("${orders.commands.topic.name}") String ordersCommandsTopicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.productsCommandsTopicName = productsCommandsTopicName;
        this.orderHistoryService = orderHistoryService;
        this.paymentsCommandsTopicName = paymentsCommandsTopicName;
        this.ordersCommandsTopicName = ordersCommandsTopicName;
    }

    @KafkaHandler
    public void handleEvent(@Payload OrderCreatedEvent event) {

        ReserveProductCommand command = new ReserveProductCommand(
                event.getProductId(),
                event.getProductQuantity(),
                event.getOrderId()
        );

        kafkaTemplate.send(productsCommandsTopicName, command);
        orderHistoryService.add(event.getOrderId(), OrderStatus.CREATED);
    }

    @KafkaHandler
    public void handleEvent(@Payload ProductReservedEvent event) {

        ProcessPaymentCommand processPaymentCommand = new ProcessPaymentCommand(event.getOrderId(),
                event.getProductId(), event.getProductPrice(), event.getProductQuantity());
        kafkaTemplate.send(paymentsCommandsTopicName, processPaymentCommand);
    }

    @KafkaHandler
    public void handleEvent(@Payload PaymentProcessedEvent event) {

        ApproveOrderCommand approveOrderCommand = new ApproveOrderCommand(event.getOrderId());
        kafkaTemplate.send(ordersCommandsTopicName, approveOrderCommand);
    }

    @KafkaHandler
    public void handleEvent(@Payload OrderApprovedEvent event) {
        orderHistoryService.add(event.getOrderId(), OrderStatus.APPROVED);
    }

    @KafkaHandler
    public void handleEvent(@Payload OrderRejectedEvent event) {
        orderHistoryService.add(event.getOrderId(), OrderStatus.REJECTED);
    }

    @KafkaHandler
    public void handleEvent(@Payload ProductReservationFailedEvent event) {
        rejectOrder(event.getOrderId());
    }

    @KafkaHandler
    public void handleEvent(@Payload PaymentFailedEvent event) {
        var rejectReservedProductCommand = new RejectReservedProductCommand();
        rejectReservedProductCommand.setOrderId(event.getOrderId());
        rejectReservedProductCommand.setProductId(event.getProductId());
        rejectReservedProductCommand.setProductQuantity(event.getProductQuantity());
        kafkaTemplate.send(productsCommandsTopicName, rejectReservedProductCommand);
    }

    @KafkaHandler
    public void handleEvent(@Payload ReserveProductRejectedEvent event) {
        rejectOrder(event.getOrderId());
    }

    private void rejectOrder(UUID orderId) {
        var rejectOrderCommand = new RejectOrderCommand(orderId);
        kafkaTemplate.send(ordersCommandsTopicName, rejectOrderCommand);
    }

}

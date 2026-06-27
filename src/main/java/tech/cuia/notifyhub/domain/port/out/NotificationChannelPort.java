package tech.cuia.notifyhub.domain.port.out;

import tech.cuia.notifyhub.domain.model.ChannelType;
import tech.cuia.notifyhub.domain.model.Notification;

public interface NotificationChannelPort {

    ChannelType supportedChannel();

    /**
     * Entrega a notificação via o canal concreto.
     * Lança {@link tech.cuia.notifyhub.domain.exception.NotificationDeliveryException} em caso de falha,
     * permitindo que o chamador decida entre retry e DLQ sem conhecer detalhes do canal.
     */
    void deliver(Notification notification);
}

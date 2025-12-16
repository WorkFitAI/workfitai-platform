package org.workfitai.notificationservice.strategy;

import org.workfitai.notificationservice.dto.kafka.NotificationEvent;

/**
 * Strategy Pattern: Defines interface for different notification processing
 * strategies.
 * Each strategy handles a specific type of notification with custom logic.
 */
public interface NotificationStrategy {

    /**
     * Check if this strategy can handle the given event.
     */
    boolean canHandle(NotificationEvent event);

    /**
     * Process the notification event.
     * Returns true if processing was successful.
     */
    boolean process(NotificationEvent event);

    /**
     * Get strategy priority (lower = higher priority).
     * Used when multiple strategies can handle the same event.
     */
    default int getPriority() {
        return 100;
    }
}

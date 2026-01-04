package org.workfitai.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.strategy.NotificationStrategy;

import java.util.Comparator;
import java.util.List;

/**
 * Chain of Responsibility Pattern + Strategy Pattern.
 * Routes notifications to appropriate strategy based on priority and matching
 * criteria.
 */
@Service
@Slf4j
public class NotificationOrchestrator {

    private final List<NotificationStrategy> strategies;

    public NotificationOrchestrator(List<NotificationStrategy> strategies) {
        // Sort strategies by priority (lower number = higher priority)
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(NotificationStrategy::getPriority))
                .toList();

        log.info("Initialized NotificationOrchestrator with {} strategies:", strategies.size());
        strategies.forEach(s -> log.info("  - {} (priority: {})",
                s.getClass().getSimpleName(), s.getPriority()));
    }

    /**
     * Process notification by finding and executing the appropriate strategy.
     * Uses Chain of Responsibility to try strategies in priority order.
     */
    public boolean process(NotificationEvent event) {
        if (event == null) {
            log.warn("Cannot process null notification event");
            return false;
        }

        log.debug("Processing notification: type={}, template={}, to={}",
                event.getEventType(), event.getTemplateType(), event.getRecipientEmail());

        // Find first matching strategy
        for (NotificationStrategy strategy : strategies) {
            if (strategy.canHandle(event)) {
                log.debug("Using strategy: {} for event: {}",
                        strategy.getClass().getSimpleName(), event.getEventType());
                try {
                    return strategy.process(event);
                } catch (Exception e) {
                    log.error("Strategy {} failed for event {}: {}",
                            strategy.getClass().getSimpleName(),
                            event.getEventId(),
                            e.getMessage(), e);
                    // Continue to next strategy on failure
                }
            }
        }

        log.warn("No strategy matched for event: {}", event.getEventType());
        return false;
    }

    /**
     * Get all registered strategies (for monitoring/debugging).
     */
    public List<String> getRegisteredStrategies() {
        return strategies.stream()
                .map(s -> s.getClass().getSimpleName() + " (priority: " + s.getPriority() + ")")
                .toList();
    }
}

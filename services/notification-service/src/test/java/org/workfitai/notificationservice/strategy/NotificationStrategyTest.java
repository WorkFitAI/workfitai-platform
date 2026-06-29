package org.workfitai.notificationservice.strategy;

import org.junit.jupiter.api.Test;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationStrategyTest {

    @Test
    void getPriority_defaultsTo100_whenNotOverridden() {
        NotificationStrategy strategy = new NotificationStrategy() {
            @Override
            public boolean canHandle(NotificationEvent event) {
                return false;
            }

            @Override
            public boolean process(NotificationEvent event) {
                return false;
            }
        };

        assertThat(strategy.getPriority()).isEqualTo(100);
    }
}

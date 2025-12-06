package org.workfitai.notificationservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.model.NotificationDocument;
import org.workfitai.notificationservice.repository.NotificationRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationPersistenceService {

    private final NotificationRepository repository;

    public NotificationDocument save(NotificationEvent event, boolean delivered, String error) {
        NotificationDocument doc = NotificationDocument.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .timestamp(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .recipientEmail(event.getRecipientEmail())
                .recipientRole(event.getRecipientRole())
                .subject(event.getSubject())
                .content(event.getContent())
                .metadata(event.getMetadata())
                .delivered(delivered)
                .error(error)
                .build();
        return repository.save(doc);
    }

    public List<NotificationDocument> latest(int limit) {
        int pageSize = Math.max(1, Math.min(limit, 200));
        return repository.findAll(PageRequest.of(0, pageSize,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "timestamp")))
                .getContent();
    }
}

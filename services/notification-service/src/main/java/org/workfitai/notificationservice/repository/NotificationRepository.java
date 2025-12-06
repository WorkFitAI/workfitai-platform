package org.workfitai.notificationservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.notificationservice.model.NotificationDocument;

@Repository
public interface NotificationRepository extends MongoRepository<NotificationDocument, String> {
}

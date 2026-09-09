package com.fraudetection.notification_service.services;

import com.fraudetection.notification_service.dto.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    public void notify(NotificationRequest request) {
        if (request.reason() != null) {
            log.info("[NOTIFICATION] {} - transaction {} for account {} amount R$ {} (reason: {})",
                    request.status(), request.transactionId(), request.sourceAccountId(),
                    request.amount(), request.reason());
        } else {
            log.info("[NOTIFICATION] {} - transaction {} for account {} amount R$ {}",
                    request.status(), request.transactionId(), request.sourceAccountId(),
                    request.amount());
        }
    }
}

package com.aisa.audit;

import com.aisa.audit.messaging.AuditMessagingProperties;
import com.aisa.audit.recording.AuditRecordingProperties;
import com.aisa.audit.retention.AuditRetentionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        AuditRecordingProperties.class,
        AuditMessagingProperties.class,
        AuditRetentionProperties.class
})
public class AuditServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}

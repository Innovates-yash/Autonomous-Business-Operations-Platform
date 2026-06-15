package com.aisa.commons.backup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BackupProperties} validation and clamping logic.
 *
 * <p>Verifies that the configuration properties enforce the backup policy constraints
 * defined in Requirement 28:
 * <ul>
 *   <li>28.1 — Backup interval must not exceed 24 hours</li>
 *   <li>28.2 — Retention must be at least 30 days</li>
 *   <li>28.4 — Retry count is bounded (1–10)</li>
 *   <li>28.5 — Restore timeout is bounded (1–120 minutes)</li>
 * </ul>
 */
class BackupPropertiesTest {

    private BackupProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BackupProperties();
    }

    // -------------------------------------------------------------------------
    // Default values
    // -------------------------------------------------------------------------

    @Test
    void defaultIntervalIs24Hours() {
        assertThat(properties.getIntervalHours()).isEqualTo(24);
    }

    @Test
    void defaultRetentionIs30Days() {
        assertThat(properties.getRetentionDays()).isEqualTo(30);
    }

    @Test
    void defaultMaxRetriesIs3() {
        assertThat(properties.getMaxRetries()).isEqualTo(3);
    }

    @Test
    void defaultRestoreTimeoutIs60Minutes() {
        assertThat(properties.getRestoreTimeoutMinutes()).isEqualTo(60);
    }

    @Test
    void defaultStoragePathIsBackups() {
        assertThat(properties.getStoragePath()).isEqualTo("/backups");
    }

    @Test
    void defaultCronExpressionIsDailyAt2AM() {
        assertThat(properties.getCronExpression()).isEqualTo("0 2 * * *");
    }

    // -------------------------------------------------------------------------
    // Interval clamping (Req 28.1: ≤24h)
    // -------------------------------------------------------------------------

    @Test
    void intervalAbove24IsClampedTo24() {
        properties.setIntervalHours(48);
        assertThat(properties.getIntervalHours()).isEqualTo(24);
    }

    @Test
    void intervalBelow1IsClampedTo1() {
        properties.setIntervalHours(0);
        assertThat(properties.getIntervalHours()).isEqualTo(1);
    }

    @Test
    void intervalWithinRangeIsAccepted() {
        properties.setIntervalHours(12);
        assertThat(properties.getIntervalHours()).isEqualTo(12);
    }

    // -------------------------------------------------------------------------
    // Retention enforcement (Req 28.2: ≥30 days)
    // -------------------------------------------------------------------------

    @Test
    void retentionBelow30IsRaisedTo30() {
        properties.setRetentionDays(7);
        assertThat(properties.getRetentionDays()).isEqualTo(30);
    }

    @Test
    void retentionAtExactly30IsAccepted() {
        properties.setRetentionDays(30);
        assertThat(properties.getRetentionDays()).isEqualTo(30);
    }

    @Test
    void retentionAbove30IsAccepted() {
        properties.setRetentionDays(90);
        assertThat(properties.getRetentionDays()).isEqualTo(90);
    }

    // -------------------------------------------------------------------------
    // Max retries enforcement (Req 28.4: retry up to 3x)
    // -------------------------------------------------------------------------

    @Test
    void retriesBelow1AreClampedTo1() {
        properties.setMaxRetries(0);
        assertThat(properties.getMaxRetries()).isEqualTo(1);
    }

    @Test
    void retriesAbove10AreClampedTo10() {
        properties.setMaxRetries(50);
        assertThat(properties.getMaxRetries()).isEqualTo(10);
    }

    @Test
    void retriesWithinRangeAreAccepted() {
        properties.setMaxRetries(5);
        assertThat(properties.getMaxRetries()).isEqualTo(5);
    }

    @Test
    void defaultRetryValueOf3IsWithinRange() {
        // Verifies the default aligns with Req 28.4 (3 retries)
        assertThat(properties.getMaxRetries()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Restore timeout enforcement (Req 28.5: ≤60 min)
    // -------------------------------------------------------------------------

    @Test
    void restoreTimeoutBelow1IsClampedTo1() {
        properties.setRestoreTimeoutMinutes(0);
        assertThat(properties.getRestoreTimeoutMinutes()).isEqualTo(1);
    }

    @Test
    void restoreTimeoutAbove120IsClampedTo120() {
        properties.setRestoreTimeoutMinutes(200);
        assertThat(properties.getRestoreTimeoutMinutes()).isEqualTo(120);
    }

    @Test
    void restoreTimeoutWithinRangeIsAccepted() {
        properties.setRestoreTimeoutMinutes(45);
        assertThat(properties.getRestoreTimeoutMinutes()).isEqualTo(45);
    }

    // -------------------------------------------------------------------------
    // Null safety for string properties
    // -------------------------------------------------------------------------

    @Test
    void nullStoragePathDefaultsToBackups() {
        properties.setStoragePath(null);
        assertThat(properties.getStoragePath()).isEqualTo("/backups");
    }

    @Test
    void nullS3BucketDefaultsToEmpty() {
        properties.setS3Bucket(null);
        assertThat(properties.getS3Bucket()).isEmpty();
    }

    @Test
    void nullS3EndpointDefaultsToEmpty() {
        properties.setS3Endpoint(null);
        assertThat(properties.getS3Endpoint()).isEmpty();
    }

    @Test
    void nullAlertWebhookDefaultsToEmpty() {
        properties.setAlertWebhookUrl(null);
        assertThat(properties.getAlertWebhookUrl()).isEmpty();
    }

    @Test
    void nullCronExpressionDefaultsToDaily() {
        properties.setCronExpression(null);
        assertThat(properties.getCronExpression()).isEqualTo("0 2 * * *");
    }

    // -------------------------------------------------------------------------
    // Convenience methods
    // -------------------------------------------------------------------------

    @Test
    void s3EnabledWhenBucketConfigured() {
        properties.setS3Bucket("s3://my-bucket/backups");
        assertThat(properties.isS3Enabled()).isTrue();
    }

    @Test
    void s3DisabledWhenBucketEmpty() {
        properties.setS3Bucket("");
        assertThat(properties.isS3Enabled()).isFalse();
    }

    @Test
    void s3DisabledWhenBucketBlank() {
        properties.setS3Bucket("   ");
        assertThat(properties.isS3Enabled()).isFalse();
    }

    @Test
    void alertingEnabledWhenWebhookConfigured() {
        properties.setAlertWebhookUrl("https://hooks.slack.com/services/xxx");
        assertThat(properties.isAlertingEnabled()).isTrue();
    }

    @Test
    void alertingDisabledWhenWebhookEmpty() {
        properties.setAlertWebhookUrl("");
        assertThat(properties.isAlertingEnabled()).isFalse();
    }
}

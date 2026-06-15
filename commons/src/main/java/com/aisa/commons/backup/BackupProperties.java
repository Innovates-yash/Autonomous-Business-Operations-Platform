package com.aisa.commons.backup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the automated backup and restore system.
 *
 * <p>Maps to {@code aisa.backup.*} in application.yml. Documents and enforces the
 * operational parameters required by Requirement 28:
 * <ul>
 *   <li>28.1 — Backup interval ≤24 hours (default: 24h)</li>
 *   <li>28.2 — Retention period ≥30 days (default: 30 days)</li>
 *   <li>28.4 — Retry up to 3 times on failure with Admin alert</li>
 *   <li>28.5 — Restore must complete within 60 minutes</li>
 * </ul>
 *
 * <p>These properties are consumed by the Kubernetes CronJob configuration and backup/restore
 * scripts. They can also be used by any Java-based monitoring or administration component that
 * needs to reference backup policy values.
 *
 * <p>Example configuration in application.yml:
 * <pre>
 * aisa:
 *   backup:
 *     interval-hours: 24
 *     retention-days: 30
 *     max-retries: 3
 *     restore-timeout-minutes: 60
 *     storage-path: /backups
 *     s3-bucket: ""
 *     s3-endpoint: ""
 *     alert-webhook-url: ""
 *     cron-expression: "0 2 * * *"
 * </pre>
 *
 * @see BackupAutoConfiguration
 */
@ConfigurationProperties(prefix = "aisa.backup")
public class BackupProperties {

    /** Maximum interval between backups in hours. Must be ≤24 (Req 28.1). Default: 24. */
    private int intervalHours = 24;

    /** Minimum retention of completed backups in days. Must be ≥30 (Req 28.2). Default: 30. */
    private int retentionDays = 30;

    /** Maximum number of retry attempts on backup failure (Req 28.4). Default: 3. */
    private int maxRetries = 3;

    /** Maximum allowed duration for a restore operation in minutes (Req 28.5). Default: 60. */
    private int restoreTimeoutMinutes = 60;

    /** Local directory path for storing backup files. Default: /backups. */
    private String storagePath = "/backups";

    /** S3-compatible bucket URI for remote backup storage. Empty means local-only. */
    private String s3Bucket = "";

    /** S3 endpoint URL for non-AWS providers. Empty uses the default AWS endpoint. */
    private String s3Endpoint = "";

    /** Webhook URL for sending Admin alerts on backup/restore failure. */
    private String alertWebhookUrl = "";

    /**
     * Cron expression for the Kubernetes CronJob schedule.
     * Default: "0 2 * * *" (daily at 02:00 UTC).
     */
    private String cronExpression = "0 2 * * *";

    // -------------------------------------------------------------------------
    // Getters and setters with validation enforcement
    // -------------------------------------------------------------------------

    public int getIntervalHours() {
        return intervalHours;
    }

    /**
     * Sets the backup interval. Values above 24 are clamped to 24 (Req 28.1: ≤24h).
     * Values below 1 are set to 1.
     */
    public void setIntervalHours(int intervalHours) {
        if (intervalHours > 24) {
            this.intervalHours = 24;
        } else if (intervalHours < 1) {
            this.intervalHours = 1;
        } else {
            this.intervalHours = intervalHours;
        }
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    /**
     * Sets the retention period. Values below 30 are raised to 30 (Req 28.2: ≥30 days).
     */
    public void setRetentionDays(int retentionDays) {
        if (retentionDays < 30) {
            this.retentionDays = 30;
        } else {
            this.retentionDays = retentionDays;
        }
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Sets the maximum retry count. Values below 1 are set to 1. Values above 10 are
     * clamped to 10 to prevent excessive retry loops.
     */
    public void setMaxRetries(int maxRetries) {
        if (maxRetries < 1) {
            this.maxRetries = 1;
        } else if (maxRetries > 10) {
            this.maxRetries = 10;
        } else {
            this.maxRetries = maxRetries;
        }
    }

    public int getRestoreTimeoutMinutes() {
        return restoreTimeoutMinutes;
    }

    /**
     * Sets the restore timeout. Values below 1 are set to 1. Values above 120 are
     * clamped to 120 minutes.
     */
    public void setRestoreTimeoutMinutes(int restoreTimeoutMinutes) {
        if (restoreTimeoutMinutes < 1) {
            this.restoreTimeoutMinutes = 1;
        } else if (restoreTimeoutMinutes > 120) {
            this.restoreTimeoutMinutes = 120;
        } else {
            this.restoreTimeoutMinutes = restoreTimeoutMinutes;
        }
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath != null ? storagePath : "/backups";
    }

    public String getS3Bucket() {
        return s3Bucket;
    }

    public void setS3Bucket(String s3Bucket) {
        this.s3Bucket = s3Bucket != null ? s3Bucket : "";
    }

    public String getS3Endpoint() {
        return s3Endpoint;
    }

    public void setS3Endpoint(String s3Endpoint) {
        this.s3Endpoint = s3Endpoint != null ? s3Endpoint : "";
    }

    public String getAlertWebhookUrl() {
        return alertWebhookUrl;
    }

    public void setAlertWebhookUrl(String alertWebhookUrl) {
        this.alertWebhookUrl = alertWebhookUrl != null ? alertWebhookUrl : "";
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression != null ? cronExpression : "0 2 * * *";
    }

    // -------------------------------------------------------------------------
    // Convenience methods
    // -------------------------------------------------------------------------

    /** Returns true if S3 remote storage is configured. */
    public boolean isS3Enabled() {
        return s3Bucket != null && !s3Bucket.isBlank();
    }

    /** Returns true if an alert webhook is configured. */
    public boolean isAlertingEnabled() {
        return alertWebhookUrl != null && !alertWebhookUrl.isBlank();
    }
}

package com.aisa.commons.observability;

import org.springframework.beans.factory.config.BeanPostProcessor;
import zipkin2.reporter.BytesMessageSender;

/**
 * Wraps any {@link BytesMessageSender} bean (the Zipkin span exporter) in a
 * {@link RetryingBytesMessageSender} so telemetry emission is retried and fails open
 * (Requirement 27.7). Already-wrapped senders are left untouched so repeated post-processing
 * never stacks decorators.
 */
final class TracingSenderRetryBeanPostProcessor implements BeanPostProcessor {

    private final int maxRetries;

    TracingSenderRetryBeanPostProcessor(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof BytesMessageSender sender
                && !(bean instanceof RetryingBytesMessageSender)) {
            return new RetryingBytesMessageSender(sender, maxRetries);
        }
        return bean;
    }
}

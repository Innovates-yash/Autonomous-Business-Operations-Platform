package com.aisa.commons.observability;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.Encoding;

/**
 * A {@link BytesMessageSender} decorator that makes span emission resilient (Requirement 27.7).
 *
 * <p>When the underlying sender fails to deliver spans to the tracing backend, the send is
 * retried up to {@code maxRetries} additional times. If every attempt fails the batch is dropped
 * and a warning is logged rather than propagating the failure (fail-open). The decorated sender is
 * invoked by the asynchronous reporter on its own background thread, so neither the retries nor an
 * unreachable backend ever block request processing.
 *
 * <p>All other behaviour ({@link #encoding()}, {@link #messageMaxBytes()}, {@link #close()}) is
 * delegated unchanged so the decorator is a drop-in replacement for the real sender.
 */
final class RetryingBytesMessageSender implements BytesMessageSender {

    private static final Logger log = LoggerFactory.getLogger(RetryingBytesMessageSender.class);

    private final BytesMessageSender delegate;
    private final int maxRetries;

    RetryingBytesMessageSender(BytesMessageSender delegate, int maxRetries) {
        this.delegate = delegate;
        this.maxRetries = maxRetries;
    }

    @Override
    public Encoding encoding() {
        return delegate.encoding();
    }

    @Override
    public int messageMaxBytes() {
        return delegate.messageMaxBytes();
    }

    @Override
    public int messageSizeInBytes(List<byte[]> encodedSpans) {
        return delegate.messageSizeInBytes(encodedSpans);
    }

    @Override
    public int messageSizeInBytes(int encodedSizeInBytes) {
        return delegate.messageSizeInBytes(encodedSizeInBytes);
    }

    @Override
    public void send(List<byte[]> encodedSpans) throws IOException {
        int totalAttempts = maxRetries + 1;
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                delegate.send(encodedSpans);
                return;
            } catch (IOException | RuntimeException ex) {
                lastFailure = ex;
                if (log.isDebugEnabled()) {
                    log.debug("Span emission attempt {} of {} failed: {}",
                            attempt, totalAttempts, ex.toString());
                }
            }
        }
        // Fail open: every attempt failed, so drop the batch instead of propagating. The
        // service keeps serving and tracing simply loses this batch (Requirement 27.7).
        log.warn("Dropping {} span(s) after {} failed emission attempt(s): {}",
                encodedSpans.size(), totalAttempts,
                lastFailure == null ? "unknown error" : lastFailure.toString());
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}

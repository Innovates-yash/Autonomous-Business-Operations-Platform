package com.aisa.commons.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.Encoding;

/**
 * Verifies the telemetry-resilience behaviour required by Requirement 27.7: span emission is
 * retried a bounded number of times and, when every attempt fails, the batch is dropped without
 * propagating the failure (fail-open) so request processing is never blocked.
 */
class RetryingBytesMessageSenderTest {

    private static final List<byte[]> SPANS = List.of(new byte[]{1}, new byte[]{2});

    @Test
    void deliversOnFirstAttemptWithoutRetrying() throws IOException {
        CountingSender delegate = new CountingSender(0);

        new RetryingBytesMessageSender(delegate, 3).send(SPANS);

        assertThat(delegate.attempts()).isEqualTo(1);
    }

    @Test
    void retriesUntilSuccess() throws IOException {
        // Fails twice, then succeeds on the third attempt.
        CountingSender delegate = new CountingSender(2);

        new RetryingBytesMessageSender(delegate, 3).send(SPANS);

        assertThat(delegate.attempts()).isEqualTo(3);
    }

    @Test
    void retriesUpToThreeTimesThenFailsOpen() {
        // Always fails. With maxRetries=3 that is one initial attempt plus three retries.
        CountingSender delegate = new CountingSender(Integer.MAX_VALUE);

        RetryingBytesMessageSender sender = new RetryingBytesMessageSender(delegate, 3);

        // Fail-open: the exhausted send must NOT throw, so the caller (the async reporter
        // thread) keeps running and request processing is never affected.
        assertThatNoException().isThrownBy(() -> sender.send(SPANS));
        assertThat(delegate.attempts()).isEqualTo(4);
    }

    @Test
    void delegatesPassthroughMethods() throws IOException {
        CountingSender delegate = new CountingSender(0);

        RetryingBytesMessageSender sender = new RetryingBytesMessageSender(delegate, 3);

        assertThat(sender.encoding()).isEqualTo(Encoding.JSON);
        assertThat(sender.messageMaxBytes()).isEqualTo(500_000);
        sender.close();
        assertThat(delegate.closed()).isTrue();
    }

    /** Test double that fails its first {@code failuresBeforeSuccess} sends, then succeeds. */
    private static final class CountingSender implements BytesMessageSender {

        private final int failuresBeforeSuccess;
        private final AtomicInteger attempts = new AtomicInteger();
        private boolean closed;

        CountingSender(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public Encoding encoding() {
            return Encoding.JSON;
        }

        @Override
        public int messageMaxBytes() {
            return 500_000;
        }

        @Override
        public int messageSizeInBytes(List<byte[]> encodedSpans) {
            return Encoding.JSON.listSizeInBytes(encodedSpans);
        }

        @Override
        public int messageSizeInBytes(int encodedSizeInBytes) {
            return Encoding.JSON.listSizeInBytes(encodedSizeInBytes);
        }

        @Override
        public void send(List<byte[]> encodedSpans) throws IOException {
            int attempt = attempts.incrementAndGet();
            if (attempt <= failuresBeforeSuccess) {
                throw new IOException("simulated tracing backend outage on attempt " + attempt);
            }
        }

        @Override
        public void close() {
            closed = true;
        }

        int attempts() {
            return attempts.get();
        }

        boolean closed() {
            return closed;
        }
    }
}

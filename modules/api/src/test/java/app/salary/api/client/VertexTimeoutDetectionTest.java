package app.salary.api.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VertexGenerativeAiClient} needs live GCP to construct and is coverage-excluded, but
 * its timeout classification is real branching logic, so it is exercised directly here.
 *
 * <p>What makes this worth testing: the SDK wraps the transport exception, so the timeout is
 * never the exception you catch — it is several causes below it.
 */
class VertexTimeoutDetectionTest {

    @Test
    void recognisesATimeoutBuriedInTheCauseChain() {
        // The realistic shape: SDK wrapper over an HTTP layer over the socket.
        Throwable wrapped = new RuntimeException("generateContent failed",
                new IllegalStateException("transport error",
                        new SocketTimeoutException("Read timed out")));

        assertTrue(VertexGenerativeAiClient.isTimeout(wrapped));
    }

    @Test
    void recognisesTheTimeoutTypesTheTransportCanRaise() {
        assertTrue(VertexGenerativeAiClient.isTimeout(new SocketTimeoutException("read")));
        assertTrue(VertexGenerativeAiClient.isTimeout(new TimeoutException("deadline")));
        assertTrue(VertexGenerativeAiClient.isTimeout(new InterruptedIOException("interrupted")));
    }

    @Test
    void doesNotMistakeAnOrdinaryFailureForATimeout() {
        // Misclassifying these would report an upstream-capacity problem for what is
        // usually a malformed request.
        assertFalse(VertexGenerativeAiClient.isTimeout(new IllegalArgumentException("bad schema")));
        assertFalse(VertexGenerativeAiClient.isTimeout(
                new RuntimeException("quota exceeded", new IOException("connection reset"))));
        assertFalse(VertexGenerativeAiClient.isTimeout(null));
    }

    @Test
    void terminatesOnASelfReferencingCause() {
        // A cause chain that points at itself would otherwise spin forever.
        SelfCausingException loop = new SelfCausingException();
        assertFalse(VertexGenerativeAiClient.isTimeout(loop));
    }

    /** {@code getCause()} returning {@code this} is legal and does happen in the wild. */
    private static final class SelfCausingException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }
}

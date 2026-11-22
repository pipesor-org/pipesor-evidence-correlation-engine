package org.pipesor.correlation.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

/** Runtime limits and peers of the Correlation service (runtime manifest). */
@ConfigurationProperties("pipesor")
public record CorrelationProperties(Endpoints endpoints, Correlation correlation) {

    public record Endpoints(URI pipeline, URI logevent, URI recommendation) {
        public Endpoints {
            if (pipeline == null || logevent == null || recommendation == null) {
                throw new IllegalArgumentException("pipesor.endpoints.{pipeline,logevent,recommendation} are required");
            }
        }
    }

    public record Correlation(Intake intake, Peer owners, Peer recommendation) {
        public Correlation {
            if (intake == null || owners == null || recommendation == null) {
                throw new IllegalArgumentException("pipesor.correlation.{intake,owners,recommendation} are required");
            }
        }
    }

    public record Intake(Duration requestTimeout, int maxRequestBytes, int maxHeaderChars, int maxInFlight,
            int resourceDeniedStatus, int capacityExceededStatus) {
        private static final Set<Integer> FAILURES = Set.of(400, 409, 413, 422, 429, 503);

        public Intake {
            if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative() || maxRequestBytes < 1
                    || maxHeaderChars < 1 || maxInFlight < 1 || !FAILURES.contains(resourceDeniedStatus)
                    || !FAILURES.contains(capacityExceededStatus)) {
                throw new IllegalArgumentException("Explicit processed-evidence intake limits are required");
            }
        }
    }

    public record Peer(Duration timeout, int maxRequestBytes, int maxResponseBytes) {
        public Peer {
            if (timeout == null || timeout.isZero() || timeout.isNegative() || maxRequestBytes < 1 || maxResponseBytes < 1) {
                throw new IllegalArgumentException("Explicit peer budgets are required");
            }
        }
    }
}

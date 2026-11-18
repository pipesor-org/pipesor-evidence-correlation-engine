package org.pipesor.correlation.context.infrastructure.persistence.jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pipesor.correlation.context.application.ports.RecommendationDelivery;
import org.pipesor.correlation.context.application.ports.RecommendationDelivery.Receipt;
import org.pipesor.correlation.context.application.queries.CorrelatedRevision;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Caller;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Outcome;
import org.pipesor.correlation.context.infrastructure.validation.HttpOwnerReferences.Decision;
import org.pipesor.correlation.context.infrastructure.validation.HttpOwnerReferences.Token;
import org.pipesor.correlation.context.interfaces.rest.transform.NlpEvidenceV2;
import org.pipesor.correlation.shared.infrastructure.json.CanonicalJson;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiveProcessedEvidenceTest {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17.11");
    private static DriverManagerDataSource dataSource;

    @BeforeAll static void start() {
        DATABASE.start();
        dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        Flyway.configure().dataSource(dataSource).defaultSchema("evidence").load().migrate();
    }

    @AfterAll static void stop() { DATABASE.stop(); }

    @Test
    void lateReferenceAndLostAcknowledgementRecoverThroughTheSameDurableOperation() throws Exception {
        var body = wireFixture();
        var input = decode(body);
        var caller = trustedCaller(input);
        var authority = new AtomicReference<>(Decision.NOT_READY);
        var checks = new AtomicInteger();
        List<CorrelatedRevision> submitted = new ArrayList<>();
        var receiver = new ReceiveProcessedEvidence(dataSource, Clock.systemUTC(), (event, evidenceId, scopeRevision, deadline) -> {
            checks.incrementAndGet();
            assertThat(event.correlationId()).isEqualTo(input.correlationId());
            return authority.get();
        }, 20_000, (revision, token, remaining) -> {
            assertThat(remaining).isPositive();
            submitted.add(revision);
            if (submitted.size() == 1) throw new RecommendationDelivery.Failure(RecommendationDelivery.Failure.Kind.UNKNOWN);
            return new Receipt(revision.contextId(), revision.contextId(), RecommendationDelivery.Status.DUPLICATE);
        }, remaining -> currentDeliveryToken());
        assertThat(receiver.receive(input, caller, deadline())).isEqualTo(Outcome.NOT_READY);
        assertThat(submitted).isEmpty();
        assertThat(receipts(caller)).isZero();

        authority.set(Decision.AUTHORIZED);
        assertThat(receiver.receive(input, caller, deadline())).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(receipts(caller)).isEqualTo(1);
        assertThat(confirmed(caller)).isZero();
        var replay = decode(body.deepCopy().put("template", "Different Drain3 enrichment"));
        assertThat(receiver.receive(replay, caller, deadline())).isEqualTo(Outcome.ACCEPTED);
        assertThat(submitted).hasSize(2);
        assertThat(submitted.get(1)).isEqualTo(submitted.getFirst());
        assertThat(confirmed(caller)).isEqualTo(1);

        assertThat(receiver.receive(replay, caller, deadline())).isEqualTo(Outcome.DUPLICATE);
        assertThat(receiver.receive(decode(body.deepCopy().put("sanitized_content", "Changed protected text")), caller, deadline()))
                .isEqualTo(Outcome.MESSAGE_CONFLICT);
        authority.set(Decision.DENIED);
        assertThat(receiver.receive(replay, caller, deadline())).isEqualTo(Outcome.RESOURCE_DENIED);
        assertThat(submitted).hasSize(2);
        assertThat(receipts(caller)).isEqualTo(1);
        assertThat(confirmed(caller)).isEqualTo(1);
        assertThat(checks).hasValue(6);
    }

    @Test
    void unauthorizedBindingAndAdmissionOverflowLeaveNoDurableState() throws Exception {
        var original = wireFixture();
        var input = decode(original);
        var caller = trustedCaller(input);
        var checks = new AtomicInteger();
        var receiver = new ReceiveProcessedEvidence(dataSource, Clock.systemUTC(),
                (event, evidenceId, scopeRevision, deadline) -> { checks.incrementAndGet(); return Decision.AUTHORIZED; },
                32, (revision, token, remaining) -> { throw new AssertionError("Excessive projection must never deliver"); },
                remaining -> { throw new AssertionError("Excessive projection must not acquire a token"); });
        var wrong = original.deepCopy();
        ((ObjectNode) wrong.get("pipeline")).put("github_workflow_id", "9234");
        assertThat(receiver.receive(decode(wrong), caller, deadline())).isEqualTo(Outcome.RESOURCE_DENIED);
        assertThat(checks).hasValue(0);
        assertThat(receiver.receive(input, caller, deadline())).isEqualTo(Outcome.CAPACITY_EXCEEDED);
        assertThat(receipts(caller)).isZero();
        assertThat(groups(caller)).isZero();
        assertThat(confirmed(caller)).isZero();
        assertThat(checks).hasValue(1);
    }

    @Test
    void aMatchingReceiptAfterTheRecipientTokenBudgetCannotConfirm() throws Exception {
        var input = decode(wireFixture());
        var caller = trustedCaller(input);
        Instant now = Instant.now();
        var deliveries = new AtomicInteger();
        var receiver = new ReceiveProcessedEvidence(dataSource, Clock.fixed(now, java.time.ZoneOffset.UTC),
                (event, evidenceId, scopeRevision, deadline) -> Decision.AUTHORIZED, 20_000,
                (revision, token, remaining) -> {
                    deliveries.incrementAndGet();
                    try { Thread.sleep(250); }
                    catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
                    return new Receipt(revision.contextId(), revision.contextId(), RecommendationDelivery.Status.ACCEPTED);
                }, remaining -> new Token("synthetic-short-lived-token", now.plusMillis(150)));
        assertThat(receiver.receive(input, caller, now.plusSeconds(10))).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(deliveries).hasValue(1);
        assertThat(receipts(caller)).isEqualTo(1);
        assertThat(confirmed(caller)).isZero();
    }

    @Test
    void everyFrozenMemberMustRemainAuthorizedBeforeAnotherRevisionCanCommitOrReplay() throws Exception {
        var firstBody = wireFixture();
        var a = decode(firstBody);
        var caller = trustedCaller(a);
        var secondBody = firstBody.deepCopy().put("message_id", UUID.randomUUID().toString())
                .put("evidence_id", UUID.randomUUID().toString());
        ((ObjectNode) secondBody.get("capture_context")).put("scope_revision", "scope-rev-13");
        var b = decode(secondBody);
        var oldAuthority = new AtomicReference<>(Decision.AUTHORIZED);
        List<CorrelatedRevision> delivered = new ArrayList<>();
        var receiver = new ReceiveProcessedEvidence(dataSource, Clock.systemUTC(), (attempt, evidenceId, revision, deadline) -> {
            assertThat(attempt.executionId()).isEqualTo(a.executionId());
            if (evidenceId.equals(a.evidenceId())) {
                assertThat(revision).isEqualTo("scope-rev-12");
                return oldAuthority.get();
            }
            assertThat(evidenceId).isEqualTo(b.evidenceId());
            assertThat(revision).isEqualTo("scope-rev-13");
            return Decision.AUTHORIZED;
        }, 20_000, (revision, token, remaining) -> {
            delivered.add(revision);
            return new Receipt(revision.contextId(), revision.contextId(), RecommendationDelivery.Status.ACCEPTED);
        }, remaining -> currentDeliveryToken());

        assertThat(receiver.receive(a, caller, deadline())).isEqualTo(Outcome.ACCEPTED);
        oldAuthority.set(Decision.DENIED);
        assertThat(receiver.receive(b, caller, deadline())).isEqualTo(Outcome.RESOURCE_DENIED);
        oldAuthority.set(Decision.NOT_READY);
        assertThat(receiver.receive(b, caller, deadline())).isEqualTo(Outcome.NOT_READY);
        assertThat(receipts(caller)).isEqualTo(1);
        assertThat(delivered).hasSize(1);

        oldAuthority.set(Decision.AUTHORIZED);
        assertThat(receiver.receive(b, caller, deadline())).isEqualTo(Outcome.ACCEPTED);
        assertThat(delivered.getLast().signals()).extracting(CorrelatedRevision.IntroducedSignal::evidenceId)
                .containsExactly(a.evidenceId(), b.evidenceId());
        oldAuthority.set(Decision.DENIED);
        assertThat(receiver.receive(b, caller, deadline())).isEqualTo(Outcome.RESOURCE_DENIED);
        assertThat(delivered).hasSize(2);
        assertThat(receipts(caller)).isEqualTo(2);
    }

    private static Caller trustedCaller(NlpEvidenceV2 input) {
        return new Caller("synthetic-signed-nlp", "synthetic-scope-" + UUID.randomUUID(),
                input.captureContext().tenantId(), input.captureContext().githubAccountId(),
                input.captureContext().githubInstallationId(), input.pipeline().githubRepositoryId(),
                input.pipeline().githubWorkflowId());
    }

    private static Token currentDeliveryToken() {
        return new Token("synthetic-recommendation-token", Instant.now().plusSeconds(60));
    }

    private static Instant deadline() { return Instant.now().plusSeconds(10); }

    private static ObjectNode wireFixture() throws Exception {
        var contents = Files.readAllBytes(Path.of("docs/contracts/nlp-processed-evidence-v2.examples.json"));
        return (ObjectNode) CanonicalJson.mapper().readTree(contents).get("with_job");
    }

    private static NlpEvidenceV2 decode(ObjectNode body) {
        return NlpEvidenceV2.decode(CanonicalJson.mapper().writeValueAsBytes(body), 4096,
                body.get("message_id").asString(), body.get("correlation_id").asString());
    }

    private static long receipts(Caller caller) throws Exception {
        return count("SELECT count(*) FROM evidence.correlation_receipts WHERE scope_key = ?", caller);
    }

    private static long groups(Caller caller) throws Exception {
        return count("SELECT count(*) FROM evidence.correlation_groups WHERE scope_key = ?", caller);
    }

    private static long confirmed(Caller caller) throws Exception {
        return count("SELECT count(*) FROM evidence.correlation_groups WHERE scope_key = ? AND confirmed_context_id IS NOT NULL", caller);
    }

    private static long count(String sql, Caller caller) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, caller.scopeKey());
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}

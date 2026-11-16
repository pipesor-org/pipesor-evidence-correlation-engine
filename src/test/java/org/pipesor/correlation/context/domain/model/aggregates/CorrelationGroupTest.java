package org.pipesor.correlation.context.domain.model.aggregates;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationGroupTest {
    @Test
    void restoringMembershipCopiesReferencesAndPreservesTheOriginalOnGrowth() {
        var context = context();
        var id = UUID.randomUUID();
        var evidenceId = UUID.randomUUID();
        var members = new HashSet<>(List.of(evidenceId));
        var restored = CorrelationGroup.restore(id, context, members);
        members.clear();
        var added = UUID.randomUUID();
        var next = restored.include(context, added);
        assertThatThrownBy(() -> restored.evidenceIds().add(added)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(restored.evidenceIds()).containsExactly(evidenceId);
        assertThat(restored.revision()).isEqualTo(1);
        assertThat(next.evidenceIds()).containsExactlyInAnyOrder(evidenceId, added);
        assertThat(next.id()).isEqualTo(id);
        assertThat(next.context()).isEqualTo(context);
        assertThat(next.revision()).isEqualTo(2);
    }

    @Test
    void distinctEvidenceCreatesCumulativeRevisionsAndDuplicatesKeepTheRevision() {
        var context = context();
        var id = UUID.randomUUID();
        var firstEvidence = UUID.randomUUID();
        var secondEvidence = UUID.randomUUID();
        var empty = CorrelationGroup.start(id, context);
        var first = empty.include(context, firstEvidence);
        var second = first.include(context, secondEvidence);
        var duplicate = second.include(new CorrelationGroup.Context(context.scope(),
                context.executionId(), context.incidentId()), firstEvidence);

        assertThat(empty.revision()).isZero();
        assertThat(empty.evidenceIds()).isEmpty();
        assertThat(first.revision()).isEqualTo(1);
        assertThat(first.evidenceIds()).containsExactly(firstEvidence);
        assertThat(second.revision()).isEqualTo(2);
        assertThat(second.evidenceIds()).containsExactlyInAnyOrder(firstEvidence, secondEvidence);
        assertThat(duplicate.revision()).isEqualTo(2);
        assertThat(duplicate.evidenceIds()).containsExactlyInAnyOrder(firstEvidence, secondEvidence);
        for (var revision : List.of(empty, first, second, duplicate)) {
            assertThat(revision.id()).isEqualTo(id);
            assertThat(revision.context()).isEqualTo(context);
        }
    }

    @Test
    void evidenceReferencesCannotBeMutatedThroughAnAlias() {
        var context = context();
        var evidenceId = UUID.randomUUID();
        var first = CorrelationGroup.start(UUID.randomUUID(), context).include(context, evidenceId);
        var references = first.evidenceIds();
        var nextEvidence = UUID.randomUUID();
        var second = first.include(context, nextEvidence);

        assertThatThrownBy(() -> references.add(nextEvidence)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(references).containsExactly(evidenceId);
        assertThat(first.evidenceIds()).containsExactly(evidenceId);
        assertThat(second.evidenceIds()).containsExactlyInAnyOrder(evidenceId, nextEvidence);
    }

    @Test
    void differentScopesExecutionsAndIncidentsAreRejectedEvenForAnExistingEvidenceId() {
        var context = context();
        var evidenceId = UUID.randomUUID();
        var group = CorrelationGroup.start(UUID.randomUUID(), context).include(context, evidenceId);
        var unrelated = List.of(
                new CorrelationGroup.Context("other-synthetic-scope", context.executionId(), context.incidentId()),
                new CorrelationGroup.Context(context.scope() + " ", context.executionId(), context.incidentId()),
                new CorrelationGroup.Context(context.scope(), UUID.randomUUID(), context.incidentId()),
                new CorrelationGroup.Context(context.scope(), context.executionId(), UUID.randomUUID()));

        for (var other : unrelated) {
            assertThatThrownBy(() -> group.include(other, evidenceId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Evidence context does not match the group");
            assertThatThrownBy(() -> group.include(other, UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Evidence context does not match the group");
        }
        assertThat(group.revision()).isEqualTo(1);
        assertThat(group.evidenceIds()).containsExactly(evidenceId);
    }

    @Test
    void contextRequiresNonblankScopeAndLogicalIdsWithoutEchoingValues() {
        var executionId = UUID.randomUUID();
        var incidentId = UUID.randomUUID();
        assertThatThrownBy(() -> new CorrelationGroup.Context(null, executionId, incidentId))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Scope is required");
        for (var scope : List.of("", " ", "\t\n")) {
            assertThatThrownBy(() -> new CorrelationGroup.Context(scope, executionId, incidentId))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Scope is required");
        }
        assertThatThrownBy(() -> new CorrelationGroup.Context("synthetic-sensitive-scope", null, incidentId))
                .isInstanceOf(NullPointerException.class).hasMessage("Execution ID is required");
        assertThatThrownBy(() -> new CorrelationGroup.Context("synthetic-sensitive-scope", executionId, null))
                .isInstanceOf(NullPointerException.class).hasMessage("Incident ID is required");
    }

    @Test
    void groupAndEvidenceRequireIdsAndContextWithoutEchoingValues() {
        var context = context();
        var id = UUID.randomUUID();
        var group = CorrelationGroup.start(id, context);
        assertThatThrownBy(() -> CorrelationGroup.start(null, context))
                .isInstanceOf(NullPointerException.class).hasMessage("Group ID is required");
        assertThatThrownBy(() -> CorrelationGroup.start(id, null))
                .isInstanceOf(NullPointerException.class).hasMessage("Group context is required");
        assertThatThrownBy(() -> group.include(null, UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class).hasMessage("Evidence context is required");
        assertThatThrownBy(() -> group.include(context, null))
                .isInstanceOf(NullPointerException.class).hasMessage("Evidence ID is required");
        assertThat(group.revision()).isZero();
        assertThat(group.evidenceIds()).isEmpty();
    }

    private static CorrelationGroup.Context context() {
        return new CorrelationGroup.Context("synthetic-machine-scope", UUID.randomUUID(), UUID.randomUUID());
    }
}

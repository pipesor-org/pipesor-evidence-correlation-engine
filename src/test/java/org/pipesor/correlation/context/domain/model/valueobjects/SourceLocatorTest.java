package org.pipesor.correlation.context.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceLocatorTest {
    private static final String DIGEST = "28a1babbb7adec8029801c6eaefd85584e1f485b7f831e8926192dfd2bdc0613";

    @Test
    void acceptsTheAuthentic21LocatorAndGeneralizedMembers() {
        assertThat(SourceLocator.valid("v2:job:130644639649:member:0_verify.txt:lines:31-31:sha256:" + DIGEST, "130644639649")).isTrue();
        assertThat(SourceLocator.valid("v2:job:7:member:build%2F3_Run%20mvn%20test.txt:lines:10-209:sha256:" + DIGEST, "7")).isTrue();
        assertThat(SourceLocator.valid("v2:job:7:member:2_npm%20ci%20%C3%A9.txt:lines:1-2:sha256:" + DIGEST, "7")).isTrue();
    }

    @Test
    void rejectsOtherJobsRangesAndUnsafeMembers() {
        assertThat(SourceLocator.valid("v2:job:7:member:0_verify.txt:lines:31-31:sha256:" + DIGEST, "8")).isFalse();
        assertThat(SourceLocator.valid("v2:job:7:member:a.txt:lines:10-9:sha256:" + DIGEST, "7")).isFalse();
        assertThat(SourceLocator.valid("v2:job:7:member:a.txt:lines:1-201:sha256:" + DIGEST, "7")).isFalse();
        assertThat(SourceLocator.valid("v2:job:7:member:%2Fetc%2Fpasswd:lines:1-1:sha256:" + DIGEST, "7")).isFalse();
        assertThat(SourceLocator.valid("v2:job:7:member:..%2Fa.txt:lines:1-1:sha256:" + DIGEST, "7")).isFalse();
        assertThat(SourceLocator.valid("v2:job:7:member:a%2Fb%2Fc.txt:lines:1-1:sha256:" + DIGEST, "7")).isFalse();
        assertThat(SourceLocator.valid("v2:job:7:member:a%0Ab.txt:lines:1-1:sha256:" + DIGEST, "7")).isFalse();
        assertThat(SourceLocator.valid("v2:job:7:member:%FF.txt:lines:1-1:sha256:" + DIGEST, "7")).isFalse();
        assertThat(SourceLocator.valid("v2:job:7:member:a b.txt:lines:1-1:sha256:" + DIGEST, "7")).isFalse();
        assertThat(SourceLocator.valid("v2:job:7:member:%2f.txt:lines:1-1:sha256:" + DIGEST, "7")).isFalse();
    }
}

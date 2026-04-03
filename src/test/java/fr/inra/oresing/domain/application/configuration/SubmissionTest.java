package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

@Tag("core.config")
class SubmissionTest {

    @Test
    void testSubmissionReferenceScopeBuilder() {
        Submission.SubmissionScope.SubmissionReferenceScope scope = SubmissionBuilder.submissionReferenceScope()
                .reference("ref")
                .component("comp")
                .build();

        Assertions.assertEquals("ref", scope.reference());
        Assertions.assertEquals("comp", scope.component());
    }

    @Test
    void testTimeScopeBuilder() {
        Submission.SubmissionScope.TimeScope scope = SubmissionBuilder.timeScope()
                .component("comp")
                .build();

        Assertions.assertEquals("comp", scope.component());
    }

    @Test
    void testSubmissionFileNameParsingBuilder() {
        List<String> authScopes = List.of("scope1");
        Submission.SubmissionFileNameParsing parsing = SubmissionBuilder.submissionFileNameParsing()
                .pattern("pattern")
                .authorizationScopes(authScopes)
                .startDate(1)
                .endDate(2)
                .build();

        Assertions.assertEquals("pattern", parsing.pattern());
        Assertions.assertEquals(authScopes, parsing.authorizationScopes());
        Assertions.assertEquals(1, parsing.startDate());
        Assertions.assertEquals(2, parsing.endDate());
    }

    @Test
    void testSubmissionScopeBuilder() {
        Submission.SubmissionScope.SubmissionReferenceScope refScope = SubmissionBuilder.submissionReferenceScope().build();
        Submission.SubmissionScope.TimeScope timeScope = SubmissionBuilder.timeScope().build();
        List<Submission.SubmissionScope.SubmissionReferenceScope> refScopes = List.of(refScope);

        Submission.SubmissionScope scope = SubmissionBuilder.submissionScope()
                .referenceScopes(refScopes)
                .timescope(timeScope)
                .build();

        Assertions.assertEquals(refScopes, scope.referenceScopes());
        Assertions.assertEquals(timeScope, scope.timescope());
    }

    @Test
    void testSubmissionBuilder() {
        Submission.SubmissionFileNameParsing parsing = SubmissionBuilder.submissionFileNameParsing().build();
        Submission.SubmissionScope scope = SubmissionBuilder.submissionScope().build();

        Submission submission = new SubmissionBuilder.SubmissionMainBuilder()
                .strategy(SubmissionType.OA_VERSIONING)
                .fileNameParsing(parsing)
                .submissionScope(scope)
                .build();

        Assertions.assertEquals(SubmissionType.OA_VERSIONING, submission.strategy());
        Assertions.assertEquals(parsing, submission.fileNameParsing());
        Assertions.assertEquals(scope, submission.submissionScope());
    }
}
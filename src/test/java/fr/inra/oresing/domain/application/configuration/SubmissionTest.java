package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@Tag("core.config")
@Tag("domain.model")
class SubmissionTest {

    @Test
    void submissionTypeValuesAndOaInsertion() {
        Assertions.assertNotNull(SubmissionType.OA_INSERTION);
        Assertions.assertNotNull(SubmissionType.OA_VERSIONING);
        Assertions.assertTrue(SubmissionType.VALUES.contains("OA_INSERTION"));
        Assertions.assertTrue(SubmissionType.VALUES.contains("OA_VERSIONING"));
        Assertions.assertEquals(2, SubmissionType.VALUES.size());
    }

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

    @Test
    void parseFileName_nullFileNameParsing_returnsNull() {
        Submission submission = new SubmissionBuilder.SubmissionMainBuilder()
                .strategy(SubmissionType.OA_INSERTION)
                .fileNameParsing(null)
                .submissionScope(SubmissionBuilder.submissionScope().build())
                .build();

        BinaryFileDataset result = submission.parseFileName(Map.of(), "file_2024.csv", null);
        Assertions.assertNull(result);
    }

    @Test
    void parseFileName_emptyPattern_returnsNull() {
        Submission.SubmissionFileNameParsing parsing = SubmissionBuilder.submissionFileNameParsing()
                .pattern("")
                .build();
        Submission submission = new SubmissionBuilder.SubmissionMainBuilder()
                .strategy(SubmissionType.OA_INSERTION)
                .fileNameParsing(parsing)
                .submissionScope(SubmissionBuilder.submissionScope().build())
                .build();

        BinaryFileDataset result = submission.parseFileName(Map.of(), "file.csv", null);
        Assertions.assertNull(result);
    }

    @Test
    void parseFileName_nonMatchingPattern_returnsNull() {
        Submission.SubmissionFileNameParsing parsing = SubmissionBuilder.submissionFileNameParsing()
                .pattern("data_(\\d{4})\\.csv")
                .authorizationScopes(List.of())
                .build();
        Submission submission = new SubmissionBuilder.SubmissionMainBuilder()
                .strategy(SubmissionType.OA_INSERTION)
                .fileNameParsing(parsing)
                .submissionScope(SubmissionBuilder.submissionScope().build())
                .build();

        BinaryFileDataset result = submission.parseFileName(Map.of(), "other_file.csv", null);
        Assertions.assertNull(result);
    }

    @Test
    void parseFileName_withStartDateGroup_parsesDate() {
        // pattern: prefix_(dd-MM-yyyy).csv ; group 1 = startDate
        Submission.SubmissionFileNameParsing parsing = SubmissionBuilder.submissionFileNameParsing()
                .pattern("data_(\\d{2}-\\d{2}-\\d{4})\\.csv")
                .authorizationScopes(List.of())
                .startDate(1)
                .build();
        Submission.SubmissionScope scope = SubmissionBuilder.submissionScope().build();
        Submission submission = new SubmissionBuilder.SubmissionMainBuilder()
                .strategy(SubmissionType.OA_INSERTION)
                .fileNameParsing(parsing)
                .submissionScope(scope)
                .build();

        BinaryFileDataset result = submission.parseFileName(Map.of(), "data_01-06-2024.csv", null);
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getFrom());
    }

    @Test
    void parseFileName_withEndDateGroup_parsesDate() {
        Submission.SubmissionFileNameParsing parsing = SubmissionBuilder.submissionFileNameParsing()
                .pattern("data_(\\d{2}-\\d{2}-\\d{4})\\.csv")
                .authorizationScopes(List.of())
                .endDate(1)
                .build();
        Submission.SubmissionScope scope = SubmissionBuilder.submissionScope().build();
        Submission submission = new SubmissionBuilder.SubmissionMainBuilder()
                .strategy(SubmissionType.OA_INSERTION)
                .fileNameParsing(parsing)
                .submissionScope(scope)
                .build();

        BinaryFileDataset result = submission.parseFileName(Map.of(), "data_31-12-2024.csv", null);
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getTo());
    }

    @Test
    void parseFileName_withAuthorizationScope_setsAuthorization() {
        Submission.SubmissionScope.SubmissionReferenceScope refScope = SubmissionBuilder.submissionReferenceScope()
                .component("site")
                .reference("sites")
                .build();
        Submission.SubmissionScope scope = SubmissionBuilder.submissionScope()
                .referenceScopes(List.of(refScope))
                .build();
        Submission.SubmissionFileNameParsing parsing = SubmissionBuilder.submissionFileNameParsing()
                .pattern("data_([a-z]+)\\.csv")
                .authorizationScopes(List.of("site"))
                .build();
        Submission submission = new SubmissionBuilder.SubmissionMainBuilder()
                .strategy(SubmissionType.OA_INSERTION)
                .fileNameParsing(parsing)
                .submissionScope(scope)
                .build();

        BinaryFileDataset result = submission.parseFileName(Map.of(), "data_paris.csv", null);
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getRequiredAuthorizations().get("sites"));
    }

    @Test
    void parseFileName_withBadStartDate_throwsException() {
        Submission.SubmissionFileNameParsing parsing = SubmissionBuilder.submissionFileNameParsing()
                .pattern("data_([a-z]+)\\.csv")
                .authorizationScopes(List.of())
                .startDate(1)
                .build();
        Submission.SubmissionScope scope = SubmissionBuilder.submissionScope().build();
        Submission submission = new SubmissionBuilder.SubmissionMainBuilder()
                .strategy(SubmissionType.OA_INSERTION)
                .fileNameParsing(parsing)
                .submissionScope(scope)
                .build();

        Assertions.assertThrows(SiOreAuthorizationRequestException.class,
                () -> submission.parseFileName(Map.of(), "data_notadate.csv", null));
    }

    @Test
    void parseFileName_withExistingDataset_doesNotOverwriteFrom() {
        Submission.SubmissionFileNameParsing parsing = SubmissionBuilder.submissionFileNameParsing()
                .pattern("data_(\\d{2}-\\d{2}-\\d{4})\\.csv")
                .authorizationScopes(List.of())
                .startDate(1)
                .build();
        Submission.SubmissionScope scope = SubmissionBuilder.submissionScope().build();
        Submission submission = new SubmissionBuilder.SubmissionMainBuilder()
                .strategy(SubmissionType.OA_INSERTION)
                .fileNameParsing(parsing)
                .submissionScope(scope)
                .build();

        BinaryFileDataset existing = new BinaryFileDataset();
        existing.setFrom("2000-01-01 00:00:00");
        BinaryFileDataset result = submission.parseFileName(Map.of(), "data_31-12-2024.csv", existing);
        // existing "from" should NOT be overwritten
        Assertions.assertEquals("2000-01-01 00:00:00", result.getFrom());
    }

    @Test
    void submissionScopeComponentNames() {
        Submission.SubmissionScope.SubmissionReferenceScope r1 = new Submission.SubmissionScope.SubmissionReferenceScope("ref1", "comp1");
        Submission.SubmissionScope.SubmissionReferenceScope r2 = new Submission.SubmissionScope.SubmissionReferenceScope("ref2", "comp2");
        Submission.SubmissionScope scope = new Submission.SubmissionScope(List.of(r1, r2), null);

        Assertions.assertEquals(2, scope.componentNames().size());
        Assertions.assertTrue(scope.componentNames().contains("comp1"));
        Assertions.assertTrue(scope.componentNames().contains("comp2"));
    }

    @Test
    void submissionScopeReferenceNames() {
        Submission.SubmissionScope.SubmissionReferenceScope r1 = new Submission.SubmissionScope.SubmissionReferenceScope("ref1", "comp1");
        Submission.SubmissionScope scope = new Submission.SubmissionScope(List.of(r1), null);

        Assertions.assertEquals(1, scope.referenceNames().size());
        Assertions.assertTrue(scope.referenceNames().contains("ref1"));
    }

    @Test
    void submissionScopeNullReferenceScopes_componentNamesEmpty() {
        Submission.SubmissionScope scope = new Submission.SubmissionScope(null, null);
        Assertions.assertTrue(scope.componentNames().isEmpty());
        Assertions.assertTrue(scope.referenceNames().isEmpty());
    }

    @Test
    void fileNameParsing_groupCount_andPatternGroups() {
        Submission.SubmissionFileNameParsing parsing = new Submission.SubmissionFileNameParsing(
                "data_(\\w+)_(\\d+)\\.csv", List.of("site"), 2, null);
        Assertions.assertEquals(2, parsing.groupCount());
    }

    @Test
    void fileNameParsing_createExampleSubmissionFileName() {
        Submission.SubmissionFileNameParsing parsing = new Submission.SubmissionFileNameParsing(
                "data_(\\w+)_(\\d{2}-\\d{2}-\\d{4})\\.csv", List.of("site"), 2, null);
        String example = parsing.createExampleSubmissionFileName();
        Assertions.assertNotNull(example);
        Assertions.assertTrue(example.contains("dd-MM-yyyy"));
    }

    @Test
    void fileNameParsing_orderedGroups() {
        Submission.SubmissionFileNameParsing parsing = new Submission.SubmissionFileNameParsing(
                "data_(\\w+)_(\\w+)\\.csv", List.of("site"), 1, 2);
        List<String> groups = parsing.orderedGroups();
        Assertions.assertEquals(2, groups.size());
    }

    @Test
    void getTimeScopePattern_noVersioning_returnsDefault() {
        Submission submission = new SubmissionBuilder.SubmissionMainBuilder()
                .strategy(SubmissionType.OA_INSERTION)
                .submissionScope(SubmissionBuilder.submissionScope().build())
                .build();
        String pattern = submission.getTimeScopePattern(Map.of());
        Assertions.assertEquals(Submission.DD_MM_YYYY_FOR_FILE, pattern);
    }
}
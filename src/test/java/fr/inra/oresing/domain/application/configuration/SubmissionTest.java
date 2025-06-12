package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("domain.model")
class SubmissionTest {
    final Submission submission = new Submission(
            SubmissionType.OA_VERSIONING,
            new Submission.SubmissionFileNameParsing(
                    "(.*)_(.*)_(.*)_(.*).csv",
                    List.of("projet", "chemin"),
                    3, 4),
            new Submission.SubmissionScope(
                    List.of(
                            new Submission.SubmissionScope.ReferenceScope(
                                    "projet",
                                    "projet"
                            ),
                            new Submission.SubmissionScope.ReferenceScope(
                                    "sites",
                                    "chemin"
                            )

                    ),
                    new Submission.SubmissionScope.TimeScope(
                            "date"
                    )
            )
    );
    final BinaryFileDataset binaryFileDataset = new BinaryFileDataset();

    @Test
    void testPatternGroups() {
        List<Submission.PatternPosition> groupPositions = submission.fileNameParsing().patternGroups();
        assertEquals(4, groupPositions.size());
        assertEquals("[[0, 4], [5, 9], [10, 14], [15, 19]]", groupPositions.toString());
    }

    @Test
    void testPatternToBeReplacedByGroupCapture() {
        assertEquals("%1$s_%2$s_%3$s_%4$s.csv", submission.fileNameParsing().patternToBeReplacedByGroupCapture());
    }

    @Test
    void testGroupCount() {
        assertEquals(4, submission.fileNameParsing().groupCount());
    }

    @Test
    void testOrderedGroups() {
        LinkedList<String> orderedGroups = submission.fileNameParsing().orderedGroups();
        assertArrayEquals(List.of("projet", "chemin", ConfigurationSchemaNode.OA_START_DATE_MATCH_PATTERN, ConfigurationSchemaNode.OA_END_DATE_MATCH_PATTERN
        ).toArray(new String[0]), orderedGroups.toArray(new String[0]));
    }

    @Test
    void parseFileName() {
        submission.parseFileName("leProjet_leSite_01-01-1984_05-01-1984.csv", binaryFileDataset);
        assertTrue(binaryFileDataset.getRequiredAuthorizations().get("projet").contains(Ltree.fromSql("leProjet")));
        assertTrue(binaryFileDataset.getRequiredAuthorizations().get("sites").contains(Ltree.fromSql("leSite")));
        assertEquals("1984-01-01 00:00:00", binaryFileDataset.getFrom());
        assertEquals("1984-01-05 00:00:00", binaryFileDataset.getTo());
        //do nothing if already done
        submission.parseFileName("leProjet2_leSite2_01-01-1985_05-01-1985.csv", binaryFileDataset);
        assertTrue(binaryFileDataset.getRequiredAuthorizations().get("projet").contains(Ltree.fromSql("leProjet")));
        assertTrue(binaryFileDataset.getRequiredAuthorizations().get("sites").contains(Ltree.fromSql("leSite")));
        assertEquals("1984-01-01 00:00:00", binaryFileDataset.getFrom());
        assertEquals("1984-01-05 00:00:00", binaryFileDataset.getTo());

    }

    @Test
    void parseFileNameWithInvalidStartDate() {
        try {
            submission.parseFileName("leProjet_leSite_01-01x1984_05-01-1984.csv", binaryFileDataset);
        } catch (SiOreAuthorizationRequestException e) {
            assertEquals(AuthorizationRequestException.BAD_FILE_NAME_START_DATE, e.getException());
            assertEquals("projetNK_cheminNK_dd-MM-yyyy_dd-MM-yyyy.csv", e.getParams().get("fileNameFormat"));
            assertEquals("01-01x1984", e.getParams().get("startDate"));
            assertEquals("dd-MM-yyyy", e.getParams().get("dateformat"));
        }

    }

    @Test
    void parseFileNameWithInvalidEndDate() {
        try {
            submission.parseFileName("leProjet_leSite_01-01-1984_05-01/1984.csv", binaryFileDataset);
        } catch (SiOreAuthorizationRequestException e) {
            assertEquals(AuthorizationRequestException.BAD_FILE_NAME_END_DATE, e.getException());
            assertEquals("projetNK_cheminNK_dd-MM-yyyy_dd-MM-yyyy.csv", e.getParams().get("fileNameFormat"));
            assertEquals("05-01/1984", e.getParams().get("endDate"));
            assertEquals("dd-MM-yyyy", e.getParams().get("dateformat"));
        }
    }
}
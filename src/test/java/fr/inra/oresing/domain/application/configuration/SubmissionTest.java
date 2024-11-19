package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubmissionTest {
    Submission submission = new Submission(
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
    BinaryFileDataset binaryFileDataset = new BinaryFileDataset();

    @Test
    void parseFileName() {
        submission.parseFileName("leProjet_leSite_01-01-1984_05-01-1984.csv", binaryFileDataset);
        assertEquals(Ltree.fromSql("leProjet"),binaryFileDataset.getRequiredAuthorizations().get("projet"));
        assertEquals(Ltree.fromSql("leSite"),binaryFileDataset.getRequiredAuthorizations().get("chemin"));
        assertEquals("{},ISO resolved to 1984-01-01",binaryFileDataset.getFrom().toString());
        assertEquals("{},ISO resolved to 1984-01-05",binaryFileDataset.getTo().toString());
        //do nothing if already done
        submission.parseFileName("leProjet2_leSite2_01-01-1985_05-01-1985.csv", binaryFileDataset);
        assertEquals(Ltree.fromSql("leProjet"),binaryFileDataset.getRequiredAuthorizations().get("projet"));
        assertEquals(Ltree.fromSql("leSite"),binaryFileDataset.getRequiredAuthorizations().get("chemin"));
        assertEquals("{},ISO resolved to 1984-01-01",binaryFileDataset.getFrom().toString());
        assertEquals("{},ISO resolved to 1984-01-05",binaryFileDataset.getTo().toString());

    }
    @Test
    void parseFileNameWithInvalidStartDate() {
        try {
            submission.parseFileName("leProjet_leSite_01-01/1984_05-01-1984.csv", binaryFileDataset);
        }catch (SiOreAuthorizationRequestException e){
            assertEquals(AuthorizationRequestException.BAD_FILE_NAME_START_DATE,e.getException());
            assertEquals("projet_chemin_dd-MM-yyyy_dd-MM-yyyy.csv",e.getParams().get("fileNameFormat"));
            assertEquals("01-01/1984",e.getParams().get("startDate"));
            assertEquals("dd-MM-yyyy",e.getParams().get("dateformat"));
        }

    }
    @Test
    void parseFileNameWithInvalidEndDate() {
        try {
            submission.parseFileName("leProjet_leSite_01-01-1984_05-01/1984.csv", binaryFileDataset);
        }catch (SiOreAuthorizationRequestException e){
            assertEquals(AuthorizationRequestException.BAD_FILE_NAME_END_DATE,e.getException());
            assertEquals("projet_chemin_dd-MM-yyyy_dd-MM-yyyy.csv",e.getParams().get("fileNameFormat"));
            assertEquals("05-01/1984",e.getParams().get("endDate"));
            assertEquals("dd-MM-yyyy",e.getParams().get("dateformat"));
        }
    }
}
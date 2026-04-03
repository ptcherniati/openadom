package fr.inra.oresing.persistence.data.read.bundle;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("domain.model")
class FileContentTest {
    final Submission submission = new Submission(
            SubmissionType.OA_VERSIONING,
            new Submission.SubmissionFileNameParsing(
                    "(.*)_(.*)_(.*)_(.*).csv",
                    List.of("projet", "chemin"),
                    3, 4),
            new Submission.SubmissionScope(
                    List.of(
                            new Submission.SubmissionScope.SubmissionReferenceScope(
                                    "projet",
                                    "projet"
                            ),
                            new Submission.SubmissionScope.SubmissionReferenceScope(
                                    "sites",
                                    "chemin"
                            )

                    ),
                    new Submission.SubmissionScope.TimeScope(
                            "date"
                    )
            )
    );

    @Test
    void buildFileNameRequest() {
        String dataName = "data";
        Application application = Mockito.mock(Application.class);
        Mockito.doReturn(Optional.of(submission)).when(application).findSubmission(dataName);
        String request = FileContent.buildFileNameRequest(application, dataName);
        assertEquals("""
                WITH linkeds AS (
                      SELECT DISTINCT
                          referencetype,
                          jsonb_object_keys(refslinkedto) AS linkedto
                      FROM  null.referencevalue
                  ),
                  linkedsarray AS (
                      SELECT
                          referencetype,
                          array_agg(linkedto) AS "refsLinked"
                      FROM linkeds
                      GROUP BY referencetype
                  )
                SELECT DISTINCT ON (rv.binaryfile)
                    format('%1$s_%2$s_%3$s_%4$s.csv',
                    ((bf."authorization").requiredauthorizations).projet[1],
                	((bf."authorization").requiredauthorizations).sites[1],
                	TO_CHAR(
                    CASE
                        WHEN lower((bf."authorization").timescope) = '-infinity'::timestamp
                          THEN '0001-01-01'::timestamp
                        ELSE lower((bf."authorization").timescope)
                    END
                ,'dd-MM-yyyy'),
                	TO_CHAR(
                    CASE
                        WHEN upper((bf."authorization").timescope) = 'infinity'::timestamp
                          THEN '9999-12-31'::timestamp
                        ELSE upper((bf."authorization").timescope)
                  	END
                ,'dd-MM-yyyy')
                ) AS "fileName",
                    la."refsLinked",
                    bf.filedata AS "fileContent"
                FROM null.referencevalue rv
                JOIN null.binaryfile bf ON bf.id = rv.binaryfile
                LEFT JOIN linkedsarray la ON la.referencetype = rv.referencetype
                WHERE rv.referencetype = 'data'
                ORDER BY rv.binaryfile;
                """, request);
    }
}
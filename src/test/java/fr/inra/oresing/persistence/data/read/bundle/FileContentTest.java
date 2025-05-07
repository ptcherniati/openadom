package fr.inra.oresing.persistence.data.read.bundle;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import org.assertj.core.api.AssertJProxySetup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void buildFileNameRequest() {
        String dataName = "data";
        Application application = Mockito.mock(Application.class);
        Mockito.doReturn(Optional.of(submission)).when(application).findSubmission(dataName);
        String request = FileContent.buildFileNameRequest(application, dataName);
        assertEquals("""
               SELECT DISTINCT ON (rv.binaryfile)
                   format('%1$s_%2$s_%3$s_%4$s.csv',
                   ((bf."authorization").requiredauthorizations).projet[1],
               	((bf."authorization").requiredauthorizations).sites[1],
               	TO_CHAR(lower((bf."authorization").timescope),'yyyy-MM-dd'),
               	TO_CHAR(upper((bf."authorization").timescope),'yyyy-MM-dd')
               ) as "fileName",
                   convert_from(bf.filedata), 'UTF8') AS "fileContent"
               FROM null.referencevalue rv
               JOIN null.binaryfile bf ON bf.id = rv.binaryfile
               WHERE rv.referencetype = 'data'
               ORDER BY rv.binaryfile, bf.updatedate DESC;
                """, request);
    }

    @Test
    void fileName() {
    }

    @Test
    void fileContent() {
    }
}
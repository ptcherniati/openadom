package fr.inra.oresing.persistence.data.read.bundle;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.SubmissionType;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record FileContent(String fileName, String fileContent) {
    public static final String EXPORT_REGISTER_DATA_CSV_SQL = """
            SELECT DISTINCT ON (rv.binaryfile)
                %3$s as "fileName",
                convert_from(decode(encode(bf.filedata, 'escape'), 'base64'), 'UTF8') AS "fileContent"
            FROM %1$s.referencevalue rv
            JOIN %1$s.binaryfile bf ON bf.id = rv.binaryfile
            WHERE rv.referencetype = '%2$s'
            ORDER BY rv.binaryfile, bf.updatedate DESC;
            """;
    public static String GENERIC_FILE_NAME = "format('%s_%s.csv', 'name', LPAD(ROW_NUMBER() OVER (ORDER BY bf.updatedate)::text, 3, '0'))";

    public static String buildFileNameRequest(Application application, String dataName) {
        String patternForFileNameRequest = application.findSubmission(dataName)
                .map(FileContent::toRequest)
                .orElse(GENERIC_FILE_NAME);
        return EXPORT_REGISTER_DATA_CSV_SQL.formatted(application.getName(), dataName, patternForFileNameRequest);
    }

    private static String toRequest(Submission submission) {
        Optional<String> patternOpt = Optional.ofNullable(submission)
                .filter(submission1 -> SubmissionType.OA_VERSIONING.equals(submission1.strategy()))
                .map(Submission::fileNameParsing)
                .map(Submission.SubmissionFileNameParsing::pattern);
        if (!patternOpt.isPresent()) {
            return GENERIC_FILE_NAME;
        }
        String pattern = patternOpt.get();
        String patternToBeReplacedByGroupCapture = Optional.of(submission)
                .map(Submission::fileNameParsing)
                .map(Submission.SubmissionFileNameParsing::patternToBeReplacedByGroupCapture)
                .orElse(pattern);
        String groups = Optional.ofNullable(submission)
                .map(Submission::fileNameParsing)
                .map(Submission.SubmissionFileNameParsing::orderedGroups)
                .stream().flatMap(List::stream)
                .map(group -> {
                    if (ConfigurationSchemaNode.OA_START_DATE_MATCH_PATTERN.equals(group)) {
                        return "TO_CHAR(lower((bf.\"authorization\").timescope),'yyyy-MM-dd')";
                    }
                    if (ConfigurationSchemaNode.OA_END_DATE_MATCH_PATTERN.equals(group)) {
                        return "TO_CHAR(upper((bf.\"authorization\").timescope),'yyyy-MM-dd')";
                    }
                    String reference = Optional.ofNullable(submission)
                            .map(Submission::submissionScope)
                            .map(Submission.SubmissionScope::referenceScopes)
                            .stream().flatMap(List::stream)
                            .filter(referenceScope -> referenceScope.component().equals(group))
                            .map(Submission.SubmissionScope.ReferenceScope::reference)
                            .findFirst()
                            .orElse(group);
                    return "((bf.\"authorization\").requiredauthorizations).%s[1]".formatted(reference);
                })
                .collect(Collectors.joining(",\n\t"));
        return  """
                format('%s',
                    %s
                )""".formatted(patternToBeReplacedByGroupCapture, groups);
    }
}
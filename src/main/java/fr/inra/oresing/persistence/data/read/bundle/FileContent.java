package fr.inra.oresing.persistence.data.read.bundle;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.DateChecker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record FileContent(List<String> refsLinked, String fileName, String fileContent) {
    private static final Pattern FORBIDDEN_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>| ]");

    public static String sanitizePatternForFilename(String pattern) {
        return FORBIDDEN_FILENAME_CHARS.matcher(pattern).replaceAll("-");
    }

    public static final String EXPORT_REGISTER_DATA_CSV_SQL = """
            WITH linkeds AS (
                  SELECT DISTINCT
                      referencetype,
                      jsonb_object_keys(refslinkedto) AS linkedto
                  FROM sipro_v01.referencevalue
              ),
              linkedsarray AS (
                  SELECT
                      referencetype,
                      array_agg(linkedto) AS "refsLinked"
                  FROM linkeds
                  GROUP BY referencetype
              )
            SELECT DISTINCT ON (rv.binaryfile)
                %3$s AS "fileName",
                la."refsLinked",
                convert_from(bf.filedata, 'UTF8') AS "fileContent"
            FROM %1$s.referencevalue rv
            JOIN %1$s.binaryfile bf ON bf.id = rv.binaryfile
            JOIN linkedsarray la ON la.referencetype = rv.referencetype
            WHERE rv.referencetype = '%2$s'
            ORDER BY rv.binaryfile;            
            """;
    public static final String GENERIC_FILE_NAME = "format('%s_%s.csv', rv.referencetype, LPAD(ROW_NUMBER() OVER (ORDER BY bf.updatedate)::text, 3, '0'))";

    public static String buildFileNameRequest(Application application, String dataName) {
        String patternForFileNameRequest = application.findSubmission(dataName)
                .filter(submission ->SubmissionType.OA_VERSIONING.equals(submission.strategy()))
                .map(submission -> FileContent.toRequest(submission, application.findData(dataName).map(StandardDataDescription::componentDescriptions).orElseGet(Map::of)))
                .orElse(GENERIC_FILE_NAME);
        return EXPORT_REGISTER_DATA_CSV_SQL.formatted(application.getName(), dataName, patternForFileNameRequest);
    }

    private static String toRequest(Submission submission, Map<String, ComponentDescription> conponentDescriptions){
        final String timescopePattern = submission.getTimeScopePattern(conponentDescriptions);
        Optional<String> patternOpt = Optional.ofNullable(submission)
                .filter(submission1 -> SubmissionType.OA_VERSIONING.equals(submission1.strategy()))
                .map(Submission::fileNameParsing)
                .map(Submission.SubmissionFileNameParsing::pattern);
        if (patternOpt.isEmpty()) {
            return GENERIC_FILE_NAME;
        }
        String fileNamePattern = patternOpt.get();
        String patternToBeReplacedByGroupCapture = Optional.of(submission)
                .map(Submission::fileNameParsing)
                .map(Submission.SubmissionFileNameParsing::patternToBeReplacedByGroupCapture)
                .orElse(fileNamePattern);
        String groups = Optional.ofNullable(submission)
                .map(Submission::fileNameParsing)
                .map(Submission.SubmissionFileNameParsing::orderedGroups)
                .stream().flatMap(List::stream)
                .map(group -> {
                    if (ConfigurationSchemaNode.OA_START_DATE_MATCH_PATTERN.equals(group)) {
                        return """
                                TO_CHAR(
                                    CASE
                                        WHEN lower((bf."authorization").timescope) = '-infinity'::timestamp
                                          THEN '0001-01-01'::timestamp
                                        ELSE lower((bf."authorization").timescope)
                                    END
                                ,'%s')"""
                                .formatted(timescopePattern);
                    }
                    if (ConfigurationSchemaNode.OA_END_DATE_MATCH_PATTERN.equals(group)) {
                        return """
                                TO_CHAR(
                                    CASE
                                        WHEN upper((bf."authorization").timescope) = 'infinity'::timestamp
                                          THEN '9999-12-31'::timestamp
                                        ELSE upper((bf."authorization").timescope)
                                  	END
                                ,'%s')""".formatted(timescopePattern);
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
    if(groups.isEmpty()) {
        return GENERIC_FILE_NAME;
    }
        return """
                format('%s',
                    %s
                )""".formatted(patternToBeReplacedByGroupCapture, groups);
    }
}
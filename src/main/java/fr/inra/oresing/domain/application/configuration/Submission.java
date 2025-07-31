package fr.inra.oresing.domain.application.configuration;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.checker.DateChecker;
import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;
import fr.inra.oresing.persistence.data.read.bundle.FileContent;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record Submission(
        SubmissionType strategy,
        SubmissionFileNameParsing fileNameParsing,
        SubmissionScope submissionScope
) {

    public static final String DD_MM_YYYY_FOR_FILE = "dd-MM-yyyy";

    public BinaryFileDataset parseFileName(Map<String, ComponentDescription> componentDescriptions, String fileName, BinaryFileDataset binaryFileDataset) {
        final String timeScopePattern = getTimeScopePattern(componentDescriptions);
        if (binaryFileDataset == null) {
            binaryFileDataset = new BinaryFileDataset();
        }
        try {
            if (fileNameParsing() == null || Strings.isNullOrEmpty(fileNameParsing().pattern())) {
                return null;
            }
            Matcher matcher = Pattern.compile(fileNameParsing().pattern()).matcher(fileName);
            if (!matcher.matches()) {
                return null;
            }
            for (int groupIndex = 1; groupIndex <= matcher.groupCount(); groupIndex++) {
                String value = matcher.group(groupIndex);
                if (groupIndex == fileNameParsing().startDate()) {
                    if (Strings.isNullOrEmpty(binaryFileDataset.getFrom())) {
                        try {
                            binaryFileDataset.setFrom(LocalDate.parse(value, DateTimeFormatter.ofPattern(timeScopePattern)).atStartOfDay().format(DataImporter.ISO_DATE_TIME_FORMATTER));
                        } catch (DateTimeParseException dtpe) {
                            throw new SiOreAuthorizationRequestException(
                                    AuthorizationRequestException.BAD_FILE_NAME_START_DATE,
                                    Map.of(
                                            "startDate", value,
                                            "dateformat", DD_MM_YYYY_FOR_FILE,
                                            "fileNameFormat", fileNameParsing().createExampleSubmissionFileName()
                                    )
                            );
                        }
                    }
                } else if (groupIndex == fileNameParsing().endDate()) {
                    if (Strings.isNullOrEmpty(binaryFileDataset.getTo())) {
                        try {
                            binaryFileDataset.setTo(LocalDate.parse(value, DateTimeFormatter.ofPattern(timeScopePattern)).atStartOfDay().format(DataImporter.ISO_DATE_TIME_FORMATTER));
                        } catch (DateTimeParseException dtpe) {
                            throw new SiOreAuthorizationRequestException(
                                    AuthorizationRequestException.BAD_FILE_NAME_END_DATE,
                                    Map.of(
                                            "endDate", value,
                                            "dateformat", DD_MM_YYYY_FOR_FILE,
                                            "fileNameFormat", fileNameParsing().createExampleSubmissionFileName()
                                    )
                            );
                        }
                    }
                } else {
                    String component = fileNameParsing().authorizationScopes.get(groupIndex - 1);
                    String reference = submissionScope().referenceScopes().stream()
                            .filter(referenceScope -> referenceScope.component.equals(component))
                            .map(SubmissionScope.ReferenceScope::reference)
                            .findFirst()
                            .orElse(component);
                    if (binaryFileDataset.getRequiredAuthorizations().get(reference) == null) {
                        binaryFileDataset.getRequiredAuthorizations().put(reference, List.of(Ltree.fromSql(value)));
                    }
                }
            }
            return binaryFileDataset;
        } catch (SiOreAuthorizationRequestException e) {
            throw e;

        } catch (Exception e) {
            throw new SiOreAuthorizationRequestException(
                    AuthorizationRequestException.INVALID_FILE_NAME,
                    Map.of("fileNameFormat", fileNameParsing().createExampleSubmissionFileName())
            );

        }
    }

    public record PatternPosition(int start, int end) {

        @Override
        public String toString() {
            return "[%s, %s]".formatted(start, end);
        }
    }

    public record SubmissionFileNameParsing(
            String pattern,
            List<String> authorizationScopes,
            Integer startDate,
            Integer endDate
    ) {

        public static final Pattern GROUP_CAPTURE_PATTERN = Pattern.compile("\\([^(]*\\)");

        public String patternToBeReplacedByGroupCapture() {
            String patternToBeReplacedByGroupCapture = pattern();
            for (int i = patternGroups().size(); i > 0; i--) {
                PatternPosition patternGroup = patternGroups().get(i - 1);
                patternToBeReplacedByGroupCapture = patternToBeReplacedByGroupCapture.substring(0, patternGroup.start()) +
                                                    "%%%d$s".formatted(i) +
                                                    patternToBeReplacedByGroupCapture.substring(patternGroup.end());
            }
            return patternToBeReplacedByGroupCapture;
        }

        public LinkedList<String> orderedGroups() {
            Map<Integer, String> orderedGroups = new HashMap<>();
            int scopeIndex = 0;
            for (int i = 1; i < groupCount() + 1; i++) {
                if (i == startDate()) {
                    orderedGroups.put(i, ConfigurationSchemaNode.OA_START_DATE_MATCH_PATTERN);
                } else if (i == endDate()) {
                    orderedGroups.put(i, ConfigurationSchemaNode.OA_END_DATE_MATCH_PATTERN);
                } else if (authorizationScopes.size() > scopeIndex) {
                    orderedGroups.put(i, authorizationScopes().get(scopeIndex++));
                } else {
                    orderedGroups.put(i, "");
                }
            }
            return new LinkedList<>(orderedGroups.values());
        }

        public int groupCount() {
            Matcher matcher = GROUP_CAPTURE_PATTERN.matcher(pattern());
            return patternGroups().size();
        }

        public List<PatternPosition> patternGroups() {
            Matcher matcher = GROUP_CAPTURE_PATTERN.matcher(pattern());
            List<PatternPosition> matches = new LinkedList<>();
            while (matcher.find()) {
                matches.add(new PatternPosition(matcher.start(), matcher.end()));
            }
            return matches;
        }

        public String createExampleSubmissionFileName() {
            int scopeIndex = 0;

            Pattern r = Pattern.compile("\\(.*?\\)"); // regex for capturing groups
            LinkedList<String> scopes = new LinkedList<>(authorizationScopes);
            Matcher m = r.matcher(pattern());

            StringBuilder sb = new StringBuilder();

            int groupCount = 0;
            while (m.find()) {
                groupCount++;
                if (groupCount == startDate || groupCount == endDate) {
                    m.appendReplacement(sb, "dd-MM-yyyy");
                } else {
                    if (scopeIndex < authorizationScopes.size()) {
                        m.appendReplacement(sb, "%sNK".formatted(scopes.pop()));
                        scopeIndex++;
                    } else {
                        m.appendReplacement(sb, "");
                    }
                }
            }
            m.appendTail(sb);

            return sb.toString();
        }
    }

    public record SubmissionScope(
            List<ReferenceScope> referenceScopes,
            TimeScope timescope
    ) {
        public Set<String> componentNames() {
            return Optional.ofNullable(referenceScopes())
                    .map(references -> references.stream().map(ReferenceScope::component).collect(Collectors.toSet()))
                    .orElse(Set.of());
        }

        public Set<String> referenceNames() {
            return Optional.ofNullable(referenceScopes())
                    .map(references -> references.stream().map(ReferenceScope::reference).collect(Collectors.toSet()))
                    .orElse(Set.of());
        }

        public record ReferenceScope(
                String reference,
                String component
        ) {
        }

        public record TimeScope(
                String component
        ) {
        }
    }

    public String getTimeScopePattern(Map<String, ComponentDescription> conponentDescriptions) {
        return Optional.ofNullable(this)
                .filter(submission1 -> SubmissionType.OA_VERSIONING.equals(submission1.strategy()))
                .map(Submission::submissionScope)
                .map(Submission.SubmissionScope::timescope)
                .map(Submission.SubmissionScope.TimeScope::component)
                .map(conponentDescriptions::get)
                .map(ComponentDescription::checker)
                .filter(DateChecker.class::isInstance)
                .map(DateChecker.class::cast)
                .map(DateChecker::pattern)
                .map(FileContent::sanitizePatternForFilename)
                .orElse(DD_MM_YYYY_FOR_FILE);
    }
}
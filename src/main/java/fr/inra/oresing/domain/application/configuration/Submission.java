package fr.inra.oresing.domain.application.configuration;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;

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
    public static final String DD_MM_YYYY = "dd/MM/yyyy";

    public BinaryFileDataset parseFileName(String fileName, BinaryFileDataset binaryFileDataset) {
        if(binaryFileDataset==null){
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
                            binaryFileDataset.setFrom(LocalDate.parse(value,DateTimeFormatter.ofPattern(DD_MM_YYYY_FOR_FILE)).atStartOfDay().format(DataImporter.ISO_DATE_TIME_FORMATTER));
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
                            binaryFileDataset.setTo(LocalDate.parse(value,DateTimeFormatter.ofPattern(DD_MM_YYYY_FOR_FILE)).atStartOfDay().format(DataImporter.ISO_DATE_TIME_FORMATTER));
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
                    AuthorizationRequestException.INVAALID_FILE_NAME,
                    Map.of("fileNameFormat", fileNameParsing().createExampleSubmissionFileName())
            );

        }
    }

    public record SubmissionFileNameParsing(
            String pattern,
            List<String> authorizationScopes,
            Integer startDate,
            Integer endDate
    ) {
        public String createExampleSubmissionFileName() {
            int scopeIndex = 0;

            Pattern r = Pattern.compile("\\(.*?\\)"); // regex for capturing groups
            LinkedList<String> scopes = new LinkedList<>(authorizationScopes);
            Matcher m = r.matcher(pattern());

            StringBuffer sb = new StringBuffer();

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
            SubmissionScope.TimeScope timescope
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
}

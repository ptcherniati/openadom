package fr.inra.oresing.domain;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
@Setter
public class BinaryFileDataset {
    private String datatype;
    private Map<String, List<Ltree>> requiredAuthorizations = new HashMap<>();
    private String from;
    private String to;
    private String comment;

    public static BinaryFileDataset EMPTY_INSTANCE() {
        return new BinaryFileDataset();
    }

    @Override
    public String toString() {
        final String authorizationsString = requiredAuthorizations.entrySet().stream()
                .map(ra -> String.format("%s : %s", ra.getKey(), ra.getValue().getFirst().getSql()))
                .collect(Collectors.joining(",", "[", "]"));
        return String.format("%s -> [%s, %s]",
                authorizationsString, Strings.isNullOrEmpty(from) ? "" : LocalDateTimeRange.DATE_FORMATTER_DDMMYYYY.format(LocalDateTimeRange.DATE_TIME_FORMATTER.parse(from)),
                Strings.isNullOrEmpty(to) ? "" : LocalDateTimeRange.DATE_FORMATTER_DDMMYYYY.format(LocalDateTimeRange.DATE_TIME_FORMATTER.parse(to))
        );
    }


    public BinaryFileDataset testrequiredAuthorizationsAndReturnHierarchicalKeys(DataRepositoryForBuffer dataRepositoryForBuffer) {
        BinaryFileDataset binaryFileDataset = this.copy();
        Map<String, List<Ltree>> requiredAuthorizationsTested = Optional.ofNullable(binaryFileDataset)
                .map(BinaryFileDataset::getRequiredAuthorizations).
                orElseGet(HashMap::new);
        for (Map.Entry<String, List<Ltree>> requiredAuthorizationByReference : requiredAuthorizationsTested.entrySet()) {
            List<Ltree> hierarchicalKeyForEntry = dataRepositoryForBuffer.getHierarchicalKeyForEntry(requiredAuthorizationByReference);
            requiredAuthorizationsTested.put(requiredAuthorizationByReference.getKey(), hierarchicalKeyForEntry);
        }
        return binaryFileDataset;

    }

    public BinaryFileDataset copy() {
        BinaryFileDataset binaryFileDataset = new BinaryFileDataset();
        binaryFileDataset.setRequiredAuthorizations(requiredAuthorizations);
        binaryFileDataset.setTo(to);
        binaryFileDataset.setFrom(from);
        binaryFileDataset.setComment(comment);
        binaryFileDataset.setDatatype(datatype);
        return binaryFileDataset;
    }
}
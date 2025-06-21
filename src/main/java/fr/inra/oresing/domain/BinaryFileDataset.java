package fr.inra.oresing.domain;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
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
                authorizationsString,
                from,
                to
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

    public BinaryFileDataset withPattern(DatePattern datePattern) {
        final LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.of(DatePattern.of(LocalDateTimeRange.YYYY_MM_DD_HH_MM_SS), getFrom(), getTo());
        try {
            BinaryFileDataset localBinaryFileDataset = copy();
            localBinaryFileDataset.setFrom(datePattern.formatter().format(localDateTimeRange.getLowerPointOrMin()));
            localBinaryFileDataset.setTo(datePattern.formatter().format(localDateTimeRange.getUpperEndpointOrMax()));
            return localBinaryFileDataset;
        } catch (Exception e) {
            return this;
        }
    }

    public void setTo(String to) {
        this.to = "null".equals(to) ? null : to;
    }

    public void setFrom(String from) {
        this.from = "null".equals(from) ? null : from;
    }
}
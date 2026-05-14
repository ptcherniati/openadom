package fr.inra.oresing.domain;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.repository.data.DataRepository;
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

    public static BinaryFileDataset emptyInstance() {
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


    public BinaryFileDataset testrequiredAuthorizationsAndReturnHierarchicalKeys(DataRepository dataRepository) {
        BinaryFileDataset binaryFileDataset = this.copy();
        Map<String, List<Ltree>> requiredAuthorizationsTested = Optional.ofNullable(binaryFileDataset)
                .map(BinaryFileDataset::getRequiredAuthorizations).
                orElseGet(HashMap::new);
        for (Map.Entry<String, List<Ltree>> requiredAuthorizationByReference : requiredAuthorizationsTested.entrySet()) {

        String referenceType = requiredAuthorizationByReference.getKey();
            final Map<String, String> hierarchicalKeysByKeyForReferenceTypes = dataRepository.findHierarchicalKeysByKeyForReferenceTypes(List.of(referenceType));
            final List<Ltree> hierarchicalKeys = requiredAuthorizationByReference.getValue()
                    .stream()
                    .map(Ltree::getSql)
                    .map(hierarchicalKeysByKeyForReferenceTypes::get)
                    .map(Ltree::fromSql)
                    .toList();
            requiredAuthorizationsTested.put(requiredAuthorizationByReference.getKey(), hierarchicalKeys );
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

    public BinaryFileDataset withPattern(DatePattern<?> datePattern) {
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

    public void setIfNotPresentDatatype(String dataType) {
        if (getDatatype() != null) {
            return;
        }
        setDatatype(dataType);
    }
}
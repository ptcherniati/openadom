package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.Ltree;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record BinaryFileDatasetResult(
        String datatype,
        Map<String, List<String>> requiredAuthorizations,
        String from,
        String to) {
    public static BinaryFileDatasetResult of(BinaryFileDataset binaryFileDataset) {
        return new BinaryFileDatasetResult(
                binaryFileDataset.getDatatype(),
                binaryFileDataset.getRequiredAuthorizations().entrySet()
                        .stream().collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().stream()
                                        .map(Ltree::getSql)
                                        .toList()
                        )),
                binaryFileDataset.getFrom(),
                binaryFileDataset.getTo()
        );
    }
}

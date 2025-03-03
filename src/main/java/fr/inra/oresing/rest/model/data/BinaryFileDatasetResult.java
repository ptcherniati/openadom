package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.Ltree;

import java.util.List;
import java.util.Map;

public record BinaryFileDatasetResult(
        String datatype,
        Map<String,List<Ltree>> requiredAuthorizations,
        String from,
        String to) {
    public static BinaryFileDatasetResult of(BinaryFileDataset binaryFileDataset) {
        return new BinaryFileDatasetResult(
                binaryFileDataset.getDatatype(),
                binaryFileDataset.getRequiredAuthorizations(),
                binaryFileDataset.getFrom(),
                binaryFileDataset.getTo()
        );
    }
}

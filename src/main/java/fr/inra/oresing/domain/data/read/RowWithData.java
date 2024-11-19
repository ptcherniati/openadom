package fr.inra.oresing.domain.data.read;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.data.Datum;
import fr.inra.oresing.domain.data.deposit.PublishContext;

import java.util.Optional;

public record RowWithData(int lineNumber, Datum datum, PublishContext.PublishContextBuilder publishContextBuilder) {
    public Optional<BinaryFileDataset> getBinaryFile() {
        return Optional.ofNullable(publishContextBuilder())
                .flatMap(PublishContext.PublishContextBuilder::getBinaryFile);
    }
}

package fr.inra.oresing.model;

import java.util.Set;
import java.util.function.Function;

public interface ReferenceColumnValue<T> extends SomethingToBeStoredAsJsonInDatabase<T>, SomethingToFormatAsCsvCellContent, SomethingToBeSentToFrontend<String> {

    Set<String> getValuesToCheck();

    ReferenceColumnValue<T> transform(Function<String, String> transformation);

    @Override
    String toJsonForFrontend();
}

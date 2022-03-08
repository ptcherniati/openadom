package fr.inra.oresing.model;

import java.util.Collection;
import java.util.function.Function;

public interface ReferenceColumnValue<T> extends SomethingToBeStoredAsJsonInDatabase<T>, SomethingToFormatAsCsvCellContent, SomethingToBeSentToFrontend<String> {

    Collection<String> getValuesToCheck();

    ReferenceColumnValue<T> transform(Function<String, String> transformation);

    @Override
    String toJsonForFrontend();
}

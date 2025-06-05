package fr.inra.oresing.persistence.requestbuilder.data;

import java.util.function.Supplier;

sealed interface SelectRequestWhere permits SelectRequestWhereInSelect {
    Supplier<String> filter();

    default String build() {
        if (filter() == null) {
            return "";
        }
        return "\n AND (\n%1$s\n)".formatted(filter().get());
    }

}
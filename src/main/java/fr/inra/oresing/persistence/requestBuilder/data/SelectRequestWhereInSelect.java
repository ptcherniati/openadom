package fr.inra.oresing.persistence.requestBuilder.data;

import java.util.function.Supplier;

record SelectRequestWhereInSelect(Supplier<String> filter) implements SelectRequestWhere {
}

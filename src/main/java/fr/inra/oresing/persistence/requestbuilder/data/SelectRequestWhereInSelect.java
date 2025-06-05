package fr.inra.oresing.persistence.requestbuilder.data;

import java.util.function.Supplier;

record SelectRequestWhereInSelect(Supplier<String> filter) implements SelectRequestWhere {
}
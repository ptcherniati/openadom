package fr.inra.oresing.persistence;

import java.util.List;

public record FilterList(
        String listName,
        List<RefsLinked> refsLinkeds
) {
}
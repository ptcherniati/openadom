package fr.inra.oresing.domain.data.read;

import fr.inra.oresing.domain.data.deposit.PublishContext;

import java.util.List;
import java.util.Map;

public record ParsedCsvRow(int lineNumber, List<Map.Entry<String, String>> columns,
                           PublishContext.PublishContextBuilder publishContextBuilder) {
}

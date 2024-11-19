package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;

import java.util.Map;
import java.util.Optional;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.*;

public record IntervalValuesNumeric(
        String from,
        String to) implements WithIntervalValues {
    public IntervalValuesNumeric {
        Float fromNumeric = null;
        Float toNumeric = null;
        if (from != null) {
            try {
                fromNumeric = Float.parseFloat(from);
            } catch (final NumberFormatException e) {
                throw new BadDownloadDatasetQuery(FILTER_BAD_FORMAT_FOR_START_NUMERIC);
            }
            if (to != null) {
                try {
                    toNumeric = Float.parseFloat(to);
                } catch (final NumberFormatException e) {
                    throw new BadDownloadDatasetQuery(FILTER_BAD_FORMAT_FOR_END_NUMERIC);
                }
                if (fromNumeric != null && toNumeric != null && fromNumeric.compareTo(toNumeric) > 0) {
                    throw new BadDownloadDatasetQuery(FILTER_BAD_FORMAT_BAD_RANGE_FOR_NUMERICS, Map.of("from", from, "to", to));
                }
            }

        }
    }

    private static Number toNumeric(final String value) {
        try {
            return Double.parseDouble(value);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    public Number fromFromNumeric() {
        return Optional.ofNullable(from())
                .map(IntervalValuesNumeric::toNumeric)
                .orElse(Integer.MIN_VALUE);
    }

    public Number fromToNumeric() {
        return Optional.ofNullable(to())
                .map(IntervalValuesNumeric::toNumeric)
                .orElse(Integer.MAX_VALUE);
    }
}

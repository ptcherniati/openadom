package fr.inra.oresing.domain.data;

import com.opencsv.CSVWriter;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import org.apache.commons.csv.CSVFormat;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public record UUIDsfromData(Set<UUID> uuidsfromData) {

    public UUIDsfromData() {
        this(new HashSet<>());
    }

    public void addRefsLinkedTo(Map.Entry<String, Map<String, RefsLinkedToValue>> refsLinkedToEntry) {
        Set<UUID> uuids = refsLinkedToEntry.getValue().values().stream()
                .map(RefsLinkedToValue::uuids)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        uuidsfromData()
                .addAll(uuids);
    }
}

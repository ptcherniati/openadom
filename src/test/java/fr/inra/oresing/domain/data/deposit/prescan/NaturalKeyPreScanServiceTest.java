package fr.inra.oresing.domain.data.deposit.prescan;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests pour {@link NaturalKeyPreScanService} : verifie l'extraction
 * de valeurs distinctes par colonne , la composition de naturalkey
 * composite , le streaming sans materialisation , et les edge cases
 * ( colonnes absentes , valeurs vides , whitespace ) .
 */
class NaturalKeyPreScanServiceTest {

    private NaturalKeyPreScanService service;
    private CSVFormat                format;

    @BeforeEach
    void setUp() {
        service = new NaturalKeyPreScanService();
        format  = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(';').get();
    }

    @Test
    void extracts_distinct_values_for_single_column() throws IOException {
        String csv = """
                site;treatment;date
                S01;T1;2024-01-01
                S02;T2;2024-01-02
                S01;T3;2024-01-03
                S03;T1;2024-01-04
                """;
        try (Reader r = new StringReader(csv)) {
            Set<String> sites = service.extractDistinctValues(r, format, "site");
            assertThat(sites).containsExactlyInAnyOrder("S01", "S02", "S03");
        }
    }

    @Test
    void extracts_distinct_values_for_multiple_columns() throws IOException {
        String csv = """
                site;treatment;date
                S01;T1;2024-01-01
                S02;T2;2024-01-02
                S01;T3;2024-01-03
                """;
        try (Reader r = new StringReader(csv)) {
            Map<String, Set<String>> result =
                    service.extractDistinctValues(r, format, Set.of("site", "treatment"));
            assertThat(result).containsOnlyKeys("site", "treatment");
            assertThat(result.get("site")).containsExactlyInAnyOrder("S01", "S02");
            assertThat(result.get("treatment")).containsExactlyInAnyOrder("T1", "T2", "T3");
        }
    }

    @Test
    void empty_columns_of_interest_returns_empty_map() throws IOException {
        try (Reader r = new StringReader("a;b\n1;2\n")) {
            Map<String, Set<String>> result =
                    service.extractDistinctValues(r, format, Collections.emptySet());
            assertThat(result).isEmpty();
        }
    }

    @Test
    void null_columns_of_interest_returns_empty_map() throws IOException {
        try (Reader r = new StringReader("a;b\n1;2\n")) {
            Map<String, Set<String>> result =
                    service.extractDistinctValues(r, format, (Set<String>) null);
            assertThat(result).isEmpty();
        }
    }

    @Test
    void missing_column_in_csv_not_in_result() throws IOException {
        String csv = """
                site;date
                S01;2024-01-01
                S02;2024-01-02
                """;
        try (Reader r = new StringReader(csv)) {
            Map<String, Set<String>> result =
                    service.extractDistinctValues(r, format, Set.of("site", "treatment"));
            // 'treatment' n'existe pas dans le CSV -> pas dans le resultat
            assertThat(result).containsOnlyKeys("site");
            assertThat(result.get("site")).containsExactlyInAnyOrder("S01", "S02");
        }
    }

    @Test
    void empty_csv_returns_empty_set_for_present_columns() throws IOException {
        // header only, no data row
        String csv = "site;treatment\n";
        try (Reader r = new StringReader(csv)) {
            Map<String, Set<String>> result =
                    service.extractDistinctValues(r, format, Set.of("site"));
            assertThat(result).containsOnlyKeys("site");
            assertThat(result.get("site")).isEmpty();
        }
    }

    @Test
    void blank_and_whitespace_cells_are_excluded() throws IOException {
        String csv = """
                site;treatment
                S01;T1
                ;T2
                S02;
                   ;T3
                S03;T4
                """;
        try (Reader r = new StringReader(csv)) {
            Map<String, Set<String>> result =
                    service.extractDistinctValues(r, format, Set.of("site", "treatment"));
            // site : S01, S02, S03 ( pas de vide ni whitespace-only )
            assertThat(result.get("site")).containsExactlyInAnyOrder("S01", "S02", "S03");
            // treatment : T1, T2, T3, T4 ( pas de vide )
            assertThat(result.get("treatment")).containsExactlyInAnyOrder("T1", "T2", "T3", "T4");
        }
    }

    @Test
    void duplicates_are_collapsed_into_distinct_set() throws IOException {
        String csv = """
                site
                S01
                S01
                S01
                S02
                S01
                """;
        try (Reader r = new StringReader(csv)) {
            Set<String> sites = service.extractDistinctValues(r, format, "site");
            assertThat(sites).containsExactlyInAnyOrder("S01", "S02");
        }
    }

    @Test
    void values_are_trimmed() throws IOException {
        String csv = """
                site
                  S01
                S02
                  S01
                S02
                """;
        try (Reader r = new StringReader(csv)) {
            Set<String> sites = service.extractDistinctValues(r, format, "site");
            // S01 et S02 deduplique apres strip()
            assertThat(sites).containsExactlyInAnyOrder("S01", "S02");
        }
    }

    @Test
    void single_column_shortcut_returns_empty_set_when_missing() throws IOException {
        try (Reader r = new StringReader("a;b\n1;2\n")) {
            Set<String> result = service.extractDistinctValues(r, format, "nonexistent");
            assertThat(result).isEmpty();
        }
    }

    @Test
    void compose_composite_natural_key_concatenates_columns() throws Exception {
        String csv = "a;b;c\nv1;v2;v3\n";
        try (Reader r = new StringReader(csv); CSVParser parser = CSVParser.parse(r, format.builder().setHeader().setSkipHeaderRecord(true).get())) {
            CSVRecord rec = parser.iterator().next();
            String nk = NaturalKeyPreScanService.composeCompositeNaturalKey(rec, List.of("a", "b"), "__");
            assertThat(nk).isEqualTo("v1__v2");
        }
    }

    @Test
    void compose_composite_natural_key_handles_missing_column_as_empty() throws Exception {
        String csv = "a;b\nv1;v2\n";
        try (Reader r = new StringReader(csv); CSVParser parser = CSVParser.parse(r, format.builder().setHeader().setSkipHeaderRecord(true).get())) {
            CSVRecord rec = parser.iterator().next();
            String nk = NaturalKeyPreScanService.composeCompositeNaturalKey(rec, List.of("a", "missing", "b"), "__");
            assertThat(nk).isEqualTo("v1____v2");
        }
    }

    @Test
    void compose_returns_empty_when_columns_list_null_or_empty() throws Exception {
        String csv = "a\n1\n";
        try (Reader r = new StringReader(csv); CSVParser parser = CSVParser.parse(r, format.builder().setHeader().setSkipHeaderRecord(true).get())) {
            CSVRecord rec = parser.iterator().next();
            assertThat(NaturalKeyPreScanService.composeCompositeNaturalKey(rec, null, "__")).isEmpty();
            assertThat(NaturalKeyPreScanService.composeCompositeNaturalKey(rec, List.of(), "__")).isEmpty();
        }
    }

    @Test
    void compose_handles_null_record() {
        String nk = NaturalKeyPreScanService.composeCompositeNaturalKey(null, List.of("a"), "__");
        assertThat(nk).isEmpty();
    }

    @Test
    void streamRecords_invokes_consumer_per_row() throws IOException {
        String csv = """
                site;date
                S01;2024-01-01
                S02;2024-01-02
                S03;2024-01-03
                """;
        AtomicInteger count = new AtomicInteger();
        try (Reader r = new StringReader(csv)) {
            service.streamRecords(r, format, rec -> {
                count.incrementAndGet();
                assertThat(rec.get("site")).startsWith("S");
            });
        }
        assertThat(count.get()).isEqualTo(3);
    }
}

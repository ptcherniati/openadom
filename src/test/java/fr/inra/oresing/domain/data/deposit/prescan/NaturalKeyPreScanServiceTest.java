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

    @Test
    void extractCompositeNaturalKeys_composes_single_column() throws IOException {
        String csv = """
                site;treatment;date
                S1;T1;2024-01-01
                S2;T2;2024-02-01
                S1;T3;2024-03-01
                """;
        try (Reader r = new StringReader(csv)) {
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, List.of("site"), "__");
            assertThat(nks).containsExactlyInAnyOrder("S1", "S2");
        }
    }

    @Test
    void extractCompositeNaturalKeys_composes_multi_column_with_separator() throws IOException {
        String csv = """
                site;treatment;date
                S1;T1;2024-01-01
                S2;T2;2024-02-01
                S1;T1;2024-03-01
                S3;T1;2024-04-01
                """;
        try (Reader r = new StringReader(csv)) {
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, List.of("site", "treatment"), "__");
            // S1__T1 ( dup ) , S2__T2 , S3__T1
            assertThat(nks).containsExactlyInAnyOrder("S1__T1", "S2__T2", "S3__T1");
        }
    }

    @Test
    void extractCompositeNaturalKeys_skips_entirely_empty_rows() throws IOException {
        String csv = """
                site;treatment
                S1;T1
                ;
                S2;T2
                """;
        try (Reader r = new StringReader(csv)) {
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, List.of("site", "treatment"), "__");
            assertThat(nks).containsExactlyInAnyOrder("S1__T1", "S2__T2");
        }
    }

    @Test
    void extractCompositeNaturalKeys_keeps_rows_with_one_filled_component() throws IOException {
        String csv = """
                site;treatment
                S1;
                ;T2
                """;
        try (Reader r = new StringReader(csv)) {
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, List.of("site", "treatment"), "__");
            // "S1__" et "__T2" sont conserves : ils ont au moins une composante
            // non-vide ( la coherence avec downstream ingestion est garantie
            // tant que le composing applique la meme regle ) .
            assertThat(nks).containsExactlyInAnyOrder("S1__", "__T2");
        }
    }

    @Test
    void extractCompositeNaturalKeys_returns_empty_for_null_or_empty_columns() throws IOException {
        String csv = "site;treatment\nS1;T1\n";
        try (Reader r = new StringReader(csv)) {
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, null, "__");
            assertThat(nks).isEmpty();
        }
        try (Reader r = new StringReader(csv)) {
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, List.of(), "__");
            assertThat(nks).isEmpty();
        }
    }

    @Test
    void extractCompositeNaturalKeys_null_separator_treated_as_empty() throws IOException {
        String csv = """
                a;b
                X;Y
                A;B
                """;
        try (Reader r = new StringReader(csv)) {
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, List.of("a", "b"), null);
            // separator null -> ""  => "XY" , "AB"
            assertThat(nks).containsExactlyInAnyOrder("XY", "AB");
        }
    }

    @Test
    void extractCompositeNaturalKeys_missing_column_treated_as_empty() throws IOException {
        String csv = """
                site
                S1
                S2
                """;
        try (Reader r = new StringReader(csv)) {
            // 'treatment' is absent ; composantes vide ; resultats "S1__" , "S2__"
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, List.of("site", "treatment"), "__");
            assertThat(nks).containsExactlyInAnyOrder("S1__", "S2__");
        }
    }

    // ============================================================
    // Regression : OA_dataHeaderLine / OA_dataFirstLine respect
    // ( ISSUE_GITLAB_PRESCAN_AXE_B_IGNORE_DATAHEADERLINE_2026-05-18 )
    // ============================================================

    /**
     * CSV "Excel-style" ACBB : 7 lignes metadata humain , header technique a
     * la ligne 8 , data a partir de la ligne 15 ( lignes 9-14 sont
     * description / type / obligatoire ) . Le prescan doit retourner les
     * naturalkeys composites des lignes data uniquement .
     */
    @Test
    void extractCompositeNaturalKeys_skips_metadata_lines_when_dataHeaderLine_set() throws IOException {
        String csv = ""
                + "Type de données :;localisation;;;\n"               // line 1
                + "Site d'étude :;theix;;;\n"                          // line 2
                + ";;;;\n"                                             // line 3
                + ";;;;\n"                                             // line 4
                + "commentaire :;;;;\n"                                // line 5
                + ";;;;\n"                                             // line 6
                + ";;;;\n"                                             // line 7
                + "type_zone;id_zone;zone_label_fr;parent_zone;area\n" // line 8 : real header
                + "Identifiant;Identifiant;Label;Parent;Area\n"        // line 9 : description
                + "Ref;texte;texte;Ref;numeric\n"                      // line 10 : type
                + "Obligatoire;Obligatoire;Facultatif;Facultatif;Facultatif\n" // line 11
                + ";;;;\n"                                             // line 12
                + ";;;;\n"                                             // line 13
                + ";;;;\n"                                             // line 14
                + "sit;acbb_theix;ACBB_THEIX;;100\n"                   // line 15 : data
                + "ilo;annexe;ANNEXE;acbb_theix;50\n"                  // line 16
                + "pex;p30;p30;annexe;10\n";                           // line 17
        try (Reader r = new StringReader(csv)) {
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, List.of("type_zone", "id_zone"), "__",
                    /* dataHeaderLine */ 8, /* dataFirstLine */ 15);
            // Only the 3 data lines should yield naturalkeys ;
            // description / type / obligatoire lines ( 9-11 ) are skipped .
            assertThat(nks).containsExactlyInAnyOrder(
                    "sit__acbb_theix",
                    "ilo__annexe",
                    "pex__p30");
        }
    }

    /**
     * BOM UTF-8 en tete du CSV : doit etre transparent pour la lecture
     * du header ( sans strip , la 1ere colonne s'appellerait
     * "﻿type_zone" et record.get("type_zone") retournerait null ) .
     */
    @Test
    void extractCompositeNaturalKeys_strips_utf8_bom_in_header() throws IOException {
        String csv = "﻿"
                + "type_zone;id_zone\n"
                + "sit;acbb_theix\n"
                + "ilo;annexe\n";
        try (Reader r = new StringReader(csv)) {
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, List.of("type_zone", "id_zone"), "__",
                    /* dataHeaderLine */ 1, /* dataFirstLine */ 2);
            assertThat(nks).containsExactlyInAnyOrder("sit__acbb_theix", "ilo__annexe");
        }
    }

    /**
     * BOM + metadata Excel-style : combinaison du test ACBB reel . Le BOM
     * est sur la ligne 1 ( metadata , skippee ) ; la ligne 8 ( header )
     * elle ne porte pas de BOM mais le strip doit etre idempotent .
     */
    @Test
    void extractCompositeNaturalKeys_bom_on_skipped_metadata_does_not_break_header() throws IOException {
        String csv = "﻿"
                + "Type de données :;localisation;\n"  // line 1 : BOM + metadata
                + ";;\n"
                + ";;\n"
                + ";;\n"
                + ";;\n"
                + ";;\n"
                + ";;\n"
                + "type_zone;id_zone\n"                 // line 8 : header
                + "Description;Description\n"           // line 9 : description ( skipped )
                + "sit;theix\n"                         // line 10 : data
                + "ilo;annexe\n";                       // line 11 : data
        try (Reader r = new StringReader(csv)) {
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, List.of("type_zone", "id_zone"), "__",
                    /* dataHeaderLine */ 8, /* dataFirstLine */ 10);
            assertThat(nks).containsExactlyInAnyOrder("sit__theix", "ilo__annexe");
        }
    }

    /**
     * Backward-compat : dataHeaderLine null ou 1 doit produire le meme
     * resultat que l'overload sans dataHeaderLine ( header sur ligne 1 ,
     * pas de skip ) .
     */
    @Test
    void extractCompositeNaturalKeys_null_dataHeaderLine_is_legacy() throws IOException {
        String csv = """
                site;treatment
                S1;T1
                S2;T2
                """;
        Set<String> legacy;
        try (Reader r = new StringReader(csv)) {
            legacy = service.extractCompositeNaturalKeys(
                    r, format, List.of("site", "treatment"), "__");
        }
        Set<String> nullHeaderLine;
        try (Reader r = new StringReader(csv)) {
            nullHeaderLine = service.extractCompositeNaturalKeys(
                    r, format, List.of("site", "treatment"), "__",
                    null, null);
        }
        Set<String> oneHeaderLine;
        try (Reader r = new StringReader(csv)) {
            oneHeaderLine = service.extractCompositeNaturalKeys(
                    r, format, List.of("site", "treatment"), "__",
                    1, 2);
        }
        assertThat(legacy).containsExactlyInAnyOrder("S1__T1", "S2__T2");
        assertThat(nullHeaderLine).isEqualTo(legacy);
        assertThat(oneHeaderLine).isEqualTo(legacy);
    }

    /**
     * Edge case : CSV plus court que dataHeaderLine ( fichier vide ou
     * tronque ) -> empty set retourne , pas d'exception .
     */
    @Test
    void extractCompositeNaturalKeys_csv_shorter_than_dataHeaderLine_returns_empty() throws IOException {
        String csv = "line1\nline2\nline3\n";
        try (Reader r = new StringReader(csv)) {
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, List.of("site", "treatment"), "__",
                    /* dataHeaderLine */ 8, /* dataFirstLine */ 15);
            assertThat(nks).isEmpty();
        }
    }

    /**
     * dataFirstLine egal a dataHeaderLine+1 ( cas standard sans lignes
     * description ) : aucune ligne data perdue .
     */
    @Test
    void extractCompositeNaturalKeys_dataFirstLine_immediately_after_header() throws IOException {
        String csv = ""
                + "skipme\n"
                + "site;treatment\n"   // line 2 : header
                + "S1;T1\n"            // line 3 : data
                + "S2;T2\n";
        try (Reader r = new StringReader(csv)) {
            Set<String> nks = service.extractCompositeNaturalKeys(
                    r, format, List.of("site", "treatment"), "__",
                    /* dataHeaderLine */ 2, /* dataFirstLine */ 3);
            assertThat(nks).containsExactlyInAnyOrder("S1__T1", "S2__T2");
        }
    }

    /**
     * Verifie que le decorator {@link NaturalKeyPreScanService.BomStrippingReader}
     * laisse passer le contenu intact quand il n'y a PAS de BOM en tete
     * ( idempotent ) .
     */
    @Test
    void bomStrippingReader_passes_content_unchanged_without_bom() throws IOException {
        String content = "header1;header2\nfoo;bar\n";
        try (Reader r = new NaturalKeyPreScanService.BomStrippingReader(new StringReader(content))) {
            char[] buf = new char[content.length() + 4];
            int read = r.read(buf, 0, buf.length);
            assertThat(read).isEqualTo(content.length());
            assertThat(new String(buf, 0, read)).isEqualTo(content);
        }
    }

    /**
     * Verifie que le decorator strip le BOM UTF-8 ( ﻿ ) en tete et
     * laisse passer le reste intact .
     */
    @Test
    void bomStrippingReader_strips_leading_bom() throws IOException {
        String content = "header1;header2\nfoo;bar\n";
        try (Reader r = new NaturalKeyPreScanService.BomStrippingReader(
                new StringReader("﻿" + content))) {
            char[] buf = new char[content.length() + 8];
            int total = 0;
            int n;
            while ((n = r.read(buf, total, buf.length - total)) > 0) {
                total += n;
            }
            assertThat(new String(buf, 0, total)).isEqualTo(content);
        }
    }
}

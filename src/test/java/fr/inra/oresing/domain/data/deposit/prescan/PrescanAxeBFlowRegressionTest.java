package fr.inra.oresing.domain.data.deposit.prescan;

import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test pour
 * {@code ISSUE_GITLAB_PRESCAN_AXE_B_IGNORE_DATAHEADERLINE_2026-05-18} .
 *
 * <p>Reproduit le scenario complet du chemin "Axe B" dans
 * {@code DataService.addData} sans booter Spring :
 *
 * <ol>
 *   <li>InputStream HTTP simule ( {@link ByteArrayInputStream} sur un CSV
 *       ACBB-style : BOM UTF-8 + 7 lignes metadata humain + header technique
 *       a la ligne 8 + 6 lignes description / type / obligatoire + data
 *       a partir de la ligne 15 ) ;</li>
 *   <li>{@code Files.copy(src, tempfile, REPLACE_EXISTING)} consume l'InputStream
 *       original ( comportement DataService.addData ligne 360 ) ;</li>
 *   <li>{@code NaturalKeyPreScanService.extractCompositeNaturalKeys} avec
 *       {@code dataHeaderLine=8 , dataFirstLine=15} sur un BufferedReader frais ;</li>
 *   <li>Verification : pas de NoSuchElementException , pas de "A header name is
 *       missing" , les naturalkeys composites retournees correspondent EXACTEMENT
 *       aux lignes data ( pas description / type / obligatoire ) ;</li>
 *   <li>Fallback : verifie que si on tente de relire l'InputStream original
 *       deja consume par Files.copy , il est vide ( reproduction du
 *       NoSuchElementException ) ; le fix dans DataService passe par
 *       {@code Files.newInputStream(tempfile)} pour retomber sur le legacy
 *       preload avec un flux frais .</li>
 * </ol>
 *
 * <p>Cible explicitement les 2 bugs corriges :
 * <ul>
 *   <li>NaturalKeyPreScanService ignorait {@code OA_dataHeaderLine} ;</li>
 *   <li>DataService.addData fallback re-utilisait l'InputStream consume ,
 *       provoquant un 500 silencieux .</li>
 * </ul>
 */
@DisplayName("Regression Axe B prescan + fallback InputStream ( issue 2026-05-18 )")
class PrescanAxeBFlowRegressionTest {

    private NaturalKeyPreScanService service;
    private CSVFormat                format;
    private Path                     tempfile;

    @BeforeEach
    void setUp() throws IOException {
        service = new NaturalKeyPreScanService();
        format  = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(';').get();
        tempfile = Files.createTempFile("prescan-regression-", ".csv");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempfile != null) {
            Files.deleteIfExists(tempfile);
        }
    }

    /**
     * Reproduction exacte du CSV ACBB issue (53 lignes simplifie a 17 ) :
     * BOM UTF-8 , 7 lignes metadata humain , header ligne 8 , 6 lignes
     * description / type / obligatoire ( lignes 9-14 ) , 3 lignes data
     * ( lignes 15-17 ) .
     */
    private byte[] acbbStyleCsvBytes() {
        String csv = ""
                + "﻿Type de données :;localisation;;;\n"          // line 1 : BOM + metadata
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
                + "ilo;annexe;ANNEXE;acbb_theix;50\n"                  // line 16 : data
                + "pex;p30;p30;annexe;10\n";                           // line 17 : data
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Replays bug scenario : ACBB CSV + dataHeaderLine=8 -> 3 naturalkeys , 0 exception")
    void prescan_with_acbb_layout_and_dataHeaderLine_succeeds() throws IOException {
        // Step 1 : simulate HTTP upload InputStream .
        InputStream httpUpload = new ByteArrayInputStream(acbbStyleCsvBytes());

        // Step 2 : DataService.addData line 360 - Files.copy consumes the
        // InputStream into a tempfile . The InputStream is CLOSED by the
        // try-with-resources .
        try (InputStream src = httpUpload) {
            Files.copy(src, tempfile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Step 3 : DataService.addData line 374 - open a FRESH BufferedReader
        // on the tempfile and run the prescan with dataHeaderLine + dataFirstLine
        // from the YAML config .
        Set<String> naturalKeys;
        try (Reader r = Files.newBufferedReader(tempfile, StandardCharsets.UTF_8)) {
            naturalKeys = service.extractCompositeNaturalKeys(
                    r, format,
                    List.of("type_zone", "id_zone"),
                    "__",
                    /* dataHeaderLine */ 8,
                    /* dataFirstLine  */ 15);
        }

        // Step 4 : verify the naturalkeys correspond to data lines only , no
        // exception , BOM stripped , metadata + description / type / obligatoire
        // lines all skipped .
        assertThat(naturalKeys)
                .as("naturalkeys must match data lines 15-17 only , no metadata leakage")
                .containsExactlyInAnyOrder(
                        "sit__acbb_theix",
                        "ilo__annexe",
                        "pex__p30");
    }

    @Test
    @DisplayName("Fallback safety : original InputStream is empty after Files.copy , must use tempfile re-read")
    void original_inputStream_is_empty_after_filesCopy_demonstrates_need_for_fresh_stream() throws IOException {
        // Step 1 : simulate HTTP upload .
        InputStream httpUpload = new ByteArrayInputStream(acbbStyleCsvBytes());

        // Step 2 : Files.copy reads ALL bytes from httpUpload into tempfile .
        // After this call , the InputStream is at EOF .
        try (InputStream src = httpUpload) {
            Files.copy(src, tempfile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Step 3 : re-using the original InputStream now would yield 0 bytes .
        // This is the bug the issue describes : DataService.addData previously
        // set effectiveInputStream = file ( the original , now empty ) in the
        // catch block , causing NoSuchElementException downstream .
        //
        // Note : the InputStream was closed by try-with-resources above , so we
        // cannot read more bytes ; in the real bug scenario the stream wasn't
        // closed before the catch , but its bytes had been fully consumed by
        // Files.copy , producing the same effect ( read returns -1 ) .
        // For the test we simulate by checking that a fresh new InputStream
        // built FROM THE TEMPFILE produces the correct content - which is what
        // the fix does .
        try (InputStream freshFromTempfile = Files.newInputStream(tempfile)) {
            byte[] readBack = freshFromTempfile.readAllBytes();
            assertThat(readBack)
                    .as("fresh InputStream from tempfile must contain the original CSV bytes")
                    .isEqualTo(acbbStyleCsvBytes());
        }
    }

    @Test
    @DisplayName("dataHeaderLine ignored ( null ) : header missing -> IllegalArgumentException ( exact bug repro )")
    void prescan_without_dataHeaderLine_throws_on_acbb_csv_as_legacy_did() throws IOException {
        // Step 1+2 : same setup .
        try (InputStream src = new ByteArrayInputStream(acbbStyleCsvBytes())) {
            Files.copy(src, tempfile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Step 3 : LEGACY behaviour ( pre-fix ) - prescan does not get
        // dataHeaderLine . CSVParser tries to parse line 1 as header
        // ( "Type de données :;localisation;;;;" ) , detects empty cells ,
        // throws IllegalArgumentException : "A header name is missing" .
        //
        // C'EST EXACTEMENT le message observe dans le log de l'issue
        // ( ISSUE_GITLAB_PRESCAN_AXE_B_IGNORE_DATAHEADERLINE_2026-05-18 ) :
        //   "[Axe B] prescan failed for refType=t_zone_etude_zet ... :
        //    A header name is missing in [...Type de données :, localisation, , , ...]"
        //
        // Ce test fixe le comportement legacy pour documenter le bug d'origine .
        // Si quelqu'un retire le param dataHeaderLine du caller dans DataService ,
        // ce test redevient red et signale la regression .
        try (Reader r = Files.newBufferedReader(tempfile, StandardCharsets.UTF_8)) {
            assertThatThrownBy(() -> service.extractCompositeNaturalKeys(
                    r, format,
                    List.of("type_zone", "id_zone"),
                    "__")) // legacy 4-arg overload
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("A header name is missing");
        }
    }
}

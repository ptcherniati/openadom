package fr.inra.oresing.domain.data;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.inra.oresing.persistence.JsonRowMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

class TsvLineRecordTest {
    public static final String[] ORDERED_COLUMNS = new String[]{
            "id",
            "patternColumnName",
            "application",
            "ReferenceType",
            "hierarchicalKey",
            "naturalKey",
            "refsLinkedTo",
            "refValues",
            "binaryFile",
            "authorization"};
    public static final String dataValueJson = """
            {
              "id" : "d526f970-70a9-44ae-a389-518271b0d4ce",
              "creationdate" : null,
              "updatedate" : null,
              "application" : "4819c5db-3da3-4e43-9e51-915a79bd54df",
              "patterncolumnname" : "",
              "hierarchicalkey" : "especesKlpf",
              "naturalkey" : "lpf",
              "refvalues" : {
                "esp_nom" : "LPF",
                "esp_definition_fr" : "LPF",
                "esp_definition_en" : "LPF",
                "colonne_homonyme_entre_referentiels" : "",
                "my_computed_column" : "my value",
                "__display_en" : "LPF",
                "__display_fr" : "LPF",
                "__display_default" : "LPF",
                "__display_description_en" : "LPF",
                "__display_description_fr" : "LPF",
                "__display_description_default" : "LPF"
              },
              "refslinkedto" : { },
              "binaryfile" : "381dd6f8-a268-45df-ba60-8c61ae357a77",
              "referencingreferences" : null,
              "linehierarchicalkeypatterncolumnname" : null,
              "linenaturalkeypatterncolumnname" : null,
              "authorization" : {
                "timescope" : "(,)",
                "requiredauthorizations" : null
              },
              "referencetype" : "especes"
            }""";
    public static final JsonRowMapper mapper = new JsonRowMapper();


    @Test
    public void testToLine() throws IOException {
        DataValue dataValue = (DataValue) mapper.readValue(dataValueJson, DataValue.class);
        final Map<String, String> map = mapper.getJsonMapper().readValue(mapper.toJson(dataValue), new TypeReference<Map<String, String>>() {
        });
        final TsvLineRecord tsvLineRecord = TsvLineRecord.of(
                map, ORDERED_COLUMNS, mapper
        );
        final String line = tsvLineRecord.line();
        System.out.println(line);
    }

}
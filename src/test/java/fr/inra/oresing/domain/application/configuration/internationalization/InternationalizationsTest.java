package fr.inra.oresing.domain.application.configuration.internationalization;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.Locale;
import java.util.Map;

@Tag("core.config")
@Tag("domain.model")
class InternationalizationsTest {

    @Test
    void testInternationalizationTitleBuilder() {
        Map<Locale, String> title = Map.of(Locale.FRENCH, "Titre");
        Map<Locale, String> description = Map.of(Locale.FRENCH, "Description");

        InternationalizationTitle i18nTitle = InternationalizationsBuilder.internationalizationTitle()
                .title(title)
                .description(description)
                .build();

        Assertions.assertEquals(title, i18nTitle.getTitle());
        Assertions.assertEquals(description, i18nTitle.getDescription());
    }

    @Test
    void testInternationalizationComponentBuilder() {
        InternationalizationTitle title = InternationalizationsBuilder.internationalizationTitle().build();
        InternationalizationComponent component = InternationalizationsBuilder.internationalizationComponent()
                .exportHeader(title)
                .build();

        Assertions.assertEquals(title, component.getExportHeader());
    }

    @Test
    void testInternationalizationSubmissionComponentBuilder() {
        Map<String, InternationalizationTitle> scopes = Map.of("scope1", InternationalizationsBuilder.internationalizationTitle().build());
        InternationalizationSubmissionComponent component = InternationalizationsBuilder.internationalizationSubmissionComponent()
                .referenceScopes(scopes)
                .build();

        Assertions.assertEquals(scopes, component.getReferenceScopes());
    }

    @Test
    void testInternationalizationDataBuilder() {
        Map<String, InternationalizationComponent> components = Map.of("comp1", InternationalizationsBuilder.internationalizationComponent().build());
        InternationalizationData data = InternationalizationsBuilder.internationalizationData()
                .components(components)
                .build();

        Assertions.assertEquals(components, data.getComponents());
    }

    @Test
    void testInternationalizationRightrequestBuilder() {
        Map<String, InternationalizationTitle> fields = Map.of("field1", InternationalizationsBuilder.internationalizationTitle().build());
        InternationalizationRightrequest request = InternationalizationsBuilder.internationalizationRightrequest()
                .fields(fields)
                .build();

        Assertions.assertEquals(fields, request.getFields());
    }

    @Test
    void testInternationalizationAdditionalFileBuilder() {
        Map<String, InternationalizationTitle> fields = Map.of("field1", InternationalizationsBuilder.internationalizationTitle().build());
        InternationalizationAdditionalFile file = InternationalizationsBuilder.internationalizationAdditionalFile()
                .fields(fields)
                .build();

        Assertions.assertEquals(fields, file.getFields());
    }

    @Test
    void testInternationalizationsBuilder() {
        Map<String, InternationalizationData> data = Map.of("data1", InternationalizationsBuilder.internationalizationData().build());
        Internationalizations i18n = InternationalizationsBuilder.internationalizations()
                .data(data)
                .build();

        Assertions.assertEquals(data, i18n.getData());
    }
}
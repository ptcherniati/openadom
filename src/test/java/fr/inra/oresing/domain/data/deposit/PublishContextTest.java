package fr.inra.oresing.domain.data.deposit;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.deposit.PublishContext.HeaderInfos;
import fr.inra.oresing.domain.data.deposit.PublishContext.RowInfos;
import fr.inra.oresing.domain.file.FileOrUUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublishContextTest {
    public static final String DATA_NAME = "dataName";
    private FileOrUUID fileOrUUID;
    private List<List<String>> preHeaderRow;
    private List<List<String>> postHeaderRow;
    private List<String> headerRow;
    private List<String> currentRow = List.of("val3", "val4");
    private HeaderInfos headerInfos;
    private RowInfos rowInfos;
    private Application application = Mockito.mock(Application.class);
    private Function getDataByReference = Mockito.mock(Function.class);

    @BeforeEach
    public void setUp() {
        // Création des mocks ou objets nécessaires
        this.fileOrUUID = Mockito.mock(FileOrUUID.class); // À adapter selon votre constructeur
        this.preHeaderRow = List.of(List.of("A", "B"), List.of("C", "D"), List.of("E", "F"));
        this.postHeaderRow = List.of(List.of("G"), List.of("h", "I"));
        this.headerRow = List.of("col1", "col2");
        this.headerInfos = new HeaderInfos(preHeaderRow, postHeaderRow, headerRow);
        this.rowInfos = new RowInfos(java.util.List.of("val1", "val2"), 1L);

    }

    @Test
    void testPublishContextCreation() {

        // Création de l'instance à tester
        PublishContext context = new PublishContext(fileOrUUID, headerInfos, rowInfos);

        // Vérifications
        testContext(context);
    }

    private void testContext(PublishContext context) {
        Assertions.assertThat(context)
                .isNotNull()
                .hasFieldOrPropertyWithValue("headerInfos", headerInfos)
                .hasFieldOrPropertyWithValue("rowInfos", rowInfos)
                .hasFieldOrPropertyWithValue("fileOrUUID", fileOrUUID)
                .hasFieldOrPropertyWithValue("headerInfos.preHeaderRow", preHeaderRow)
                .hasFieldOrPropertyWithValue("headerInfos.postHeaderRow", postHeaderRow)
                .hasFieldOrPropertyWithValue("headerInfos.headerRow", headerRow)
                .hasFieldOrPropertyWithValue("rowInfos.currentRow", List.of("val1", "val2"))
                .hasFieldOrPropertyWithValue("rowInfos.currentRowNumber", 1L);
    }

    private void testContext2(PublishContext context) {
        Assertions.assertThat(context)
                .isNotNull()
                .hasFieldOrPropertyWithValue("headerInfos", headerInfos)
                .hasFieldOrPropertyWithValue("fileOrUUID", fileOrUUID)
                .hasFieldOrPropertyWithValue("headerInfos.preHeaderRow", preHeaderRow)
                .hasFieldOrPropertyWithValue("headerInfos.postHeaderRow", postHeaderRow)
                .hasFieldOrPropertyWithValue("headerInfos.headerRow", headerRow)
                .hasFieldOrPropertyWithValue("rowInfos.currentRow", currentRow)
                .hasFieldOrPropertyWithValue("rowInfos.currentRowNumber", 4L);
    }

    @Test
    void testRowInfosValidation() {
        List<String> currentRow = List.of("val1", "val2");
        long validLineNumber = 1L;
        assertDoesNotThrow(() -> new RowInfos(currentRow, validLineNumber));
        // Test d'une valeur invalide
        assertThrows(IllegalArgumentException.class, () -> new RowInfos(currentRow, 0L));
    }

    @Test
    void PublishContextBuilderTest() {
        final PublishContext context = buildContext();
        final PublishContext context2 = buildContext2();
        testContext(context);
        testContext2(context2);
    }

    private PublishContext buildContext() {
        final PublishContext.PublishContextBuilder publishContextBuilder = new PublishContext.PublishContextBuilder(
                application,
                DATA_NAME,
                fileOrUUID,
                getDataByReference
        );
        BinaryFileDataset binaryFileDataset = Mockito.mock(BinaryFileDataset.class);
        Mockito.when(fileOrUUID.binaryfiledataset()).thenReturn(binaryFileDataset);
        Assertions.assertThat(publishContextBuilder.binaryFileDataset())
                .isEqualTo(binaryFileDataset);

        return publishContextBuilder
                .withPreHeaderRow(preHeaderRow)
                .withHeaderRow(headerRow)
                .withPostHeaderRow(postHeaderRow)
                .withRowInfos(rowInfos)
                .build();
    }

    private PublishContext buildContext2() {
        final PublishContext.PublishContextBuilder publishContextBuilder = new PublishContext.PublishContextBuilder(
                application,
                DATA_NAME,
                fileOrUUID,
                getDataByReference
        );
        BinaryFileDataset binaryFileDataset = Mockito.mock(BinaryFileDataset.class);
        Mockito.when(fileOrUUID.binaryfiledataset()).thenReturn(binaryFileDataset);
        Assertions.assertThat(publishContextBuilder.binaryFileDataset())
                .isEqualTo(binaryFileDataset);

        return publishContextBuilder
                .withPreHeaderRow(preHeaderRow)
                .withHeaderRow(headerRow)
                .withPostHeaderRow(postHeaderRow)
                .build(currentRow,4L );
    }
}
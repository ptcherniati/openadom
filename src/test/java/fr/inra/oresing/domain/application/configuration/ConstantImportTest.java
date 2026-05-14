package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

@Tag("core.config")
@Tag("domain.model")
class ConstantImportTest {

    @Test
    void testSubmissionConstantHeaderBuilder() {
        SubmissionConstantHeader header = ConstantImportBuilder.submissionConstantHeader().build();
        Assertions.assertEquals(ConstantImportHeader.ConstantImportHeaderType.SubmissionComponent, header.type());
    }

    @Test
    void testFileColumnConstantHeaderBuilder() {
        FileColumnConstantHeader header = ConstantImportBuilder.fileColumnConstantHeader()
                .rowNumber(1)
                .columnNumber(2)
                .build();
        Assertions.assertEquals(ConstantImportHeader.ConstantImportHeaderType.FileConstantHeader, header.type());
        Assertions.assertEquals(1, header.rowNumber());
        Assertions.assertEquals(2, header.columnNumber());
    }

    @Test
    void testColumnConstantHeaderByColumnNumberBuilder() {
        ColumnConstantHeaderByColumnNumber header = ConstantImportBuilder.columnConstantHeaderByColumnNumber()
                .rowNumber(1)
                .columnNumber(2)
                .build();
        Assertions.assertEquals(ConstantImportHeader.ConstantImportHeaderType.ColumnConstantHeaderByColumnNumber, header.type());
        Assertions.assertEquals(1, header.rowNumber());
        Assertions.assertEquals(2, header.columnNumber());
    }

    @Test
    void testColumnConstantHeaderByHeaderNameBuilder() {
        List<Locale> locales = List.of(Locale.FRENCH);
        ColumnConstantHeaderByHeaderName header = ConstantImportBuilder.columnConstantHeaderByHeaderName()
                .rowNumber(1)
                .headerName("myHeader")
                .langRestrictions(locales)
                .build();
        Assertions.assertEquals(ConstantImportHeader.ConstantImportHeaderType.ColumnConstantHeaderByHeaderName, header.type());
        Assertions.assertEquals(1, header.rowNumber());
        Assertions.assertEquals("myHeader", header.headerName());
        Assertions.assertEquals(locales, header.langRestrictions());
    }
}
package fr.inra.oresing.domain.exceptions;

import fr.inra.oresing.domain.exceptions.application.BadLabelNameException;
import fr.inra.oresing.domain.exceptions.application.NoSuchApplicationException;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.binaryfile.binaryfile.BadFileOrUUIDQuery;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.domain.exceptions.data.data.BadBinaryFileDatasetQuery;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import fr.inra.oresing.domain.exceptions.data.data.DeleteOnrepositoryApplicationNotAllowedException;
import fr.inra.oresing.domain.exceptions.data.data.UnloadDataCsvFileException;
import fr.inra.oresing.domain.exceptions.role.role.BadApplicationRoleException;
import fr.inra.oresing.domain.exceptions.role.role.BadRoleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des classes d'exceptions du domaine.
 */
@Tag("domain.model")
@DisplayName("Domain exceptions — constructeurs, messages et constantes")
class DomainExceptionsExtendedTest {

    // ─── OreSiTechnicalException ─────────────────────────────────────────────

    @Nested
    @DisplayName("OreSiTechnicalException")
    class OreSiTechnicalExceptionTest {
        @Test
        void messageConstructor() {
            OreSiTechnicalException ex = new OreSiTechnicalException("CODE");
            assertThat(ex.getMessage()).isEqualTo("CODE");
        }

        @Test
        void nullMessageUsesConstant() {
            OreSiTechnicalException ex = new OreSiTechnicalException(null);
            assertThat(ex.getMessage()).isEqualTo(OreSiTechnicalException.NO_MESSAGE);
        }

        @Test
        void causeConstructor() {
            RuntimeException cause = new RuntimeException("root");
            OreSiTechnicalException ex = new OreSiTechnicalException("CODE", cause);
            assertThat(ex.getCause()).isSameAs(cause);
            assertThat(ex.getMessage()).isEqualTo("CODE");
        }
    }

    // ─── SiOreIllegalArgumentException ──────────────────────────────────────

    @Nested
    @DisplayName("SiOreIllegalArgumentException")
    class SiOreIllegalArgumentExceptionTest {
        @Test
        void constructorStoresFields() {
            var ex = new SiOreIllegalArgumentException("myCode", Map.of("k", "v"));
            assertThat(ex.getMessage()).isEqualTo("myCode");
            assertThat(ex.getParams()).containsEntry("k", "v");
        }

        @Test
        void noRightOnTable() {
            var ex = SiOreIllegalArgumentException.noRightOnTable("myTable");
            assertThat(ex.getMessage()).isEqualTo(SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE);
            assertThat(ex.getParams()).containsEntry(SiOreIllegalArgumentException.TABLE, "myTable");
        }

        @Test
        void noRightOnTableForDeposit() {
            var ex = SiOreIllegalArgumentException.noRightOnTableForDeposit("t");
            assertThat(ex.getMessage()).isEqualTo(SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE_FOR_DEPOSIT);
        }

        @Test
        void noRightOnTableForPublishOrUnpublish() {
            var ex = SiOreIllegalArgumentException.noRightOnTableForPublishOrUnpublish("t");
            assertThat(ex.getMessage()).isEqualTo(SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE_FOR_PUBLISH_OR_UNPUBLISH);
        }

        @Test
        void noRightOnTableForDelete() {
            var ex = SiOreIllegalArgumentException.noRightOnTableForDelete("t");
            assertThat(ex.getMessage()).isEqualTo(SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE_FOR_DELETE);
        }

        @Test
        void noRightOnTableForDeleteFromException() {
            var src = new SiOreIllegalArgumentException("x", Map.of(SiOreIllegalArgumentException.TABLE, "tbl"));
            var ex = SiOreIllegalArgumentException.noRightOnTableForDelete(src);
            assertThat(ex.getParams()).containsEntry(SiOreIllegalArgumentException.TABLE, "tbl");
        }

        @Test
        void noRightOnTableForPublishFromExceptionWithNullParams() {
            var src = new SiOreIllegalArgumentException("x", null);
            var ex = SiOreIllegalArgumentException.noRightOnTableForPublishOrUnpublish(src);
            assertThat(ex.getParams()).containsEntry(SiOreIllegalArgumentException.TABLE, SiOreIllegalArgumentException.NOT_GIVEN);
        }
    }

    // ─── BadLabelNameException ───────────────────────────────────────────────

    @Nested
    @DisplayName("BadLabelNameException")
    class BadLabelNameExceptionTest {
        @Test
        void constructorStoresLabelType() {
            BadLabelNameException ex = new BadLabelNameException(BadLabelNameException.LabelType.APPLICATION, "name", "arg1");
            assertThat(ex.getLabelType()).isEqualTo(BadLabelNameException.LabelType.APPLICATION);
            assertThat(ex.getArgs()).contains("arg1");
            assertThat(ex.getMessage()).isEqualTo("name");
        }

        @Test
        void labelTypeValues() {
            assertThat(BadLabelNameException.LabelType.values()).hasSize(3);
        }
    }

    // ─── NoSuchApplicationException ─────────────────────────────────────────

    @Nested
    @DisplayName("NoSuchApplicationException")
    class NoSuchApplicationExceptionTest {
        @Test
        void constructorStoresNameAndMessage() {
            NoSuchApplicationException ex = new NoSuchApplicationException("myApp");
            assertThat(ex.getNameOrId()).isEqualTo("myApp");
            assertThat(ex.getMessage()).contains("myApp");
        }
    }

    // ─── AuthorizationRequestException ──────────────────────────────────────

    @Nested
    @DisplayName("AuthorizationRequestException")
    class AuthorizationRequestExceptionTest {
        @Test
        void messageIsNotBlank() {
            for (AuthorizationRequestException e : AuthorizationRequestException.values()) {
                assertThat(e.getMessage()).isNotBlank();
            }
        }

        @Test
        void allValues() {
            assertThat(AuthorizationRequestException.values()).hasSizeGreaterThan(3);
        }
    }

    // ─── SiOreAuthorizationRequestException ─────────────────────────────────

    @Nested
    @DisplayName("SiOreAuthorizationRequestException")
    class SiOreAuthorizationRequestExceptionTest {
        @Test
        void constructorStoresFields() {
            var ex = new SiOreAuthorizationRequestException(
                    AuthorizationRequestException.NO_AUTHORIZATION_NAME,
                    Map.of("key", "val"));
            assertThat(ex.getException()).isEqualTo(AuthorizationRequestException.NO_AUTHORIZATION_NAME);
            assertThat(ex.getParams()).containsEntry("key", "val");
        }
    }

    // ─── ConfigurationException ──────────────────────────────────────────────

    @Nested
    @DisplayName("ConfigurationException")
    class ConfigurationExceptionTest {
        @Test
        void messageIsNotBlank() {
            for (ConfigurationException e : ConfigurationException.values()) {
                assertThat(e.getMessage()).isNotBlank();
            }
        }

        @Test
        void hasManyValues() {
            assertThat(ConfigurationException.values()).hasSizeGreaterThan(10);
        }
    }

    // ─── BadApplicationConfigurationException ───────────────────────────────

    @Nested
    @DisplayName("BadApplicationConfigurationException")
    class BadApplicationConfigurationExceptionTest {
        @Test
        void constructorStoresFields() {
            BadApplicationConfigurationException ex = new BadApplicationConfigurationException(
                    "msg", ConfigurationException.exception);
            assertThat(ex.getMessage()).isEqualTo("msg");
            assertThat(ex.getConfigurationException()).isEqualTo(ConfigurationException.exception);
        }
    }

    // ─── BadFileOrUUIDQuery ──────────────────────────────────────────────────

    @Nested
    @DisplayName("BadFileOrUUIDQuery")
    class BadFileOrUUIDQueryTest {
        @Test
        void constructor() {
            BadFileOrUUIDQuery ex = new BadFileOrUUIDQuery("ERR");
            assertThat(ex.getMessage()).isEqualTo("ERR");
            assertThat(ex).isInstanceOf(OreSiTechnicalException.class);
        }
    }

    // ─── BadBinaryFileDatasetQuery ───────────────────────────────────────────

    @Nested
    @DisplayName("BadBinaryFileDatasetQuery")
    class BadBinaryFileDatasetQueryTest {
        @Test
        void constructor() {
            BadBinaryFileDatasetQuery ex = new BadBinaryFileDatasetQuery("ERR2");
            assertThat(ex.getMessage()).isEqualTo("ERR2");
        }
    }

    // ─── BadDownloadDatasetQuery ─────────────────────────────────────────────

    @Nested
    @DisplayName("BadDownloadDatasetQuery")
    class BadDownloadDatasetQueryTest {
        @Test
        void constants() {
            assertThat(BadDownloadDatasetQuery.MISSING_INTERVAL_VALUE).isNotBlank();
            assertThat(BadDownloadDatasetQuery.MISSING_TYPE_FOR_INTERVAL_VALUE).isNotBlank();
            assertThat(BadDownloadDatasetQuery.MISSING_FILTER).isNotBlank();
        }

        @Test
        void constructorWithMessage() {
            BadDownloadDatasetQuery ex = new BadDownloadDatasetQuery(BadDownloadDatasetQuery.MISSING_FILTER);
            assertThat(ex.getMessage()).isEqualTo(BadDownloadDatasetQuery.MISSING_FILTER);
        }
    }

    // ─── DeleteOnrepositoryApplicationNotAllowedException ───────────────────

    @Nested
    @DisplayName("DeleteOnrepositoryApplicationNotAllowedException")
    class DeleteOnrepositoryApplicationNotAllowedExceptionTest {
        @Test
        void constructor() {
            DeleteOnrepositoryApplicationNotAllowedException ex = new DeleteOnrepositoryApplicationNotAllowedException();
            assertThat(ex.getMessage()).isNotBlank();
            assertThat(ex).isInstanceOf(OreSiTechnicalException.class);
        }
    }

    // ─── UnloadDataCsvFileException ──────────────────────────────────────────

    @Nested
    @DisplayName("UnloadDataCsvFileException")
    class UnloadDataCsvFileExceptionTest {
        @Test
        void constructor() {
            Exception cause = new Exception("root cause");
            UnloadDataCsvFileException ex = new UnloadDataCsvFileException(cause);
            assertThat(ex.getMessage()).isNotBlank();
            assertThat(ex).isInstanceOf(OreSiTechnicalException.class);
        }
    }

    // ─── BadRoleException ────────────────────────────────────────────────────

    @Nested
    @DisplayName("BadRoleException")
    class BadRoleExceptionTest {
        @Test
        void constructorWithMessage() {
            BadRoleException ex = new BadRoleException("MSG", "my_role");
            assertThat(ex.getMessage()).isEqualTo("MSG");
            assertThat(ex.getRole()).isEqualTo("my_role");
        }

        @Test
        void constructorWithCause() {
            RuntimeException cause = new RuntimeException("c");
            BadRoleException ex = new BadRoleException("MSG", "r", cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    // ─── BadApplicationRoleException ────────────────────────────────────────

    @Nested
    @DisplayName("BadApplicationRoleException")
    class BadApplicationRoleExceptionTest {
        @Test
        void constructorWithMessage() {
            BadApplicationRoleException ex = new BadApplicationRoleException("MSG", "role", null);
            assertThat(ex.getMessage()).isEqualTo("MSG");
        }

        @Test
        void constructorWithCause() {
            RuntimeException cause = new RuntimeException("c");
            BadApplicationRoleException ex = new BadApplicationRoleException("MSG", "role", cause, null);
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }
}

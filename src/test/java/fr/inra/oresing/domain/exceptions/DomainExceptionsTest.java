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
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires purs de toutes les classes d'exceptions du domaine.
 * Aucun contexte Spring.
 */
@Tag("domain.model")
class DomainExceptionsTest {

    // ------------------------------------------------------------------ //
    //  OreSiTechnicalException                                             //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("OreSiTechnicalException")
    class OreSiTechnicalExceptionTest {

        @Test
        void messageConstructor() {
            OreSiTechnicalException e = new OreSiTechnicalException("error");
            assertThat(e.getMessage()).isEqualTo("error");
            assertThat(e).isInstanceOf(RuntimeException.class);
        }

        @Test
        void nullMessageFallsBackToNoMessage() {
            OreSiTechnicalException e = new OreSiTechnicalException(null);
            assertThat(e.getMessage()).isEqualTo(OreSiTechnicalException.NO_MESSAGE);
        }

        @Test
        void messageWithCauseConstructor() {
            Throwable cause = new RuntimeException("root");
            OreSiTechnicalException e = new OreSiTechnicalException("wrapped", cause);
            assertThat(e.getMessage()).isEqualTo("wrapped");
            assertThat(e.getCause()).isSameAs(cause);
        }

        @Test
        void nullMessageWithCauseFallsBackToNoMessage() {
            OreSiTechnicalException e = new OreSiTechnicalException(null, new RuntimeException());
            assertThat(e.getMessage()).isEqualTo(OreSiTechnicalException.NO_MESSAGE);
        }
    }

    // ------------------------------------------------------------------ //
    //  SiOreIllegalArgumentException                                      //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("SiOreIllegalArgumentException")
    class SiOreIllegalArgumentExceptionTest {

        @Test
        void noRightOnTable() {
            SiOreIllegalArgumentException e = SiOreIllegalArgumentException.noRightOnTable("myTable");
            assertThat(e.getMessage()).isEqualTo(SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE);
            assertThat(e.getParams()).containsEntry(SiOreIllegalArgumentException.TABLE, "myTable");
        }

        @Test
        void noRightOnTableForDeposit() {
            SiOreIllegalArgumentException e = SiOreIllegalArgumentException.noRightOnTableForDeposit("t");
            assertThat(e.getMessage()).isEqualTo(SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE_FOR_DEPOSIT);
        }

        @Test
        void noRightOnTableForPublish() {
            SiOreIllegalArgumentException e = SiOreIllegalArgumentException.noRightOnTableForPublishOrUnpublish("t");
            assertThat(e.getMessage()).isEqualTo(SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE_FOR_PUBLISH_OR_UNPUBLISH);
        }

        @Test
        void noRightOnTableForDelete() {
            SiOreIllegalArgumentException e = SiOreIllegalArgumentException.noRightOnTableForDelete("t");
            assertThat(e.getMessage()).isEqualTo(SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE_FOR_DELETE);
        }

        @Test
        void noRightOnTableForDeleteFromException() {
            SiOreIllegalArgumentException inner = SiOreIllegalArgumentException.noRightOnTable("inner");
            SiOreIllegalArgumentException e = SiOreIllegalArgumentException.noRightOnTableForDelete(inner);
            assertThat(e.getParams()).containsEntry(SiOreIllegalArgumentException.TABLE, "inner");
        }

        @Test
        void noRightOnTableForDeleteFromExceptionWithNullParams() {
            // exception sans params → fallback NOT_GIVEN
            SiOreIllegalArgumentException inner = new SiOreIllegalArgumentException("x", null);
            SiOreIllegalArgumentException e = SiOreIllegalArgumentException.noRightOnTableForDelete(inner);
            assertThat(e.getParams()).containsEntry(SiOreIllegalArgumentException.TABLE,
                    SiOreIllegalArgumentException.NOT_GIVEN);
        }

        @Test
        void noRightOnTableForPublishFromException() {
            SiOreIllegalArgumentException inner = SiOreIllegalArgumentException.noRightOnTable("t2");
            SiOreIllegalArgumentException e = SiOreIllegalArgumentException.noRightOnTableForPublishOrUnpublish(inner);
            assertThat(e.getParams()).containsEntry(SiOreIllegalArgumentException.TABLE, "t2");
        }

        @Test
        void noRightOnTableForDepositFromException() {
            SiOreIllegalArgumentException inner = SiOreIllegalArgumentException.noRightOnTable("t3");
            SiOreIllegalArgumentException e = SiOreIllegalArgumentException.noRightOnTableForDeposit(inner);
            assertThat(e.getParams()).containsEntry(SiOreIllegalArgumentException.TABLE, "t3");
        }
    }

    // ------------------------------------------------------------------ //
    //  BadDownloadDatasetQuery                                             //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("BadDownloadDatasetQuery")
    class BadDownloadDatasetQueryTest {

        @Test
        void simpleMessage() {
            BadDownloadDatasetQuery e = new BadDownloadDatasetQuery(BadDownloadDatasetQuery.MISSING_FILTER);
            assertThat(e.getMessage()).isEqualTo(BadDownloadDatasetQuery.MISSING_FILTER);
            assertThat(e.getParams()).isNull();
        }

        @Test
        void messageWithParams() {
            Map<String, Serializable> params = Map.of("from", "0", "to", "10");
            BadDownloadDatasetQuery e = new BadDownloadDatasetQuery(
                    BadDownloadDatasetQuery.FILTER_BAD_FORMAT_BAD_RANGE_FOR_NUMERICS, params);
            assertThat(e.getParams()).containsEntry("from", "0");
        }

        @Test
        void messageWithCause() {
            RuntimeException cause = new RuntimeException("root");
            BadDownloadDatasetQuery e = new BadDownloadDatasetQuery("msg", cause);
            assertThat(e.getCause()).isSameAs(cause);
        }

        @Test
        void allConstantsAreDefined() {
            // vérifie que les constantes ne sont pas null (détecte les regressions de refactoring)
            assertThat(BadDownloadDatasetQuery.MISSING_COMPONENT_KEY_FOR_SEARCH).isNotBlank();
            assertThat(BadDownloadDatasetQuery.FILTER_BAD_FORMAT_FOR_START_NUMERIC).isNotBlank();
            assertThat(BadDownloadDatasetQuery.FILTER_BAD_FORMAT_FOR_END_NUMERIC).isNotBlank();
            assertThat(BadDownloadDatasetQuery.FILTER_BAD_FORMAT_BAD_RANGE_FOR_NUMERICS).isNotBlank();
        }
    }

    // ------------------------------------------------------------------ //
    //  NoSuchApplicationException                                          //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NoSuchApplicationException")
    class NoSuchApplicationExceptionTest {

        @Test
        void messageContainsNameOrId() {
            NoSuchApplicationException e = new NoSuchApplicationException("myapp");
            assertThat(e.getNameOrId()).isEqualTo("myapp");
            assertThat(e.getMessage()).contains("myapp");
        }

        @Test
        void messageFollowsPattern() {
            NoSuchApplicationException e = new NoSuchApplicationException("xyz");
            assertThat(e.getMessage()).isEqualTo(
                    NoSuchApplicationException.APPLICATION_INCONNUE_PATTERN.formatted("xyz"));
        }
    }

    // ------------------------------------------------------------------ //
    //  BadLabelNameException                                               //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("BadLabelNameException")
    class BadLabelNameExceptionTest {

        @Test
        void applicationLabel() {
            BadLabelNameException e = new BadLabelNameException(
                    BadLabelNameException.LabelType.APPLICATION, "bad-name", "arg1");
            assertThat(e.getLabelType()).isEqualTo(BadLabelNameException.LabelType.APPLICATION);
            assertThat(e.getArgs()).containsExactly("arg1");
            assertThat(e.getMessage()).isEqualTo("bad-name");
        }

        @Test
        void allLabelTypes() {
            for (BadLabelNameException.LabelType t : BadLabelNameException.LabelType.values()) {
                BadLabelNameException e = new BadLabelNameException(t, "n");
                assertThat(e.getLabelType()).isEqualTo(t);
            }
        }

        @Test
        void multipleArgs() {
            BadLabelNameException e = new BadLabelNameException(
                    BadLabelNameException.LabelType.REFERENCE, "n", "a", "b", "c");
            assertThat(e.getArgs()).containsExactly("a", "b", "c");
        }
    }

    // ------------------------------------------------------------------ //
    //  AuthorizationRequestException (enum)                               //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("AuthorizationRequestException enum")
    class AuthorizationRequestExceptionEnumTest {

        @Test
        void allValuesHaveMessage() {
            for (AuthorizationRequestException val : AuthorizationRequestException.values()) {
                assertThat(val.getMessage()).isNotBlank()
                        .as("Enum %s doit avoir un message", val);
            }
        }

        @Test
        void messageStartsLowercase() {
            // la méthode toMessage() met la première lettre en minuscule
            for (AuthorizationRequestException val : AuthorizationRequestException.values()) {
                char first = val.getMessage().charAt(0);
                assertThat(Character.isLowerCase(first))
                        .as("Message de %s doit commencer par une minuscule", val)
                        .isTrue();
            }
        }

        @Test
        void noAuthorizationNameHasMessage() {
            assertThat(AuthorizationRequestException.NO_AUTHORIZATION_NAME.getMessage())
                    .isEqualTo("noAuthorizationName");
        }
    }

    // ------------------------------------------------------------------ //
    //  SiOreAuthorizationRequestException                                 //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("SiOreAuthorizationRequestException")
    class SiOreAuthorizationRequestExceptionTest {

        @Test
        void constructorSetsFields() {
            SiOreAuthorizationRequestException e = new SiOreAuthorizationRequestException(
                    AuthorizationRequestException.BAD_REFERENCES,
                    Map.of("key", "val"));
            assertThat(e.getException()).isEqualTo(AuthorizationRequestException.BAD_REFERENCES);
            assertThat(e.getParams()).containsEntry("key", "val");
        }

        @Test
        void nullParamsAllowed() {
            SiOreAuthorizationRequestException e = new SiOreAuthorizationRequestException(
                    AuthorizationRequestException.MISSING_REQUIRED_AUTHORIZATION, null);
            assertThat(e.getParams()).isNull();
        }
    }

    // ------------------------------------------------------------------ //
    //  BadRoleException                                                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("BadRoleException")
    class BadRoleExceptionTest {

        @Test
        void twoArgConstructor() {
            BadRoleException e = new BadRoleException("msg", "admin");
            assertThat(e.getMessage()).isEqualTo("msg");
            assertThat(e.getRole()).isEqualTo("admin");
        }

        @Test
        void threeArgConstructorWithCause() {
            RuntimeException cause = new RuntimeException("root");
            BadRoleException e = new BadRoleException("msg", "reader", cause);
            assertThat(e.getCause()).isSameAs(cause);
            assertThat(e.getRole()).isEqualTo("reader");
        }
    }

    // ------------------------------------------------------------------ //
    //  BadApplicationRoleException                                         //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("BadApplicationRoleException")
    class BadApplicationRoleExceptionTest {

        @Test
        void twoArgConstructor() {
            BadApplicationRoleException e = new BadApplicationRoleException("msg", "admin", null);
            assertThat(e.getMessage()).isEqualTo("msg");
        }

        @Test
        void threeArgConstructorWithCause() {
            RuntimeException cause = new RuntimeException("root");
            BadApplicationRoleException e = new BadApplicationRoleException("msg", "role", cause, null);
            assertThat(e.getCause()).isSameAs(cause);
        }
    }

    // ------------------------------------------------------------------ //
    //  BadBinaryFileDatasetQuery                                           //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("BadBinaryFileDatasetQuery")
    class BadBinaryFileDatasetQueryTest {

        @Test
        void message() {
            BadBinaryFileDatasetQuery e = new BadBinaryFileDatasetQuery("bad query");
            assertThat(e.getMessage()).isEqualTo("bad query");
            assertThat(e).isInstanceOf(OreSiTechnicalException.class);
        }
    }

    // ------------------------------------------------------------------ //
    //  BadFileOrUUIDQuery                                                  //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("BadFileOrUUIDQuery")
    class BadFileOrUUIDQueryTest {

        @Test
        void message() {
            BadFileOrUUIDQuery e = new BadFileOrUUIDQuery("bad uuid");
            assertThat(e.getMessage()).isEqualTo("bad uuid");
        }
    }

    // ------------------------------------------------------------------ //
    //  DeleteOnrepositoryApplicationNotAllowedException                   //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("DeleteOnrepositoryApplicationNotAllowedException")
    class DeleteNotAllowedTest {

        @Test
        void defaultMessage() {
            DeleteOnrepositoryApplicationNotAllowedException e =
                    new DeleteOnrepositoryApplicationNotAllowedException();
            assertThat(e.getMessage()).isEqualTo("DELETE_ON_REPOSITORY_APPLICATION_NOT_ALLOWED_EXCEPTION");
        }
    }

    // ------------------------------------------------------------------ //
    //  UnloadDataCsvFileException                                          //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("UnloadDataCsvFileException")
    class UnloadDataCsvFileExceptionTest {

        @Test
        void wrapsInnerException() {
            RuntimeException inner = new RuntimeException("csv error");
            UnloadDataCsvFileException e = new UnloadDataCsvFileException(inner);
            assertThat(e.getMessage()).isEqualTo("unloadDataCsvFileException");
        }
    }

    // ------------------------------------------------------------------ //
    //  BadApplicationConfigurationException                               //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("BadApplicationConfigurationException")
    class BadApplicationConfigurationExceptionTest {

        @Test
        void storesConfigurationException() {
            ConfigurationException ce = ConfigurationException.characterNotAcceptInName;
            BadApplicationConfigurationException e =
                    new BadApplicationConfigurationException("wrapping", ce);
            assertThat(e.getMessage()).isEqualTo("wrapping");
            assertThat(e.getConfigurationException()).isSameAs(ce);
        }
    }
}
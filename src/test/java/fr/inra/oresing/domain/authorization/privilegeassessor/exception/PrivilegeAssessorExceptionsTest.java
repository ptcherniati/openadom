package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.OreSiUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs des exceptions du package privilegeassessor.exception.
 * Objectif : couvrir les constructeurs et les constantes de message.
 */
@Tag("core.auth")
@DisplayName("PrivilegeAssessor exceptions — constructeurs et constantes")
class PrivilegeAssessorExceptionsTest {

    // ------------------------------------------------------------------ //
    //  NotApplicationCanDeleteRightsException                            //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationCanDeleteRightsException")
    class NotApplicationCanDeleteRightsExceptionTest {

        @Test
        @DisplayName("constructeur(app, dataType) stocke les champs")
        void constructorStoresFields() {
            NotApplicationCanDeleteRightsException ex =
                    new NotApplicationCanDeleteRightsException("myApp", "myData");
            assertThat(ex.applicationName).isEqualTo("myApp");
            assertThat(ex.dataType).isEqualTo("myData");
        }

        @Test
        @DisplayName("constante NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION non null")
        void constantNotNull() {
            assertThat(NotApplicationCanDeleteRightsException.NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION)
                    .isNotBlank();
        }

        @Test
        @DisplayName("getMessage() retourne la constante")
        void messageIsConstant() {
            NotApplicationCanDeleteRightsException ex =
                    new NotApplicationCanDeleteRightsException("a", "b");
            assertThat(ex.getMessage())
                    .isEqualTo(NotApplicationCanDeleteRightsException.NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationCanManageReferenceRightsException                   //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationCanManageReferenceRightsException")
    class NotApplicationCanManageReferenceRightsExceptionTest {

        @Test
        @DisplayName("constructeur(app) stocke applicationName")
        void constructorStoresApp() {
            NotApplicationCanManageReferenceRightsException ex =
                    new NotApplicationCanManageReferenceRightsException("refApp");
            assertThat(ex.applicationName).isEqualTo("refApp");
        }

        @Test
        @DisplayName("constante non null")
        void constantNotNull() {
            assertThat(NotApplicationCanManageReferenceRightsException.NO_RIGHT_FOR_MANAGE_REFERENCES_RIGHTS_APPLICATION)
                    .isNotBlank();
        }

        @Test
        @DisplayName("getMessage() retourne la constante")
        void message() {
            NotApplicationCanManageReferenceRightsException ex =
                    new NotApplicationCanManageReferenceRightsException("app");
            assertThat(ex.getMessage())
                    .isEqualTo(NotApplicationCanManageReferenceRightsException.NO_RIGHT_FOR_MANAGE_REFERENCES_RIGHTS_APPLICATION);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationCanSetRightsException                               //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationCanSetRightsException")
    class NotApplicationCanSetRightsExceptionTest {

        @Test
        @DisplayName("constructeur(app) stocke applicationName")
        void constructorStoresApp() {
            NotApplicationCanSetRightsException ex =
                    new NotApplicationCanSetRightsException("setApp");
            assertThat(ex.applicationName).isEqualTo("setApp");
        }

        @Test
        @DisplayName("authorizationsRestrictions initialisée vide")
        void authorizationsRestrictionsEmpty() {
            NotApplicationCanSetRightsException ex =
                    new NotApplicationCanSetRightsException("app");
            assertThat(ex.authorizationsRestrictions).isEmpty();
        }

        @Test
        @DisplayName("getMessage() retourne la constante")
        void message() {
            NotApplicationCanSetRightsException ex =
                    new NotApplicationCanSetRightsException("app");
            assertThat(ex.getMessage())
                    .isEqualTo(NotApplicationCanSetRightsException.NO_RIGHT_FOR_SET_RIGHTS_APPLICATION);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationUserReaderRightsException                           //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationUserReaderRightsException")
    class NotApplicationUserReaderRightsExceptionTest {

        @Test
        @DisplayName("constructeur(app) stocke applicationName")
        void constructorWithApp() {
            NotApplicationUserReaderRightsException ex =
                    new NotApplicationUserReaderRightsException("readerApp");
            assertThat(ex.applicationName).isEqualTo("readerApp");
        }

        @Test
        @DisplayName("constructeur sans arg ne lève pas d'exception")
        void constructorNoArgs() {
            NotApplicationUserReaderRightsException ex =
                    new NotApplicationUserReaderRightsException();
            assertThat(ex.getMessage())
                    .isEqualTo(NotApplicationUserReaderRightsException.NO_RIGHT_FOR_APPLICATION_USER_READER_RIGHT_EXCEPTION);
        }

        @Test
        @DisplayName("constructeur(Throwable) encapsule la cause")
        void constructorWithCause() {
            RuntimeException cause = new RuntimeException("root");
            NotApplicationUserReaderRightsException ex =
                    new NotApplicationUserReaderRightsException(cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationDataWriterForPublishException                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationDataWriterForPublishException")
    class NotApplicationDataWriterForPublishExceptionTest {

        @Test
        @DisplayName("constructeur(app, data) stocke les champs")
        void constructorWithFields() {
            NotApplicationDataWriterForPublishException ex =
                    new NotApplicationDataWriterForPublishException("pubApp", "pubData");
            assertThat(ex.applicationName).isEqualTo("pubApp");
            assertThat(ex.dataName).isEqualTo("pubData");
        }

        @Test
        @DisplayName("constructeur sans arg ne lève pas d'exception")
        void constructorNoArgs() {
            NotApplicationDataWriterForPublishException ex =
                    new NotApplicationDataWriterForPublishException();
            assertThat(ex.getMessage())
                    .isEqualTo(NotApplicationDataWriterForPublishException.NO_RIGHT_FOR_USER_DATA_WRITER_FOR_PUBLISH);
        }

        @Test
        @DisplayName("constructeur(Throwable) encapsule la cause")
        void constructorWithCause() {
            RuntimeException cause = new RuntimeException("cause");
            NotApplicationDataWriterForPublishException ex =
                    new NotApplicationDataWriterForPublishException(cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    // ------------------------------------------------------------------ //
    //  IllegalUserToBeGranted                                            //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("IllegalUserToBeGranted")
    class IllegalUserToBeGrantedTest {

        @Test
        @DisplayName("constructeur(user, app) stocke login et applicationName")
        void constructorStoresLoginAndApp() {
            OreSiUser user = new OreSiUser();
            user.setLogin("john");
            IllegalUserToBeGranted ex = new IllegalUserToBeGranted(user, "grantApp");
            assertThat(ex.login).isEqualTo("john");
            assertThat(ex.applicationName).isEqualTo("grantApp");
        }

        @Test
        @DisplayName("getMessage() retourne ILLEGAL_ROLE_TO_BE_GRANTED")
        void message() {
            OreSiUser user = new OreSiUser();
            user.setLogin("user1");
            IllegalUserToBeGranted ex = new IllegalUserToBeGranted(user, "app");
            assertThat(ex.getMessage())
                    .isEqualTo(IllegalUserToBeGranted.ILLEGAL_ROLE_TO_BE_GRANTED);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationManagerRightsException                              //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationManagerRightsException")
    class NotApplicationManagerRightsExceptionTest {

        @Test
        @DisplayName("constante NO_RIGHT_FOR_APPLICATION_MANAGEMENT non null")
        void constantNotNull() {
            assertThat(NotApplicationManagerRightsException.NO_RIGHT_FOR_APPLICATION_MANAGEMENT)
                    .isNotBlank();
        }

        @Test
        @DisplayName("getMessage() retourne la constante")
        void message() {
            NotApplicationManagerRightsException ex =
                    new NotApplicationManagerRightsException("mgrApp");
            assertThat(ex.getMessage())
                    .isEqualTo(NotApplicationManagerRightsException.NO_RIGHT_FOR_APPLICATION_MANAGEMENT);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationUserManagerRightsException                          //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationUserManagerRightsException")
    class NotApplicationUserManagerRightsExceptionTest {

        @Test
        @DisplayName("getMessage() retourne la constante")
        void message() {
            NotApplicationUserManagerRightsException ex =
                    new NotApplicationUserManagerRightsException("umgrApp");
            assertThat(ex.getMessage())
                    .isEqualTo(NotApplicationUserManagerRightsException.NO_RIGHT_FOR_APPLICATION_USER_MANAGEMENT);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationDataReaderException                                 //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationDataReaderException")
    class NotApplicationDataReaderExceptionTest {

        @Test
        @DisplayName("constructeur(app, data) stocke les champs")
        void constructorStoresFields() {
            NotApplicationDataReaderException ex =
                    new NotApplicationDataReaderException("readApp", "readData");
            assertThat(ex.applicationName).isEqualTo("readApp");
            assertThat(ex.dataName).isEqualTo("readData");
        }

        @Test
        @DisplayName("getMessage() retourne NO_RIGHT_FOR_USER_DATA_READER")
        void message() {
            NotApplicationDataReaderException ex =
                    new NotApplicationDataReaderException("a", "b");
            assertThat(ex.getMessage())
                    .isEqualTo(NotApplicationDataReaderException.NO_RIGHT_FOR_USER_DATA_READER);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationDataWriterException                                 //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationDataWriterException")
    class NotApplicationDataWriterExceptionTest {

        @Test
        @DisplayName("constructeur(app, data) stocke les champs")
        void constructorStoresFields() {
            NotApplicationDataWriterException ex =
                    new NotApplicationDataWriterException("writerApp", "writerData");
            assertThat(ex.applicationName).isEqualTo("writerApp");
            assertThat(ex.dataName).isEqualTo("writerData");
        }

        @Test
        @DisplayName("getMessage() retourne NO_RIGHT_FOR_USER_DATA_WRITER")
        void message() {
            NotApplicationDataWriterException ex =
                    new NotApplicationDataWriterException("a", "b");
            assertThat(ex.getMessage())
                    .isEqualTo(NotApplicationDataWriterException.NO_RIGHT_FOR_USER_DATA_WRITER);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationCreatorRightsException                              //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationCreatorRightsException")
    class NotApplicationCreatorRightsExceptionTest {

        @Test
        @DisplayName("constructeur(app) stocke applicationName")
        void constructorStoresApp() {
            NotApplicationCreatorRightsException ex =
                    new NotApplicationCreatorRightsException("creatorApp");
            assertThat(ex.applicationName).isEqualTo("creatorApp");
            assertThat(ex.applicationRestrictions).isEmpty();
        }

        @Test
        @DisplayName("constructeur sans arg")
        void constructorNoArgs() {
            NotApplicationCreatorRightsException ex = new NotApplicationCreatorRightsException();
            assertThat(ex.getMessage()).isEqualTo(NotApplicationCreatorRightsException.NO_RIGHT_FOR_APPLICATION_CREATION);
            assertThat(ex.applicationRestrictions).isEmpty();
        }

        @Test
        @DisplayName("constructeur(app, Set) stocke les restrictions")
        void constructorWithRestrictions() {
            java.util.Set<String> restrictions = java.util.Set.of("r1", "r2");
            NotApplicationCreatorRightsException ex =
                    new NotApplicationCreatorRightsException("appX", restrictions);
            assertThat(ex.applicationName).isEqualTo("appX");
            assertThat(ex.applicationRestrictions).containsExactlyInAnyOrder("r1", "r2");
        }

        @Test
        @DisplayName("getMessage() retourne NO_RIGHT_FOR_APPLICATION_CREATION")
        void message() {
            NotApplicationCreatorRightsException ex =
                    new NotApplicationCreatorRightsException("app");
            assertThat(ex.getMessage())
                    .isEqualTo(NotApplicationCreatorRightsException.NO_RIGHT_FOR_APPLICATION_CREATION);
        }
    }

    // ------------------------------------------------------------------ //
    //  BadLoginForAction                                                  //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("BadLoginForAction")
    class BadLoginForActionTest {

        @Test
        @DisplayName("constructeur stocke login")
        void constructorStoresLogin() {
            BadLoginForAction ex = new BadLoginForAction("badLogin");
            assertThat(ex.getLogin()).isEqualTo("badLogin");
        }

        @Test
        @DisplayName("constante BAD_LOGIN_FOR_ACTION non null")
        void constantNotNull() {
            assertThat(BadLoginForAction.BAD_LOGIN_FOR_ACTION).isNotBlank();
        }

        @Test
        @DisplayName("getMessage() retourne la constante")
        void messageIsConstant() {
            BadLoginForAction ex = new BadLoginForAction("user1");
            assertThat(ex.getMessage()).isEqualTo(BadLoginForAction.BAD_LOGIN_FOR_ACTION);
        }
    }

    // ------------------------------------------------------------------ //
    //  DisconnectedException                                              //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("DisconnectedException")
    class DisconnectedExceptionTest {

        @Test
        @DisplayName("constructeur(message) stocke le message")
        void constructorWithMessage() {
            DisconnectedException ex = new DisconnectedException("disconnected");
            assertThat(ex.getMessage()).isEqualTo("disconnected");
        }
    }

    // ------------------------------------------------------------------ //
    //  IllegalRoleToBeGranted                                             //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("IllegalRoleToBeGranted")
    class IllegalRoleToBeGrantedTest {

        @Test
        @DisplayName("constructeur stocke role")
        void constructorStoresRole() {
            IllegalRoleToBeGranted ex = new IllegalRoleToBeGranted("badRole");
            assertThat(ex.getRole()).isEqualTo("badRole");
        }

        @Test
        @DisplayName("constante ILLEGAL_ROLE_TO_BE_GRANTED non null")
        void constantNotNull() {
            assertThat(IllegalRoleToBeGranted.ILLEGAL_ROLE_TO_BE_GRANTED).isNotBlank();
        }

        @Test
        @DisplayName("getMessage() retourne la constante")
        void messageIsConstant() {
            IllegalRoleToBeGranted ex = new IllegalRoleToBeGranted("role");
            assertThat(ex.getMessage()).isEqualTo(IllegalRoleToBeGranted.ILLEGAL_ROLE_TO_BE_GRANTED);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationCanDeleteRightsException — 2e constructeur           //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationCanDeleteRightsException — constructeurs supplémentaires")
    class NotApplicationCanDeleteRightsExceptionAdditionalTest {

        @Test
        @DisplayName("constructeur(app, dataType, list) stocke les restrictions")
        void constructorWithList() {
            fr.inra.oresing.domain.Authorization auth = new fr.inra.oresing.domain.Authorization(
                    java.util.Map.of(), fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange.always());
            NotApplicationCanDeleteRightsException ex =
                    new NotApplicationCanDeleteRightsException("app", "data", java.util.List.of(auth));
            assertThat(ex.getAuthorizationsRestrictions()).hasSize(1);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationCanManageReferenceRightsException — 2e constructeur  //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationCanManageReferenceRightsException — constructeurs supplémentaires")
    class NotApplicationCanManageReferenceRightsExceptionAdditionalTest {

        @Test
        @DisplayName("constructeur(app, list) stocke les restrictions")
        void constructorWithList() {
            NotApplicationCanManageReferenceRightsException ex =
                    new NotApplicationCanManageReferenceRightsException("app", java.util.List.of("ref1", "ref2"));
            assertThat(ex.getAuthorizationsRestrictions()).containsExactlyInAnyOrder("ref1", "ref2");
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationCanSetRightsException — 2e constructeur              //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationCanSetRightsException — constructeurs supplémentaires")
    class NotApplicationCanSetRightsExceptionAdditionalTest {

        @Test
        @DisplayName("constructeur(app, list) stocke les restrictions")
        void constructorWithList() {
            fr.inra.oresing.domain.Authorization auth = new fr.inra.oresing.domain.Authorization(
                    java.util.Map.of(), fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange.always());
            NotApplicationCanSetRightsException ex =
                    new NotApplicationCanSetRightsException("app", java.util.List.of(auth));
            assertThat(ex.getAuthorizationsRestrictions()).hasSize(1);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationCanSetRightsReferencesException                      //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationCanSetRightsReferencesException")
    class NotApplicationCanSetRightsReferencesExceptionTest {

        @Test
        @DisplayName("constructeur stocke applicationName")
        void constructorStoresApp() {
            NotApplicationCanSetRightsReferencesException ex =
                    new NotApplicationCanSetRightsReferencesException("refApp");
            assertThat(ex.getApplicationName()).isEqualTo("refApp");
        }

        @Test
        @DisplayName("constante non null")
        void constantNotNull() {
            assertThat(NotApplicationCanSetRightsReferencesException.NO_RIGHT_FOR_SET_RIGHTS_REFERENCES_APPLICATION)
                    .isNotBlank();
        }

        @Test
        @DisplayName("getMessage() retourne la constante")
        void message() {
            NotApplicationCanSetRightsReferencesException ex =
                    new NotApplicationCanSetRightsReferencesException("app");
            assertThat(ex.getMessage())
                    .isEqualTo(NotApplicationCanSetRightsReferencesException.NO_RIGHT_FOR_SET_RIGHTS_REFERENCES_APPLICATION);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationDataReaderException — constructeurs supplémentaires  //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationDataReaderException — constructeurs supplémentaires")
    class NotApplicationDataReaderExceptionAdditionalTest {

        @Test
        @DisplayName("constructeur(app) stocke applicationName sans dataName")
        void constructorWithAppOnly() {
            NotApplicationDataReaderException ex = new NotApplicationDataReaderException("appOnly");
            assertThat(ex.applicationName).isEqualTo("appOnly");
        }

        @Test
        @DisplayName("constructeur sans arg")
        void constructorNoArgs() {
            NotApplicationDataReaderException ex = new NotApplicationDataReaderException();
            assertThat(ex.getMessage()).isEqualTo(NotApplicationDataReaderException.NO_RIGHT_FOR_USER_DATA_READER);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationDataWriterException — constructeurs supplémentaires  //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationDataWriterException — constructeurs supplémentaires")
    class NotApplicationDataWriterExceptionAdditionalTest {

        @Test
        @DisplayName("constructeur sans arg")
        void constructorNoArgs() {
            NotApplicationDataWriterException ex = new NotApplicationDataWriterException();
            assertThat(ex.getMessage()).isEqualTo(NotApplicationDataWriterException.NO_RIGHT_FOR_USER_DATA_WRITER);
        }

        @Test
        @DisplayName("constructeur(Throwable) encapsule la cause")
        void constructorWithCause() {
            RuntimeException cause = new RuntimeException("root");
            NotApplicationDataWriterException ex = new NotApplicationDataWriterException(cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationDataWriterForDepositException                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationDataWriterForDepositException")
    class NotApplicationDataWriterForDepositExceptionTest {

        @Test
        @DisplayName("constructeur(app, data) stocke les champs")
        void constructorStoresFields() {
            NotApplicationDataWriterForDepositException ex =
                    new NotApplicationDataWriterForDepositException("depApp", "depData");
            assertThat(ex.applicationName).isEqualTo("depApp");
            assertThat(ex.dataName).isEqualTo("depData");
        }

        @Test
        @DisplayName("constante NO_RIGHT_FOR_USER_DATA_WRITER_FOR_DEPOSIT non null")
        void constantNotNull() {
            assertThat(NotApplicationDataWriterForDepositException.NO_RIGHT_FOR_USER_DATA_WRITER_FOR_DEPOSIT)
                    .isNotBlank();
        }

        @Test
        @DisplayName("getMessage() retourne la constante")
        void message() {
            NotApplicationDataWriterForDepositException ex =
                    new NotApplicationDataWriterForDepositException("a", "b");
            assertThat(ex.getMessage())
                    .isEqualTo(NotApplicationDataWriterForDepositException.NO_RIGHT_FOR_USER_DATA_WRITER_FOR_DEPOSIT);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationManagerRightsException — constructeurs supplémentaires//
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationManagerRightsException — constructeurs supplémentaires")
    class NotApplicationManagerRightsExceptionAdditionalTest {

        @Test
        @DisplayName("constructeur sans arg")
        void constructorNoArgs() {
            NotApplicationManagerRightsException ex = new NotApplicationManagerRightsException();
            assertThat(ex.getMessage()).isEqualTo(NotApplicationManagerRightsException.NO_RIGHT_FOR_APPLICATION_MANAGEMENT);
        }

        @Test
        @DisplayName("constructeur(Throwable) encapsule la cause")
        void constructorWithCause() {
            RuntimeException cause = new RuntimeException("root");
            NotApplicationManagerRightsException ex = new NotApplicationManagerRightsException(cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotApplicationUserManagerRightsException — constructeurs suppl.    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotApplicationUserManagerRightsException — constructeurs supplémentaires")
    class NotApplicationUserManagerRightsExceptionAdditionalTest {

        @Test
        @DisplayName("constructeur sans arg")
        void constructorNoArgs() {
            NotApplicationUserManagerRightsException ex = new NotApplicationUserManagerRightsException();
            assertThat(ex.getMessage()).isEqualTo(NotApplicationUserManagerRightsException.NO_RIGHT_FOR_APPLICATION_USER_MANAGEMENT);
        }

        @Test
        @DisplayName("constructeur(Throwable) encapsule la cause")
        void constructorWithCause() {
            RuntimeException cause = new RuntimeException("root");
            NotApplicationUserManagerRightsException ex = new NotApplicationUserManagerRightsException(cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }
    }

    // ------------------------------------------------------------------ //
    //  NotOpenAdomAdminException                                          //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotOpenAdomAdminException")
    class NotOpenAdomAdminExceptionTest {

        @Test
        @DisplayName("constructeur sans arg")
        void constructorNoArgs() {
            NotOpenAdomAdminException ex = new NotOpenAdomAdminException();
            assertThat(ex.getMessage()).isEqualTo(NotOpenAdomAdminException.OPEN_ADOM_ADMIN_REQUIRED_FOR_OPERATION);
        }

        @Test
        @DisplayName("constructeur(Throwable) encapsule la cause")
        void constructorWithCause() {
            RuntimeException cause = new RuntimeException("root");
            NotOpenAdomAdminException ex = new NotOpenAdomAdminException(cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("constante OPEN_ADOM_ADMIN_REQUIRED_FOR_OPERATION non null")
        void constantNotNull() {
            assertThat(NotOpenAdomAdminException.OPEN_ADOM_ADMIN_REQUIRED_FOR_OPERATION).isNotBlank();
        }
    }

    // ------------------------------------------------------------------ //
    //  NotOpenAdomAdministratorForSystemException                         //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("NotOpenAdomAdministratorForSystemException")
    class NotOpenAdomAdministratorForSystemExceptionTest {

        @Test
        @DisplayName("constructeur sans arg")
        void constructorNoArgs() {
            NotOpenAdomAdministratorForSystemException ex = new NotOpenAdomAdministratorForSystemException();
            assertThat(ex.getMessage()).isEqualTo(NotOpenAdomAdministratorForSystemException.NOT_OPEN_ADOM_ADMINISTRATOR_FOR_SYSTEM);
        }

        @Test
        @DisplayName("constructeur(Throwable) encapsule la cause")
        void constructorWithCause() {
            RuntimeException cause = new RuntimeException("root");
            NotOpenAdomAdministratorForSystemException ex = new NotOpenAdomAdministratorForSystemException(cause);
            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("constante NOT_OPEN_ADOM_ADMINISTRATOR_FOR_SYSTEM non null")
        void constantNotNull() {
            assertThat(NotOpenAdomAdministratorForSystemException.NOT_OPEN_ADOM_ADMINISTRATOR_FOR_SYSTEM).isNotBlank();
        }
    }
}
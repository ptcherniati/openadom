package fr.inra.oresing.persistence.index;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Authorization;
import fr.inra.oresing.domain.application.configuration.AuthorizationScopeComponentData;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.authorization.request.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorizationIndexTest {

    private AuthorizationIndex authorizationIndex;
    private Application application;

    @BeforeEach
    void setUp() {
        application = Mockito.mock(Application.class);
        Mockito.when(application.getName()).thenReturn("monsore");
        Mockito.when(application.getConfiguration().dataDescription()).thenReturn(
                Map.of("pem", mockStandardDataDescription("pem"))
        );
        authorizationIndex = new AuthorizationIndex(application);
    }

    @Test
    @Disabled
    void createIndexForPem() {
        String createIndexSql = authorizationIndex.createIndex("pem");
        assertEquals(
                """
                        CREATE INDEX IF NOT EXISTS authorization_pem_index
                            ON monsore.referencevalue USING gin
                            (
                                referencetype,
                                refvalues,
                                (("authorization").requiredauthorizations.projet),
                                (("authorization").requiredauthorizations.sites),
                                (("authorization").timescope)
                            )
                            WHERE referencetype = 'pem';""",
                createIndexSql);
    }

    @Test
    @Disabled
    void testCreateIndexes() {
        String createIndexesSql = authorizationIndex.createIndexes();
        assertEquals(
                """
                        DO $$
                        DECLARE
                            idx record;
                        BEGIN
                            FOR idx IN (SELECT indexname FROM pg_indexes WHERE schemaname = 'monsore' AND indexname LIKE 'authorization_%_index')
                            LOOP
                                EXECUTE 'DROP INDEX IF EXISTS ' || quote_ident(idx.indexname);
                            END LOOP;
                        END $$;
                        
                        CREATE INDEX IF NOT EXISTS authorization_pem_index
                            ON monsore.referencevalue USING gin
                            (
                                referencetype,
                                refvalues,
                                (("authorization").requiredauthorizations.projet),
                                (("authorization").requiredauthorizations.sites),
                                (("authorization").timescope)
                            )
                            WHERE referencetype = 'pem';
                        """,
                createIndexesSql);
    }

    @Test
    @Disabled
    void testSqlFilterForAuthorization() {
        LocalDateTimeRange timescope = LocalDateTimeRange.forDay(LocalDate.of(1984, 1, 2));
        Map<String, List<Ltree>> authorizationScope = Map.of(
                "projet", List.of(Ltree.fromSql("projetKprojet_atlantique")),
                "sites", List.of(Ltree.fromSql("type_de_sitesKplateforme"))
        );
        AuthorizationForScope authorization = new AuthorizationForReferenceScopeAndTimeScope(Set.of(), authorizationScope, timescope);
        String sqlFilter = authorizationIndex.sqlFilterForAuthorization("pem", authorization, false);
        assertEquals("""
                        referencetype = 'pem'
                        AND ("authorization").requiredauthorizations.projet @> ARRAY['projetKprojet_atlantique']::ltree[]
                        AND ("authorization").requiredauthorizations.sites @> ARRAY['type_de_sitesKplateforme']::ltree[]
                        AND ("authorization").timescope && '[1984-01-02T00:00, 1984-01-03T00:00)'::tsrange""",
                sqlFilter);

        authorization = new AuthorizationNoRestriction(Set.of());
        sqlFilter = authorizationIndex.sqlFilterForAuthorization("pem", authorization, false);
        assertEquals("referencetype = 'pem'", sqlFilter);
    }

    private StandardDataDescription mockStandardDataDescription(String dataName) {
        StandardDataDescription description = Mockito.mock(StandardDataDescription.class);
        Mockito.when(description.authorization()).thenReturn(
                new Authorization(
                        List.of(
                                new AuthorizationScopeComponentData("projet", ""),
                                new AuthorizationScopeComponentData("sites", "")
                        ),
                        "timescope"
                )
        );
        return description;
    }

    @Test
    @Disabled
    public void testSqlFilterForAuthorizationWithMultipleFields() {
        LocalDateTimeRange timescope = LocalDateTimeRange.forDay(LocalDate.of(2023, 5, 15));
        Map<String, List<Ltree>> authorizationScope = Map.of(
                "projet", List.of(Ltree.fromSql("projetKprojet_atlantique"), Ltree.fromSql("projetKprojet_mediterranee")),
                "sites", List.of(Ltree.fromSql("type_de_sitesKplateforme"), Ltree.fromSql("type_de_sitesKlaboratoire")),
                "equipe", List.of(Ltree.fromSql("equipeKequipe_A"))
        );
        AuthorizationForScope authorization = new AuthorizationForReferenceScopeAndTimeScope(Set.of(), authorizationScope, timescope);
        String sqlFilter = authorizationIndex.sqlFilterForAuthorization("pem", authorization, false);
        Assertions.assertEquals("""
                        referencetype = 'pem'
                        AND ("authorization").requiredauthorizations.projet @> ARRAY['projetKprojet_atlantique', 'projetKprojet_mediterranee']::ltree[]
                        AND ("authorization").requiredauthorizations.sites @> ARRAY['type_de_sitesKplateforme', 'type_de_sitesKlaboratoire']::ltree[]
                        AND ("authorization").requiredauthorizations.equipe @> ARRAY['equipeKequipe_A']::ltree[]
                        AND ("authorization").timescope && '[2023-05-15T00:00, 2023-05-16T00:00)'::tsrange""",
                sqlFilter);
    }

    @Test
    @Disabled
    public void testSqlFilterForAuthorizationWithEmptyFields() {
        Map<String, List<Ltree>> authorizationScope = Map.of(
                "projet", List.of(),
                "sites", List.of(Ltree.fromSql("type_de_sitesKplateforme"))
        );
        AuthorizationForScope authorization = new AuthorizationForReferenceScope(Set.of(), authorizationScope);
        String sqlFilter = authorizationIndex.sqlFilterForAuthorization("pem", authorization, false);
        Assertions.assertEquals("""
                        referencetype = 'pem'
                        AND ("authorization").requiredauthorizations.projet IS NULL
                        AND ("authorization").requiredauthorizations.sites @> ARRAY['type_de_sitesKplateforme']::ltree[]""",
                sqlFilter);
    }
}

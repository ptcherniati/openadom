package fr.inra.oresing.persistence.index;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.authorization.request.AuthorizationForReferenceScope;
import fr.inra.oresing.domain.authorization.request.AuthorizationForReferenceScopeAndTimeScope;
import fr.inra.oresing.domain.authorization.request.AuthorizationForScope;
import fr.inra.oresing.domain.authorization.request.AuthorizationNoRestriction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;

@org.junit.jupiter.api.Tag("core.auth")
class AuthorizationIndexTest {

    public static final String PEM = "pem";
    public static final String MONSORE = "monsore";
    private AuthorizationIndex authorizationIndex;

    @BeforeEach
    void setUp() {
        Application application = Mockito.mock(Application.class, "mockedApplication");
        Configuration configuration = Mockito.mock(Configuration.class, "mockedConfiguration");
        Mockito.when(application.getName()).thenReturn(MONSORE);
        Mockito.when(application.getConfiguration()).thenReturn(configuration);
        final StandardDataDescription pem = mockStandardDataDescription();
        Mockito.when(configuration.dataDescription()).thenReturn(
                Map.of(PEM, pem)
        );
        Mockito.when(application.findData(PEM)).thenReturn(Optional.of(pem));
        authorizationIndex = new AuthorizationIndex(application);
    }

    @Test
    void createIndexForPem() {
        String createIndexSql = authorizationIndex.createIndex(PEM);
        assertEquals(
                """
                                                        CREATE INDEX IF NOT EXISTS authorization_pem_index_refvalues_index
                                                        ON monsore.referencevalue USING gin
                                                        (
                                                            refvalues jsonb_path_ops
                                                        )
                                                        WHERE referencetype = 'pem';
                        
                                                        CREATE INDEX IF NOT EXISTS authorization_pem_index_auth_index
                                                        ON monsore.referencevalue USING gin
                                                        (
                                                            (("authorization").requiredauthorizations.dataProjet),
                                                            (("authorization").requiredauthorizations.dataSites)
                                                        )
                                                        WHERE referencetype = 'pem';
                        
                                                        CREATE INDEX IF NOT EXISTS authorization_pem_index_timescope_index
                                                        ON monsore.referencevalue USING gist
                                                        ((("authorization").timescope))
                                                        WHERE referencetype = 'pem';
                                                        
                                                        """,
                createIndexSql);
    }

    @Test
    void testCreateIndexes() {
        String createIndexesSql = authorizationIndex.createIndexes();
        assertEquals(
                """
                        DO $$
                        DECLARE
                            idx record;
                        BEGIN
                            FOR idx IN (SELECT indexname FROM pg_indexes WHERE schemaname = 'monsore' 
                            AND indexname LIKE 'authorization_%_index')
                            LOOP
                                EXECUTE 'DROP INDEX IF EXISTS ' || quote_ident(idx.indexname);
                            END LOOP;
                        END $$;
                        
                        CREATE INDEX IF NOT EXISTS authorization_pem_index_refvalues_index
                        ON monsore.referencevalue USING gin
                        (
                            refvalues jsonb_path_ops
                        )
                        WHERE referencetype = 'pem';
                        
                        CREATE INDEX IF NOT EXISTS authorization_pem_index_auth_index
                        ON monsore.referencevalue USING gin
                        (
                            (("authorization").requiredauthorizations.dataProjet),
                            (("authorization").requiredauthorizations.dataSites)
                        )
                        WHERE referencetype = 'pem';
                        
                        CREATE INDEX IF NOT EXISTS authorization_pem_index_timescope_index
                        ON monsore.referencevalue USING gist
                        ((("authorization").timescope))
                        WHERE referencetype = 'pem';
                        
                        
                        """,
                createIndexesSql);
    }

    @Test
    void testSqlFilterForAuthorization() {
        LocalDateTimeRange timescope = LocalDateTimeRange.forDay(LocalDate.of(1984, 1, 2));
        Map<String, List<Ltree>> authorizationScope = Map.of(
                "projet", List.of(Ltree.fromSql("projetKprojet_atlantique")),
                "sites", List.of(Ltree.fromSql("type_de_sitesKplateforme"))
        );
        AuthorizationForScope authorization = new AuthorizationForReferenceScopeAndTimeScope(Set.of(), authorizationScope, timescope);
        String sqlFilter = authorizationIndex.sqlFilterForAuthorization(PEM, authorization, true);
        assertEquals("""
                        referencetype ='pem'
                         AND ("authorization").requiredauthorizations.projet @> ARRAY['projetKprojet_atlantique']::ltree[]
                         AND ("authorization").requiredauthorizations.sites @> ARRAY['type_de_sitesKplateforme']::ltree[]
                         AND ("authorization").timescope && '["1984-01-02 00:00:00","1984-01-03 00:00:00")'::tsrange""",
                sqlFilter);

        authorization = new AuthorizationNoRestriction(Set.of());
        sqlFilter = authorizationIndex.sqlFilterForAuthorization(PEM, authorization, false);
        assertEquals("referencetype ='pem'", sqlFilter);
    }

    private StandardDataDescription mockStandardDataDescription() {
        return new StandardDataDescription(
                ';',
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new Authorization(
                        List.of(
                                new AuthorizationScopeComponentData("projet", "dataProjet"),
                                new AuthorizationScopeComponentData("sites", "dataSites")
                        ),
                        "timeScope"
                ),
                null,
                null
        );
    }


    @Test
    void testSqlFilterForAuthorizationWithMultipleFields() {
        LocalDateTimeRange timescope = LocalDateTimeRange.forDay(LocalDate.of(2023, 5, 15));
        Map<String, List<Ltree>> authorizationScope = Map.of(
                "projet", List.of(Ltree.fromSql("projetKprojet_atlantique"), Ltree.fromSql("projetKprojet_mediterranee")),
                "sites", List.of(Ltree.fromSql("type_de_sitesKplateforme"), Ltree.fromSql("type_de_sitesKlaboratoire")),
                "equipe", List.of(Ltree.fromSql("equipeKequipe_A"))
        );
        AuthorizationForScope authorization = new AuthorizationForReferenceScopeAndTimeScope(Set.of(), authorizationScope, timescope);
        String sqlFilter = authorizationIndex.sqlFilterForAuthorization(PEM, authorization, true);
        assertEquals("""
                        referencetype ='pem'
                         AND ("authorization").requiredauthorizations.equipe @> ARRAY['equipeKequipe_A']::ltree[]
                         AND ("authorization").requiredauthorizations.projet @> ARRAY['projetKprojet_atlantique', 'projetKprojet_mediterranee']::ltree[]
                         AND ("authorization").requiredauthorizations.sites @> ARRAY['type_de_sitesKlaboratoire', 'type_de_sitesKplateforme']::ltree[]
                         AND ("authorization").timescope && '["2023-05-15 00:00:00","2023-05-16 00:00:00")'::tsrange""",
                sqlFilter);
    }

    @Test
    void testSqlFilterForAuthorizationWithEmptyFields() {
        Map<String, List<Ltree>> authorizationScope = Map.of(
                "projet", List.of(),
                "sites", List.of(Ltree.fromSql("type_de_sitesKplateforme"))
        );
        AuthorizationForScope authorization = new AuthorizationForReferenceScope(Set.of(), authorizationScope);
        String sqlFilter = authorizationIndex.sqlFilterForAuthorization(PEM, authorization, false);
        assertEquals("""
                        referencetype ='pem'
                         AND ("authorization").requiredauthorizations.projet IS NULL
                         AND ("authorization").requiredauthorizations.sites @> ARRAY['type_de_sitesKplateforme']::ltree[]""",
                sqlFilter);
    }
}
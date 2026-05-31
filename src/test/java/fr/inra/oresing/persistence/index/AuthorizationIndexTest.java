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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        Mockito.when(application.getAllDataNames()).thenReturn(List.of(PEM));
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
                            ref_types TEXT[] := ARRAY['pem'];
                            ref_type TEXT;
                        BEGIN
                            FOREACH ref_type IN ARRAY ref_types
                            LOOP
                                FOR idx IN (SELECT indexname FROM pg_indexes
                                            WHERE schemaname = 'monsore'
                                            AND indexname LIKE 'authorization_' || ref_type || '_index%')
                                LOOP
                                    EXECUTE 'DROP INDEX IF EXISTS monsore.' || quote_ident(idx.indexname);
                                END LOOP;
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
    void createIndexWithNoFilterModelSkipsRefvaluesIndex() {
        StandardDataDescription pem = dataDescription(FilterModel.NONE, Map.of(), new Authorization(
                List.of(
                        new AuthorizationScopeComponentData("projet", "dataProjet"),
                        new AuthorizationScopeComponentData("sites", "dataSites")
                ),
                "timeScope"
        ));
        AuthorizationIndex index = new AuthorizationIndex(applicationFor(pem));

        assertEquals(
                """
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
                index.createIndex(PEM));
    }

    @Test
    void createIndexWithDefinedFiltersCreatesColumnIndexes() {
        ComponentDescription listComponent = ComponentDescriptionBuilder.basicComponent()
                .componentKey("status")
                .tags(Set.of(Tag.FilterListTag.instance()))
                .build();
        ComponentDescription textComponent = ComponentDescriptionBuilder.basicComponent()
                .componentKey("comment")
                .tags(Set.of(Tag.FilterTextTag.instance()))
                .build();
        Map<String, ComponentDescription> components = new LinkedHashMap<>();
        components.put("status", listComponent);
        components.put("comment", textComponent);
        StandardDataDescription pem = dataDescription(FilterModel.DEFINED_FILTERS, components, null);
        AuthorizationIndex index = new AuthorizationIndex(applicationFor(pem));

        assertEquals(
                """
                        CREATE INDEX IF NOT EXISTS authorization_pem_index_filter_status_index
                        ON monsore.referencevalue ((refvalues->>'status'))
                        WHERE referencetype = 'pem';
                        
                        CREATE INDEX IF NOT EXISTS authorization_pem_index_filter_comment_text_index
                        ON monsore.referencevalue USING gin (lower(refvalues->>'comment') gin_trgm_ops)
                        WHERE referencetype = 'pem';
                        
                        """,
                index.createIndex(PEM));
        assertEquals(Set.of(
                        "authorization_pem_index_filter_status_index",
                        "authorization_pem_index_filter_comment_text_index"),
                index.expectedIndexNames());
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
                FilterModel.LEGACY_GIN,
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
                null,
                null
        );
    }

    private Application applicationFor(StandardDataDescription pem) {
        Application application = Mockito.mock(Application.class, "mockedApplicationForFilterModel");
        Configuration configuration = Mockito.mock(Configuration.class, "mockedConfigurationForFilterModel");
        Mockito.when(application.getName()).thenReturn(MONSORE);
        Mockito.when(application.getConfiguration()).thenReturn(configuration);
        Mockito.when(configuration.dataDescription()).thenReturn(Map.of(PEM, pem));
        Mockito.when(application.findData(PEM)).thenReturn(Optional.of(pem));
        Mockito.when(application.getAllDataNames()).thenReturn(List.of(PEM));
        return application;
    }

    private StandardDataDescription dataDescription(FilterModel filterModel, Map<String, ComponentDescription> components, Authorization authorization) {
        return new StandardDataDescription(
                ';',
                null,
                null,
                null,
                null,
                filterModel,
                null,
                components,
                null,
                authorization,
                null,
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
package fr.inra.oresing.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.domain.application.Application;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-application repository pour la table de cache materialise des
 * dropdowns de scope ( ecran DataVersioningView ) .
 *
 * <p>La table {@code <app>.data_versioning_scope_cache} stocke , par
 * ( application , reference_type , column_name , user_id ) , la liste
 * pre-calculee des valeurs distinctes visibles par cet utilisateur
 * pour ce referentiel . Cf. migration V8 et
 * {@link fr.inra.oresing.cache.DataVersioningScopeCacheService} .
 *
 * <p>Pas d'extension de {@link JsonTableInApplicationSchemaRepositoryTemplate} :
 * le payload est un simple JSONB sans entite metier riche , l'utilisation
 * directe de {@code NamedParameterJdbcTemplate} suffit .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DataVersioningScopeCacheRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Application application;
    private final SqlSchemaForApplication schema;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    public DataVersioningScopeCacheRepository(final Application application) {
        this.application = application;
        this.schema = SqlSchema.forApplication(application);
    }

    /**
     * Cle composite : ( application , reference_type , column_name , user_id ) .
     */
    public Optional<List<List<String>>> find(String referenceType, String columnName, UUID userId) {
        String query = """
                SELECT visible_values::text
                FROM %s.data_versioning_scope_cache
                WHERE application    = :application::uuid
                  AND reference_type = :referenceType
                  AND column_name    = :columnName
                  AND user_id        = :userId::uuid
                """.formatted(schema.getSqlIdentifier());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("application", application.getId())
                .addValue("referenceType", referenceType)
                .addValue("columnName", columnName)
                .addValue("userId", userId);
        try {
            String json = jdbc.queryForObject(query, params, String.class);
            return Optional.ofNullable(json).map(this::deserialize);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Upsert ( REPLACE ) de l'entree cache . Le compute_at est rafraichi
     * a chaque ecriture .
     */
    public void put(String referenceType, String columnName, UUID userId, List<List<String>> values) {
        String query = """
                INSERT INTO %s.data_versioning_scope_cache
                    (application, reference_type, column_name, user_id, visible_values, computed_at)
                VALUES (:application::uuid, :referenceType, :columnName, :userId::uuid, :visibleValues::jsonb, now())
                ON CONFLICT (application, reference_type, column_name, user_id)
                DO UPDATE SET
                    visible_values = EXCLUDED.visible_values,
                    computed_at    = EXCLUDED.computed_at
                """.formatted(schema.getSqlIdentifier());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("application", application.getId())
                .addValue("referenceType", referenceType)
                .addValue("columnName", columnName)
                .addValue("userId", userId)
                .addValue("visibleValues", serialize(values));
        jdbc.update(query, params);
    }

    /**
     * Invalidation par referencetype ( hook : YAML edit qui mute la
     * definition d'un referentiel , ou cleanup post-mutation manuel ) .
     * Note : les triggers SQL statement-level sur referencevalue gerent
     * deja l'invalidation automatique sur INSERT/DELETE de data ; cette
     * methode est pour les cas hors-trigger .
     */
    public int invalidateByReferenceType(String referenceType) {
        String query = """
                DELETE FROM %s.data_versioning_scope_cache
                WHERE application    = :application::uuid
                  AND reference_type = :referenceType
                """.formatted(schema.getSqlIdentifier());
        return jdbc.update(query, new MapSqlParameterSource()
                .addValue("application", application.getId())
                .addValue("referenceType", referenceType));
    }

    /**
     * Invalidation par user ( hook : grant / revoke d'un scope a ce user
     * change ce qu'il a le droit de voir ) .
     */
    public int invalidateByUserId(UUID userId) {
        String query = """
                DELETE FROM %s.data_versioning_scope_cache
                WHERE user_id = :userId::uuid
                """.formatted(schema.getSqlIdentifier());
        return jdbc.update(query, new MapSqlParameterSource().addValue("userId", userId));
    }

    /**
     * Invalidation totale ( hook : YAML edit / admin invalidate-caches ) .
     */
    public int invalidateAll() {
        String query = """
                DELETE FROM %s.data_versioning_scope_cache
                WHERE application = :application::uuid
                """.formatted(schema.getSqlIdentifier());
        return jdbc.update(query, new MapSqlParameterSource()
                .addValue("application", application.getId()));
    }

    /**
     * Compteur d'entrees cache pour cette application ( endpoint admin
     * d'observabilite ) .
     */
    public long count() {
        String query = """
                SELECT count(*)
                FROM %s.data_versioning_scope_cache
                WHERE application = :application::uuid
                """.formatted(schema.getSqlIdentifier());
        Long c = jdbc.queryForObject(query, new MapSqlParameterSource()
                .addValue("application", application.getId()), Long.class);
        return c == null ? 0L : c;
    }

    private String serialize(List<List<String>> values) {
        try {
            return JSON.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize visible_values", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> deserialize(String json) {
        try {
            return JSON.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize visible_values", e);
        }
    }
}

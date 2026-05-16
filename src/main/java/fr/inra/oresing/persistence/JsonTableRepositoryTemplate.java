package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import fr.inra.oresing.domain.OreSiEntity;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

abstract class JsonTableRepositoryTemplate<T extends OreSiEntity> implements InitializingBean {
    @Autowired
    private TransactionTemplate transactionTemplate;


    @Autowired
    private JsonRowMapper<T> jsonRowMapper;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public void afterPropertiesSet() {
        // pour force la recuperation petit a petit et pas tout en meme temps (probleme memoire)
        namedParameterJdbcTemplate.getJdbcTemplate().setFetchSize(1000);
    }

    protected NamedParameterJdbcTemplate getNamedParameterJdbcTemplate() {
        return namedParameterJdbcTemplate;
    }

    private UnmodifiableIterator<List<T>> partition(final Stream<T> stream) {
        // 7min19 pour 10
        // 6min07 pour 30
        // 6min15 pour 40
        // 5min46 pour 50
        // 5min48 pour 100
        // 5min50 pour 500
        // 6min21 pour 1000
        // the SELECT CURRENT_USER round-trip per call was a leftover
        // debug query - removed.
        return Iterators.partition(stream.iterator(), 50);
    }

    public List<UUID> storeAll(final Stream<T> stream) {
        // two SELECT round-trips ( CURRENT_USER + per-entity login )
        // removed. The login was assigned to a local that was never used ;
        // the result was dead weight on the hot path of every storeAll().
        final String query = getUpsertQuery();
        final List<UUID> uuids = new LinkedList<>();
        partition(stream).forEachRemaining(entities -> {
            entities.forEach(e -> {
                if (e.getId() == null) {
                    e.setId(UUID.randomUUID());
                }
            });
            //jsonRowMapper.getJsonMapper().setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CASE);
            final String json = jsonRowMapper.toJson(entities);
            try {
                uuids.addAll(namedParameterJdbcTemplate.queryForList(
                        query, new MapSqlParameterSource("json", json), UUID.class));
            } catch (final Exception e) {
                Pattern pattern = Pattern.compile(
                        "new row violates row-level security policy for\\s+\"([^\"]+)\"",
                        Pattern.DOTALL
                );                Matcher matcher = pattern.matcher(Objects.requireNonNull(e.getMessage()));
                Matcher matcher2 = pattern.matcher(Objects.requireNonNull(e.getCause().getMessage()));
                if (matcher.matches()) {
                    String table = matcher.group(1);
                    throw SiOreIllegalArgumentException.noRightOnTable(table);
                } else if (matcher2.matches()) {
                    String table = matcher2.group(1);
                    throw SiOreIllegalArgumentException.noRightOnTable(table);
                }
                throw e;
            }
        });
        return uuids;
    }

    protected abstract String getUpsertQuery();

    public UUID store(final T entity) {
        final UUID id = entity.getId();
        storeAll(Stream.of(entity));
        return id;
    }

    /**
     * Supprime des objets dans la base
     *
     * @param ids les identifiants des objets a supprimer (peut-etre null, dans ce cas, rien n'est supprimer)
     * @return vrai si un objet a été supprimé
     */
    public boolean delete(final List<UUID> ids) {
        final SqlTable table = getTable();
        final String query = String.format("DELETE FROM %s WHERE id::uuid IN (:ids)", table.getSqlIdentifier());
        final int count = namedParameterJdbcTemplate.update(query, new MapSqlParameterSource("ids", ids));
        return count > 0;
    }

    /**
     * Supprime un objet dans la base
     *
     * @param id l'identifiant de l'objet a supprimer (peut-etre null, dans ce cas, rien n'est supprimer)
     * @return vrai si un objet a été supprimé
     */
    public boolean delete(final UUID id) {
        final SqlTable table = getTable();
        final String query = String.format("DELETE FROM %s WHERE id=:id", table.getSqlIdentifier());
        final int count = namedParameterJdbcTemplate.update(query, new MapSqlParameterSource("id", id));
        return count > 0;
    }

    protected abstract SqlTable getTable();

    protected JsonRowMapper<T> getJsonRowMapper() {
        return jsonRowMapper;
    }

    public T findById(final UUID id) {
        return tryFindById(id).orElseThrow(() -> new NoSuchElementException(id + " dans la table " + getTable()));
    }

    public Optional<T> tryFindById(final UUID id) {
        Preconditions.checkArgument(id != null);
        final String query = String.format("SELECT '%s' as \"@class\", to_jsonb(t) as json FROM %s t WHERE id = :id", getEntityClass().getName(), getTable().getSqlIdentifier());
        return namedParameterJdbcTemplate.query(query, new MapSqlParameterSource("id", id), jsonRowMapper).stream().findFirst();
    }

    protected abstract Class<T> getEntityClass();

    public List<T> findAll() {
        return find(null, EmptySqlParameterSource.INSTANCE);
    }

    public Stream<T> findAllStream() {
        return findStream(null, EmptySqlParameterSource.INSTANCE);
    }

    protected List<T> findByPropertyEquals(final String property, final Object value) {
        return find(property + " = :" + property, new MapSqlParameterSource(property, value));
    }

    protected List<T> find(final String whereClause, final SqlParameterSource sqlParameterSource) {
        String sql = "SELECT '%s' as \"@class\",  to_jsonb(t) as json FROM %s t";
        if (whereClause != null) {
            sql += " WHERE " + whereClause;
        }
        final String query = String.format(sql, getEntityClass().getName(), getTable().getSqlIdentifier());
        return namedParameterJdbcTemplate.query(query, sqlParameterSource, jsonRowMapper);
    }

    protected Stream<T> findStream(final String whereClause, final SqlParameterSource sqlParameterSource) {
        String sql = "SELECT '%s' as \"@class\",  to_jsonb(t) as json FROM %s t";
        if (whereClause != null) {
            sql += " WHERE " + whereClause;
        }
        final String query = String.format(sql, getEntityClass().getName(), getTable().getSqlIdentifier());
        return namedParameterJdbcTemplate.queryForStream(query, sqlParameterSource, jsonRowMapper);
    }

    public void flush() {
        Objects.requireNonNull(transactionTemplate.getTransactionManager()).getTransaction(transactionTemplate).flush();
    }
}
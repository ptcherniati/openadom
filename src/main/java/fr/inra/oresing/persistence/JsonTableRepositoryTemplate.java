package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import fr.inra.oresing.model.OreSiEntity;
import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.BadSqlGrammarException;
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

    private UnmodifiableIterator<List<T>> partition(Stream<T> stream) {
        // 7min19 pour 10
        // 6min07 pour 30
        // 6min15 pour 40
        // 5min46 pour 50
        // 5min48 pour 100
        // 5min50 pour 500
        // 6min21 pour 1000
        final String s = namedParameterJdbcTemplate.queryForObject("select CURRENT_USER::TEXT;", Map.of(), String.class);
        return Iterators.partition(stream.iterator(), 50);
    }

    public List<UUID> storeAll(Stream<T> stream) {
        final String s = namedParameterJdbcTemplate.queryForObject("select CURRENT_USER::TEXT;", Map.of(), String.class);
        String query = getUpsertQuery();
        List<UUID> uuids = new LinkedList<>();
        partition(stream).forEachRemaining(entities -> {
            entities.forEach(e -> {
                if (e.getId() == null) {
                    final String a = namedParameterJdbcTemplate.queryForObject("select login from oresiuser where id::TEXT=CURRENT_USER::TEXT;", Map.of(), String.class);
                    e.setId(UUID.randomUUID());
                }
            });
            String json = getJsonRowMapper().toJson(entities);
            try{
                uuids.addAll(namedParameterJdbcTemplate.queryForList(
                        query, new MapSqlParameterSource("json", json), UUID.class));
            }catch (BadSqlGrammarException bsge){
                final Pattern pattern = Pattern.compile(".*new row violates row-level security policy for.*\"(.*)\".*", Pattern.DOTALL);
                final Matcher matcher = pattern.matcher(bsge.getMessage());
                if(matcher.matches()){
                    final String table = matcher.group(1);
                    throw new SiOreIllegalArgumentException("noRightOnTable", Map.of(
                                    "table", table)
                            );
                }
                throw bsge;
            }
        });
        return uuids;
    }

    protected abstract String getUpsertQuery();

    public UUID store(T entity) {
        UUID id = entity.getId();
        storeAll(Stream.of(entity));
        return id;
    }

    /**
     * Supprime un objet dans la base
     *
     * @param id l'identifiant de l'objet a supprimer (peut-etre null, dans ce cas, rien n'est supprimer)
     * @return vrai si un objet a été supprimé
     */
    public boolean delete(UUID id) {
        SqlTable table = getTable();
        String query = String.format("DELETE FROM %s WHERE id=:id", table.getSqlIdentifier());
        int count = namedParameterJdbcTemplate.update(query, new MapSqlParameterSource("id", id));
        return count > 0;
    }

    protected abstract SqlTable getTable();

    protected JsonRowMapper<T> getJsonRowMapper() {
        return jsonRowMapper;
    }

    public T findById(UUID id) {
        return tryFindById(id).orElseThrow(() -> new NoSuchElementException(id + " dans la table " + getTable()));
    }

    public Optional<T> tryFindById(UUID id) {
        Preconditions.checkArgument(id != null);
        String query = String.format("SELECT '%s' as \"@class\", to_jsonb(t) as json FROM %s t WHERE id = :id", getEntityClass().getName(), getTable().getSqlIdentifier());
        Optional<T> result = namedParameterJdbcTemplate.query(query, new MapSqlParameterSource("id", id), getJsonRowMapper()).stream().findFirst();
        return result;
    }

    protected abstract Class<T> getEntityClass();

    public List<T> findAll() {
        return find(null, EmptySqlParameterSource.INSTANCE);
    }

    protected List<T> findByPropertyEquals(String property, Object value) {
        return find(property + " = :" + property, new MapSqlParameterSource(property, value));
    }

    protected List<T> find(String whereClause, SqlParameterSource sqlParameterSource) {
        String sql = "SELECT '%s' as \"@class\",  to_jsonb(t) as json FROM %s t";
        if (whereClause != null) {
            sql += " WHERE " + whereClause;
        }
        String query = String.format(sql, getEntityClass().getName(), getTable().getSqlIdentifier());
        List<T> result = namedParameterJdbcTemplate.query(query, sqlParameterSource, getJsonRowMapper());
        return result;
    }

    public void flush() {
        transactionTemplate.getTransactionManager().getTransaction(transactionTemplate).flush();
    }
}
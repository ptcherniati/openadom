package fr.inra.oresing.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class JsonRowMapper<T> implements RowMapper<T> {

    /**
     * Mapper json pour la persistence (dialogue avec la base de données)
     */
    private ObjectMapper jsonMapper;

    public JsonRowMapper() {
        jsonMapper = new ObjectMapper();
        // there is no case in SQL, but in java we love camelCase :p
        jsonMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CASE)
        ;
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            Class<T> type = (Class<T>)Class.forName(rs.getString("@class"));
            String json = rs.getString("json");
            T result = jsonMapper.readValue(json, type);
            return result;
        } catch (ClassNotFoundException | IOException eee) {
            throw new SQLException("Can't convert result from database to object", eee);
        }
    }

    public String toJson(Object e) {
        try {
            return jsonMapper.writeValueAsString(e);
        } catch (JsonProcessingException eee) {
            throw new IllegalArgumentException("Can't convert argument to json: " + e, eee);
        }
    }
}

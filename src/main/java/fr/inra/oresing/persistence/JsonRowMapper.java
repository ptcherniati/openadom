package fr.inra.oresing.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class JsonRowMapper<T> implements RowMapper<T> {

    @Autowired
    @Qualifier("sqlJsonMapper")
    private ObjectMapper jsonMapper;

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
}

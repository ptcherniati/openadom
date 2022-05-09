package fr.inra.oresing.persistence;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.inra.oresing.model.LocalDateTimeRange;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

@Component
public class JsonRowMapper<T> implements RowMapper<T> {

    /**
     * Mapper json pour la persistence (dialogue avec la base de données)
     */
    private final ObjectMapper jsonMapper;

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

        SimpleModule module = new SimpleModule()
                .addSerializer(LocalDateTimeRange.class, new JsonSerializer<>() {
                    @Override
                    public void serialize(LocalDateTimeRange value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                        gen.writeString(value.toSqlExpression());
                    }
                })
                .addDeserializer(LocalDateTimeRange.class, new JsonDeserializer<>() {
                    @Override
                    public LocalDateTimeRange deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                        return LocalDateTimeRange.parseSql(p.getText());
                    }
                })
                .addSerializer(Ltree.class, new JsonSerializer<>() {
                    @Override
                    public void serialize(Ltree value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                        gen.writeString(value.getSql());
                    }
                })
                .addDeserializer(Ltree.class, new JsonDeserializer<>() {
                    @Override
                    public Ltree deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                        return Ltree.fromSql(p.getText());
                    }
                })
                .addSerializer(ReferenceDatum.class, new JsonSerializer<>() {
                    @Override
                    public void serialize(ReferenceDatum value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                        gen.writeObject(value.toJsonForDatabase());
                    }
                })
                .addDeserializer(ReferenceDatum.class, new JsonDeserializer<>() {
                    @Override
                    public ReferenceDatum deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                        Map map = p.readValueAs(Map.class);
                        return ReferenceDatum.fromDatabaseJson(map);
                    }
                })
                ;
        jsonMapper.registerModule(module);
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            Class<T> type = (Class<T>)Class.forName(rs.getString("@class"));
            String json = rs.getString("json");
            T result = jsonMapper.readValue(json, type);
            return result;
        } catch (JsonProcessingException eee) {
            throw new SiOreIllegalArgumentException(
                    "sqlConvertException",
                    Map.of(
                            "originalMessage", eee.getOriginalMessage(),
                            "locationLineNumber", eee.getLocation().getColumnNr(),
                            "locationColumnNumber", eee.getLocation().getLineNr(),
                            "message", eee.getMessage()
                    )
            );
            // throw new SQLException("Can't convert result from database to object", eee);
        }catch (ClassNotFoundException eee) {
            throw new SiOreIllegalArgumentException(
                    "sqlConvertExceptionForClass",
                    Map.of(
                            "message", eee.getLocalizedMessage()
                    )
            );
            // throw new SQLException("Can't convert result from database to object", eee);
        } catch (IOException e) {
            throw new SiOreIllegalArgumentException(
                    "IOException",
                    Map.of(
                            "message", e.getLocalizedMessage()
                    )
            );
        }
    }

    public String toJson(Object e) {
        try {
            return jsonMapper.writeValueAsString(e);
        } catch (JsonProcessingException eee) {
            throw new SiOreIllegalArgumentException(
                    "sqlConvertException",
                    Map.of(
                            "originalMessage", eee.getOriginalMessage(),
                            "locationLineNumber", eee.getLocation().getColumnNr(),
                            "locationColumnNumber", eee.getLocation().getLineNr(),
                            "message", eee.getMessage()
                    )
            );
            // throw new SQLException("Can't convert result from database to object", eee);
        } catch (IOException eee) {
            throw new SiOreIllegalArgumentException(
                    "IOException",
                    Map.of(
                            "message", eee.getLocalizedMessage()
                    )
            );
        }
    }
}
package fr.inra.oresing.persistence;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.Mapper;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.*;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.authorization.request.*;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.checker.type.AbstractType;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.groovy.StringGroovyExpression;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import lombok.Getter;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Primary
public class JsonRowMapper<T> implements RowMapper<T>, Mapper {

    public JsonRowMapper(PropertyNamingStrategy strategies) {
        buildMapper();
    }

    /**
     * Mapper json pour la persistence (dialogue avec la base de données)
     */
    @Getter
    private ObjectMapper jsonMapper;

    public JsonRowMapper() {
        buildMapper();
    }

    private void buildMapper() {
        this.jsonMapper = JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(new JavaTimeModule())
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CASE)
                .build();


        SimpleModule module = new SimpleModule()
                .addDeserializer(Ltree.class, getLtreeDeserializer())
                .addDeserializer(LocalDateTimeRange.class, getLocalDateTimeRangeJsonDeserializer())
                .addDeserializer(FieldDescription.class, getFieldDescriptionJsonDeserializer())
                .addDeserializer(Depends.class, getDependsJsonDeserializer())
                .addDeserializer(Tag.class, getTagJsonDeserializer())
                .addDeserializer(CheckerDescription.class, getDescriptionJsonDeserializer())
                .addDeserializer(ComponentDescription.class, getComponentDescriptionJsonDeserializer())
                .addDeserializer(ConstantImport.class, getImportJsonDeserializer())
                .addDeserializer(AuthorizationForScope.class, getAuthorizationForScopeJsonDeserializer())
                .addDeserializer(TemporalAccessor.class, getTemporalAccessorJsonDeserializer())
                .addDeserializer(DataDatum.class, getDataDatumJsonDeserializer())
                .addDeserializer(FieldType.class, getFieldTypeJsonDeserializer())
                .addSerializer(LocalDateTimeRange.class, getLocalDateTimeRangeJsonSerializer())
                .addSerializer(ValidationError.class, getValidationErrorJsonSerializer())
                .addSerializer(InvalidDatasetContentException.class, getInvalidDatasetContentExceptionJsonSerializer())
                .addSerializer(Ltree.class, getLtreeJsonSerializer())
                .addSerializer(DataDatum.class, getDataDatumJsonSerializer())
                .addSerializer(FieldType.class, getFieldTypeJsonSerializer())
                .addSerializer(StringGroovyExpression.class, getStringGroovyExpressionJsonSerializer());
        jsonMapper.registerModule(module);
        jsonMapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public Object handleUnexpectedToken(DeserializationContext ctxt,
                                                JavaType targetType,
                                                JsonToken t,
                                                JsonParser p,
                                                String failureMsg) throws IOException {
                if (String.class == targetType.getRawClass() && (JsonToken.START_ARRAY == t || JsonToken.START_OBJECT == t)) {
                    return null;
                }
                return super.handleUnexpectedToken(ctxt, targetType, t, p, failureMsg);
            }
        });
    }

    private static JsonSerializer<StringGroovyExpression> getStringGroovyExpressionJsonSerializer() {
        return new JsonSerializer<>() {
            @Override
            public void serialize(StringGroovyExpression value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.toString());
            }
        };
    }

    private static JsonSerializer<FieldType> getFieldTypeJsonSerializer() {
        return new JsonSerializer<>() {
            @Override
            public void serialize(FieldType value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                value.serialize(gen);
            }
        };
    }

    private static JsonSerializer<DataDatum> getDataDatumJsonSerializer() {
        return new JsonSerializer<>() {
            @Override
            public void serialize(DataDatum value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                ImmutableMap<String, FieldType<?>> jsonForDatabase = value.toJsonForDatabase();
                gen.writeStartObject();
                for (Map.Entry<String, FieldType<?>> fieldType : jsonForDatabase.entrySet()) {
                    fieldType.getValue().serialize(gen, fieldType.getKey());
                }
                gen.writeEndObject();
            }
        };
    }

    private static JsonSerializer<Ltree> getLtreeJsonSerializer() {
        return new JsonSerializer<>() {
            @Override
            public void serialize(Ltree value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.getSql());
            }
        };
    }

    private static JsonSerializer<InvalidDatasetContentException> getInvalidDatasetContentExceptionJsonSerializer() {
        return new JsonSerializer<>() {
            @Override
            public void serialize(InvalidDatasetContentException value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeObject(value);
            }
        };
    }

    private static JsonSerializer<ValidationError> getValidationErrorJsonSerializer() {
        return new JsonSerializer<>() {
            @Override
            public void serialize(ValidationError value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeObject(value.toJsonObject());
            }
        };
    }

    private static JsonSerializer<LocalDateTimeRange> getLocalDateTimeRangeJsonSerializer() {
        return new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTimeRange value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.toSqlExpression());
            }
        };
    }

    private static JsonDeserializer<FieldType<?>> getFieldTypeJsonDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public FieldType<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return AbstractType.readObject(p.readValueAs(Object.class));
            }
        };
    }

    private static JsonDeserializer<DataDatum> getDataDatumJsonDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public DataDatum deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                Map map = p.readValueAs(Map.class);
                return DataDatum.fromDatabaseJson(map);
            }
        };
    }

    private JsonDeserializer<TemporalAccessor> getTemporalAccessorJsonDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public TemporalAccessor deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.readValueAsTree();
                try {
                    return jsonMapper.convertValue(node, LocalDateTime.class);
                } catch (Exception e) {
                    try {
                        return jsonMapper.convertValue(node, LocalTime.class);
                    } catch (Exception ee) {
                        return jsonMapper.convertValue(node, LocalDate.class);
                    }
                }
            }
        };
    }

    private JsonDeserializer<AuthorizationForScope> getAuthorizationForScopeJsonDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public AuthorizationForScope deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.getCodec().readTree(p);
                if (null == node || node.isEmpty()) {
                    return new AuthorizationNoRestriction(Set.of());
                }
                final ArrayNode operationTypesNode = (ArrayNode) node.get("operationtypes");
                final JsonNode timeScope = node.get("timescope");
                final JsonNode authorizationScope = node.get("authorizationscope");
                Set<OperationType> operationTypes = extractOperationTypes(operationTypesNode);
                if (null == authorizationScope) {
                    return new AuthorizationForTimeScope(operationTypes, extractLocalDateTimeRange(timeScope));
                }
                if (null == timeScope) {
                    return new AuthorizationForReferenceScope(operationTypes, extractAuthorizationScope(authorizationScope));
                }

                return new AuthorizationForReferenceScopeAndTimeScope(
                        operationTypes,
                        extractAuthorizationScope(authorizationScope),
                        extractLocalDateTimeRange(timeScope)
                );
            }
        };
    }

    private JsonDeserializer<ConstantImport> getImportJsonDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public ConstantImport deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.readValueAsTree();
                ConstantImportHeader.ConstantImportHeaderType type = Optional.ofNullable(node.get("type")).map(JsonNode::asText).map(ConstantImportHeader.ConstantImportHeaderType::valueOf).orElse(ConstantImportHeader.ConstantImportHeaderType.FileConstantHeader);
                return switch (type) {
                    case MissingConstantImportHeader -> null;
                    case FileConstantHeader -> jsonMapper.convertValue(node, FileColumnConstantHeader.class);
                    case ColumnConstantHeaderByColumnNumber ->
                            jsonMapper.convertValue(node, ColumnConstantHeaderByColumnNumber.class);
                    case ColumnConstantHeaderByHeaderName ->
                            jsonMapper.convertValue(node, ColumnConstantHeaderByHeaderName.class);
                    case SubmissionComponent -> jsonMapper.convertValue(node, SubmissionConstantHeader.class);
                };
            }
        };
    }

    private JsonDeserializer<ComponentDescription> getComponentDescriptionJsonDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public ComponentDescription deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.readValueAsTree();
                ComponentDescription.ComponentDescriptionType type = Optional.ofNullable(node.get("type")).map(JsonNode::asText).map(ComponentDescription.ComponentDescriptionType::valueOf).orElse(ComponentDescription.ComponentDescriptionType.BasicComponent);
                return switch (type) {
                    case AuthorizationScopeComponent -> jsonMapper.convertValue(node, ReferenceScopeComponent.class);
                    case TagsDescription -> jsonMapper.convertValue(node, FilteredDescriptionComponent.class);
                    case ComputedComponent -> jsonMapper.convertValue(node, ComputedComponent.class);
                    case DynamicComponent -> jsonMapper.convertValue(node, DynamicComponent.class);
                    case BasicComponent -> jsonMapper.convertValue(node, BasicComponent.class);
                    case ConstantComponent -> jsonMapper.convertValue(node, ConstantComponent.class);
                    case PatternComponent -> jsonMapper.convertValue(node, PatternComponent.class);
                    case PatternComponentQualifiers -> jsonMapper.convertValue(node, PatternComponentQualifiers.class);
                    case PatternComponentAdjacents -> jsonMapper.convertValue(node, PatternComponentAdjacents.class);
                };
            }
        };
    }

    private JsonDeserializer<CheckerDescription> getDescriptionJsonDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public CheckerDescription deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.readValueAsTree();
                CheckerDescription.CheckerDescriptionType type = Optional.ofNullable(node.get("type")).map(JsonNode::asText).map(CheckerDescription.CheckerDescriptionType::valueOf).orElse(CheckerDescription.CheckerDescriptionType.StringChecker);
                return switch (type) {
                    case ReferenceChecker -> jsonMapper.convertValue(node, ReferenceChecker.class);
                    case BooleanChecker -> jsonMapper.convertValue(node, BooleanChecker.class);
                    case ComputationChecker -> jsonMapper.convertValue(node, ComputationChecker.class);
                    case DateChecker -> jsonMapper.convertValue(node, DateChecker.class);
                    case FloatChecker -> jsonMapper.convertValue(node, FloatChecker.class);
                    case GroovyExpressionChecker -> jsonMapper.convertValue(node, GroovyExpressionChecker.class);
                    case IntegerChecker -> jsonMapper.convertValue(node, IntegerChecker.class);
                    case StringChecker -> jsonMapper.convertValue(node, StringChecker.class);
                };
            }
        };
    }

    private static JsonDeserializer<Tag> getTagJsonDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public Tag deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.readValueAsTree();
                Tag.TagDefinitions type = Optional.ofNullable(node.get("tagdefinition")).map(JsonNode::asText).map(Tag.TagDefinitions::valueOf).orElse(Tag.TagDefinitions.NO_TAG);
                return switch (type) {
                    case NO_TAG -> node.isTextual() ? Tag.buildTag(node.asText()) : Tag.NoTag.instance();
                    case DATA_TAG -> Tag.DataTag.instance();
                    case REFFERENCE_TAG -> Tag.ReferenceTag.instance();
                    case HIDDEN_TAG -> Tag.HiddenTag.instance();
                    case ORDER_TAG ->
                            Optional.ofNullable(node.get("tagorder")).map(JsonNode::asInt).map(Tag.OrderTag::new).orElse(Tag.OrderTag.ORDER_TAG_NOUGHT);
                    case DOMAIN_TAG ->
                            Optional.ofNullable(node.get("tagname")).map(JsonNode::asText).map(Tag.DomainTag::new).orElse(new Tag.DomainTag(""));
                };
            }
        };
    }

    private JsonDeserializer<Depends> getDependsJsonDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public Depends deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.readValueAsTree();
                Depends.DependsType type = Optional.ofNullable(node.get("type")).map(JsonNode::asText).map(Depends.DependsType::valueOf).orElse(Depends.DependsType.DependsReferences);
                return switch (type) {
                    case DependsParent -> jsonMapper.convertValue(node, DependsParent.class);
                    case DependsRecursive -> jsonMapper.convertValue(node, DependsRecursive.class);
                    case DependsReferences -> jsonMapper.convertValue(node, DependsReferences.class);
                };
            }
        };
    }

    private JsonDeserializer<FieldDescription> getFieldDescriptionJsonDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public FieldDescription deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonNode node = p.readValueAsTree();
                FieldDescription.FieldDescriptionType type = Optional.ofNullable(node.get("type")).map(JsonNode::asText).map(FieldDescription.FieldDescriptionType::valueOf).orElse(FieldDescription.FieldDescriptionType.RightsRequestField);

                return switch (type) {
                    case RightsRequestField -> jsonMapper.convertValue(node, RightsRequestField.class);
                    case AdditionalFileField -> jsonMapper.convertValue(node, AdditionalFileField.class);
                };
            }
        };
    }

    private static JsonDeserializer<LocalDateTimeRange> getLocalDateTimeRangeJsonDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public LocalDateTimeRange deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return LocalDateTimeRange.parseSql(p.getText());
            }
        };
    }

    private static JsonDeserializer<Ltree> getLtreeDeserializer() {
        return new JsonDeserializer<>() {
            @Override
            public Ltree deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return Ltree.fromSqlWithoutCheck(p.getText());
            }
        };
    }

    Set<OperationType> extractOperationTypes(final ArrayNode operationTypeNode) {
        return ((Set<String>) jsonMapper.convertValue(operationTypeNode, Set.class))
                .stream()
                .map(OperationType::valueOf)
                .collect(Collectors.toSet());
    }

    LocalDateTimeRange extractLocalDateTimeRange(final JsonNode localDateTimeRangeNode) {
        return jsonMapper.convertValue(localDateTimeRangeNode, LocalDateTimeRange.class);
    }

    Map<String, List<Ltree>> extractAuthorizationScope(final JsonNode authorizationScopeNode) {
        final Map<String, Object> map = jsonMapper.convertValue(authorizationScopeNode, Map.class);
        return map.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> ((List<String>) entry.getValue()).stream().map(Object::toString).map(Ltree::fromSql).toList()
                ));
    }

    public void disableInsensitiveProperties() {
        jsonMapper = jsonMapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);

    }

    public <C> C toObject(String json, Class<C> clazz) throws JsonProcessingException {
        return jsonMapper.readValue(json, clazz);
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            Class<T> type = (Class<T>) Class.forName(rs.getString("@class"));
            String json = rs.getString("json");
            return jsonMapper.readValue(json, type);
        } catch (JsonProcessingException eee) {
            throw new SiOreIllegalArgumentException(
                    "sqlConvertException",
                    Map.of(
                            "originalMessage", eee.getOriginalMessage(),
                            "locationLineNumber", Optional.ofNullable(eee.getLocation()).map(JsonLocation::getColumnNr).orElse(-1),
                            "locationColumnNumber", Optional.ofNullable(eee.getLocation()).map(JsonLocation::getLineNr).orElse(-1),
                            "message", eee.getMessage()
                    )
            );
            // throw new SQLException("Can't convert result from database to object", eee);
        } catch (ClassNotFoundException eee) {
            throw new SiOreIllegalArgumentException(
                    "sqlConvertExceptionForClass",
                    Map.of(
                            "message", eee.getLocalizedMessage()
                    )
            );
            // throw new SQLException("Can't convert result from database to object", eee);
        }
    }

    public <T> T readValue(String json, Class<T> clazz) throws IOException {
        return jsonMapper.readValue(json, clazz);
    }

    public <T> T readStream(InputStream stream, Class<T> clazz) throws IOException {
        return jsonMapper.readValue(stream, clazz);
    }

    public String toJson(Object e) {
        try {
            return jsonMapper
                    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                    .writeValueAsString(e);
        } catch (JsonProcessingException eee) {
            throw new SiOreIllegalArgumentException(
                    "sqlConvertException",
                    Map.of(
                            "originalMessage", Optional.of(eee).map(JsonProcessingException::getOriginalMessage).orElse(""),
                            "locationLineNumber", Optional.of(eee).map(JsonProcessingException::getLocation).map(JsonLocation::getColumnNr).orElse(-1),
                            "locationColumnNumber", Optional.of(eee).map(JsonProcessingException::getLocation).map(JsonLocation::getLineNr).orElse(-1),
                            "message", Optional.of(eee).map(JsonProcessingException::getMessage).orElse("")
                    )
            );
            // throw new SQLException("Can't convert result from database to object", eee);
        }
    }

    public <T> T convertValue(Object fromValue, Class<T> toValueType) {
        return jsonMapper.convertValue(fromValue, toValueType);
    }
}
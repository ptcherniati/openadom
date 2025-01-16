package fr.inra.oresing.domain.application.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.persistence.JsonRowMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import java.util.*;

@org.junit.jupiter.api.Tag("SUITE")
class ConfigurationTest {

    public static final JsonRowMapper MAPPER = new JsonRowMapper<ComponentDescription>();
    public static final JsonRowMapper<StandardDataDescription> mapper = new JsonRowMapper<>();
    public static JsonNode dataDescriptionNode;
    public static StandardDataDescription dataDescription;
    final Configuration configuration = Mockito.mock(Configuration.class);

    @BeforeEach
    public void buildContext() throws JsonProcessingException {
        dataDescriptionNode = new ObjectMapper().readTree("""
                {
                     "separator" : ";",
                     "headerLine" : 1,
                     "firstRowLine" : 2,
                     "allowUnexpectedColumns" : false,
                     "tags" : [ {
                       "tagDefinition" : "DOMAIN_TAG",
                       "tagName" : "data"
                     } ],
                     "naturalKey" : [ "first", "second","third" ],
                     "componentDescriptions" : {
                       "third" : {
                          "type" : "BasicComponent",
                          "componentkey" : "third",
                          "defaultvalue" : null,
                          "tags" : [ {
                            "tagdefinition" : "NO_TAG",
                            "tagname" : "no_tag"
                          } ],
                          "importheader" : "3",
                          "exportheadername" : "3",
                          "required" : false,
                          "mandatory" : "OPTIONAL",
                          "checker" : null,
                          "submissionauthorizationscope" : "",
                          "hidden" : false,
                          "referencecheckertype" : {
                            "empty" : true,
                            "present" : false
                          },
                          "chartdescription" : null,
                          "reference" : false
                        },
                       "first" : {
                          "type" : "BasicComponent",
                          "componentkey" : "first",
                          "defaultvalue" : null,
                          "tags" : [ {
                            "tagdefinition" : "NO_TAG",
                            "tagname" : "no_tag"
                          } ],
                          "importheader" : "1",
                          "exportheadername" : "1",
                          "required" : false,
                          "mandatory" : "OPTIONAL",
                          "checker" : null,
                          "submissionauthorizationscope" : "",
                          "hidden" : false,
                          "referencecheckertype" : {
                            "empty" : true,
                            "present" : false
                          },
                          "chartdescription" : null,
                          "reference" : false
                        },
                       "second" : {
                          "type" : "BasicComponent",
                          "componentkey" : "second",
                          "defaultvalue" : null,
                          "tags" : [ {
                            "tagdefinition" : "NO_TAG",
                            "tagname" : "no_tag"
                          } ],
                          "importheader" : "2",
                          "exportheadername" : "2",
                          "required" : false,
                          "mandatory" : "OPTIONAL",
                          "checker" : null,
                          "submissionauthorizationscope" : "",
                          "hidden" : false,
                          "referencecheckertype" : {
                            "empty" : true,
                            "present" : false
                          },
                          "chartdescription" : null,
                          "reference" : false
                        }
                     },
                     "submission" : null,
                     "authorization" : null,
                     "validations" : { },
                     "depends" : [ ],
                     "migrations" : null,
                     "hidden" : false,
                     "order" : 9999
                   }""");
        buildDataDescription();
        Mockito.doCallRealMethod().when(configuration).getInternationalizedSortedColumns(Mockito.anyString(), Mockito.eq("fr"), Mockito.any());
    }

    private void buildDataDescription() throws JsonProcessingException {
        dataDescription = MAPPER.getJsonMapper().readValue(
                MAPPER.toJson(
                        dataDescriptionNode
                ), StandardDataDescription.class);
        Mockito.doReturn(Optional.of(dataDescription)).when(configuration).findData(Mockito.anyString());
    }

    public record ComponentDefinition(String label, String name, Integer order) {
    }

    private JsonNode buildComponentNode(ComponentDefinition componentDefinition) {
        Set<fr.inra.oresing.domain.application.configuration.Tag> tags = componentDefinition.order() == null ?
                Set.of(fr.inra.oresing.domain.application.configuration.Tag.NoTag.instance()) :
                Set.of(new fr.inra.oresing.domain.application.configuration.Tag.OrderTag(componentDefinition.order()));
        BasicComponent component = new BasicComponent(
                ComponentDescription.ComponentDescriptionType.BasicComponent,
                componentDefinition.label(),
                null,
                tags,
                componentDefinition.name(),
                componentDefinition.name(),
                List.of(),
                false,
                ComponentPresenceConstraint.OPTIONAL,
                null,
                null
        );
        try {
            return MAPPER.getJsonMapper().readTree(
                    MAPPER.toJson(component)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    private void addComponents(List<ComponentDefinition> componentDefinitions) {
        ObjectNode componentsNode = (ObjectNode) dataDescriptionNode.get("componentDescriptions");
        componentDefinitions.stream()
                .map(this::buildComponentNode)
                .forEach(component -> componentsNode.set(
                        component.get("componentkey").asText(),
                        component)
                );
        buildDataDescription();
    }

    @Test
    void getSortedColumnsWithKeyThenAlphabeticOrderTest() {
        Map<String, Configuration.InternationalizedSortedColumn> internationalizedSortedColumns = configuration.getInternationalizedSortedColumns("component", "fr", new LinkedList<>());
        Assertions.assertIterableEquals(
                new LinkedHashSet<>(Arrays.asList("first", "second", "third")),
                internationalizedSortedColumns.keySet()
        );
    }

    @Test
    void getSortedColumnsWithKeyThenAlphabeticOrderAddNoOrderCommponentTest() {
        addComponents(List.of(
                new ComponentDefinition("sixth", "6", null),
                new ComponentDefinition("fourth", "4", null),
                new ComponentDefinition("fifth", "5", null)
        ));
        Map<String, Configuration.InternationalizedSortedColumn> internationalizedSortedColumns = configuration.getInternationalizedSortedColumns("component", "fr", new LinkedList<>());
        Assertions.assertIterableEquals(
                new LinkedHashSet<>(Arrays.asList("first", "second", "third", "fifth", "fourth", "sixth")),
                internationalizedSortedColumns.keySet()
        );
    }

    @Test
    void getSortedColumnsWithOrderThenAlphabeticOrderTest() {
        addComponents(List.of(
                new ComponentDefinition("sixth", "6", 1),
                new ComponentDefinition("fourth", "4", 2),
                new ComponentDefinition("fifth", "5", 2)
        ));
        Map<String, Configuration.InternationalizedSortedColumn> internationalizedSortedColumns = configuration.getInternationalizedSortedColumns("component", "fr", new LinkedList<>());
        Assertions.assertIterableEquals(
                new LinkedHashSet<>(Arrays.asList("sixth", "fifth", "fourth", "first", "second", "third")),
                internationalizedSortedColumns.keySet()
        );
    }

    @Test
    void getSortedColumnsWithLabelOrderUsingOrder() {
        addComponents(List.of(
                new ComponentDefinition("first", "1", 1),
                new ComponentDefinition("second", "2", 1),
                new ComponentDefinition("third", "3", 1),//order 1 then alphabetical
                new ComponentDefinition("fourth", "4", 2),
                new ComponentDefinition("fifth", "5", 3),
                new ComponentDefinition("sixth", "6", 3)//order 3 then alphabetical
        ));
        Map<String, Configuration.InternationalizedSortedColumn> internationalizedSortedColumns = configuration.getInternationalizedSortedColumns("component", "fr", new LinkedList<>());
        Assertions.assertIterableEquals(
                new LinkedHashSet<>(Arrays.asList("first", "second", "third", "fourth", "fifth", "sixth")),
                internationalizedSortedColumns.keySet()
        );
    }

    @Test
    void getSortedColumnsWithLabelOrderUsingOrderAndToBeSortedFirst() {
        addComponents(List.of(
                new ComponentDefinition("first", "1", 1),
                new ComponentDefinition("second", "2", 1),
                new ComponentDefinition("third", "3", 1),//order 1 then alphabetical
                new ComponentDefinition("fourth", "4", 2),
                new ComponentDefinition("fifth", "5", 3),
                new ComponentDefinition("sixth", "6", 3)//order 3 then alphabetical
        ));
        Map<String, Configuration.InternationalizedSortedColumn> internationalizedSortedColumns = configuration.getInternationalizedSortedColumns("component", "fr", new LinkedList<>(List.of("sixth")));
        Assertions.assertIterableEquals(
                new LinkedHashSet<>(Arrays.asList("sixth", "first", "second", "third", "fourth", "fifth")),
                internationalizedSortedColumns.keySet()
        );
    }
}
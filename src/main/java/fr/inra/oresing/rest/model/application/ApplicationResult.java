package fr.inra.oresing.rest.model.application;

import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.Node;
import fr.inra.oresing.domain.application.configuration.RightRequestDescription;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.rest.model.authorization.AuthorizationsForUserResult;
import fr.inra.oresing.rest.model.authorization.CurrentApplicationUserRolesResult;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;


public record ApplicationResult(
        String id,
        String name,
        String title,
        String comment,
        UUID configFile,
        Internationalizations internationalization,
        Map<String, StandardDataDescription> data,
        Map<String, Node> references,
        Map<String, Map<AuthorizationsForUserResult.Roles, Boolean>> authorizations,
        List<DataSynthesis> referenceSynthesis,
        Map<String, Node> dataTypes,
        Map<String, AdditionalFile> additionalFiles,
        ApplicationResult.RightsRequest rightsRequest,
        Configuration configuration,
        CurrentApplicationUserRolesResult currentApplicationUserRolesResult,
        Map<String, Set<String>> dependantNodesByDataName
) {

    public List<String> getOrderedReferences() {
        return configuration().hierarchicalNodes().stream()
                .sorted()
                .map(Node::nodeName)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Setter
    @Getter
    public static class DataSynthesis{
        String ReferenceType;
        int lineCount;

    }

    public record RightsRequest(RightRequestDescription description) {
    }


    public record AdditionalFile(Set<String> fields) {
    }


    public record Reference(String id, String label, Set<String> children, Map<String, Column> columns,
                            Map<String, DynamicColumn> dynamicColumns, Set<String> tags) {

        public record Column(String id, String title, boolean key, String linkedTo, Set<String> tags) {
        }


        public record DynamicColumn(String id, String title, String headerPrefix, String reference,
                                    String referenceColumnToLookForHeader, boolean presenceConstraint,
                                    Set<String> tags) {
        }


        public record ReferenceUUIDAndDisplay(String display, UUID uuid, Map<String, FieldType> values) {
        }
    }


    public record DataType(String id, String label, Map<String, Variable> variables,
                           ApplicationResult.DataType.Repository repository, boolean hasAuthorizations,
                           Set<String> tags) {

        public record Repository(String filePattern, Map<String, Integer> authorizationScope,
                                 TokenDateDescription startDate, TokenDateDescription endDate) {
        }


        public record TokenDateDescription(Integer token) {
        }


        public record Variable(String id, String label, Map<String, Component> components,
                               Chart chartDescription, Set<String> tags) {

            public record Component(String id, String label, Set<String> tags) {
            }


            public record Chart(String value, String unit, String gap, String standardDeviation,
                                String aggregation) {
            }
        }
    }

}
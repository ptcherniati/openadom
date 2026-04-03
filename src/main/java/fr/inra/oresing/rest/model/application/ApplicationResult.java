package fr.inra.oresing.rest.model.application;

import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.Node;
import fr.inra.oresing.domain.application.configuration.RightRequestDescription;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
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
        if(configuration==null) {
            return List.of();
        }
        return configuration().hierarchicalNodes().stream()
                .sorted()
                .map(Node::nodeName)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Setter
    @Getter
    public static class DataSynthesis {
        String referenceType;
        int lineCount;

    }

    public record RightsRequest(RightRequestDescription description) {
    }


    public record AdditionalFile(Set<String> fields) {
    }

}
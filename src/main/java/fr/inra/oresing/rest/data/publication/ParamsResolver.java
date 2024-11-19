package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;
import fr.inra.oresing.domain.file.FileOrUUID;
import groovyjarjarantlr4.v4.codegen.model.chunk.ListLabelRef;
import org.apache.commons.collections4.MapUtils;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public record ParamsResolver(AuthorizationPublicationService builder) implements State {

    public AuthorizationForUserBuilder resolveParams(
            Function<Map<String, List<Ltree>>, Map<String, List<Ltree>>> requiredAuthorizationResolver
    ) {
        Map<String, List<Ltree>> resolvedRequiredAuthorizations = Optional.ofNullable(params())
                .map(FileOrUUID::binaryfiledataset)
                .map(BinaryFileDataset::getRequiredAuthorizations)
                .map(requiredAuthorizationResolver)
                .orElse(null);
        Map<String, List<Ltree>> requiredAuthorizations = params()
                .binaryfiledataset()
                .getRequiredAuthorizations();
        Map<String, List<Ltree>> missingRequiredAuthorizations = requiredAuthorizations.keySet().stream()
                .filter(ref -> resolvedRequiredAuthorizations.get(ref) == null)
                .map(ref-> new AbstractMap.SimpleEntry<String, List<Ltree>>(ref, requiredAuthorizations.get(ref)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if(MapUtils.isNotEmpty(missingRequiredAuthorizations)){
            throw new SiOreAuthorizationRequestException(
                    AuthorizationRequestException.MISSING_REQUIRED_AUTHORIZATION,
                    Map.of(
                            "missingRequiredAuthorizations", missingRequiredAuthorizations
                    )
            );
        }
        //builder().getParams().binaryfiledataset().setRequiredAuthorizations(resolvedRequiredAuthorizations);
        return new AuthorizationForUserBuilder(builder());
    }
}

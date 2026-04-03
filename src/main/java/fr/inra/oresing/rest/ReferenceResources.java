package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.ApplicationInformation;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.model.reference.GetReferenceResult;
import fr.inra.oresing.rest.usecases.application.BuildOpenAdomUseCase;
import fr.inra.oresing.rest.usecases.application.GetApplicationOrAccordingToRightsUseCase;
import fr.inra.oresing.rest.usecases.application.GetApplicationUseCase;
import fr.inra.oresing.rest.usecases.data.FindReferenceUseCase;
import fr.inra.oresing.rest.usecases.data.GetFormatCheckedUseCase;
import fr.inra.oresing.rest.usecases.data.GetReferenceDisplaysByIdUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/applications/{nameOrId}/references")
@SecurityRequirement(name = "Bearer Authentication")
public class ReferenceResources {

    private final FindReferenceUseCase findReferenceUseCase;
    private final GetFormatCheckedUseCase getFormatCheckedUseCase;
    private final GetReferenceDisplaysByIdUseCase getReferenceDisplaysByIdUseCase;
    private final GetApplicationOrAccordingToRightsUseCase getApplicationOrAccordingToRightsUseCase;
    private final GetApplicationUseCase getApplicationUseCase;
    private final BuildOpenAdomUseCase buildOpenAdomUseCase;

    public ReferenceResources(
            FindReferenceUseCase findReferenceUseCase,
            GetFormatCheckedUseCase getFormatCheckedUseCase,
            GetReferenceDisplaysByIdUseCase getReferenceDisplaysByIdUseCase,
            GetApplicationOrAccordingToRightsUseCase getApplicationOrAccordingToRightsUseCase,
            GetApplicationUseCase getApplicationUseCase,
            BuildOpenAdomUseCase buildOpenAdomUseCase) {
        this.findReferenceUseCase = findReferenceUseCase;
        this.getFormatCheckedUseCase = getFormatCheckedUseCase;
        this.getReferenceDisplaysByIdUseCase = getReferenceDisplaysByIdUseCase;
        this.getApplicationOrAccordingToRightsUseCase = getApplicationOrAccordingToRightsUseCase;
        this.getApplicationUseCase = getApplicationUseCase;
        this.buildOpenAdomUseCase = buildOpenAdomUseCase;
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> listNameReferences(@PathVariable("nameOrId") final String nameOrId) {
        final Application application = getApplicationUseCase.execute(nameOrId);
        final ApplicationResult applicationResult = buildOpenAdomUseCase.execute(application, new String[]{ApplicationInformation.ALL.name()});
        return ResponseEntity.ok(applicationResult.getOrderedReferences());
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/{refType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetReferenceResult> getReference(
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("refType") final String refType,
            @RequestParam final MultiValueMap<String, String> params) {
        final List<DataValue> list = findReferenceUseCase.execute(nameOrId, refType, params);

        final Map<String, Map<String, LineChecker>> checkedFormatColumns = getFormatCheckedUseCase.execute(nameOrId, refType);
        Set<String> listOfReferenceIds = list.stream()
                .map(DataValue::getReferenceType)
                .collect(Collectors.toSet());
        final Map<Ltree, List<DataValue>> requiredReferencesValues = getReferenceDisplaysByIdUseCase.execute(
            getApplicationOrAccordingToRightsUseCase.execute(nameOrId),
            listOfReferenceIds);
        
        Map<String, String> referenceTypeForReferencingColumns =
                Optional.ofNullable(checkedFormatColumns.get(ReferenceType.class.getSimpleName()))
                        .map(checkedFormatColumn -> checkedFormatColumn.entrySet()
                                .stream()
                                .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                e -> Optional.of(e)
                                                        .map(Map.Entry::getValue)
                                                        .map(LineChecker::underlyingType)
                                                        .filter(ReferenceType.class::isInstance)
                                                        .map(c -> (ReferenceType) c)
                                                        .map(ReferenceType::getRefType)
                                                        .orElse("error")
                                        )
                                )
                        )
                        .orElseGet(LinkedHashMap::new);
        
        final ImmutableSet<GetReferenceResult.ReferenceValue> referenceValues = list.stream()
                .map(referenceValue ->
                        new GetReferenceResult.ReferenceValue(
                                referenceValue.getId().toString(),
                                referenceValue.getPatternColumnName(),
                                referenceValue.getHierarchicalKey().getSql(),
                                referenceValue.getNaturalKey().getSql(),
                                referenceValue.getRefValues().toJsonForFrontend(),
                                referenceValue.getRefsLinkedTo(),
                                referenceValue.getReferencingreferences()
                        )
                )
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetReferenceResult.ReferenceValue::commparingValue)));
        return ResponseEntity.ok(new GetReferenceResult(referenceValues, referenceTypeForReferencingColumns));
    }
}

package fr.inra.oresing.rest;

import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.rest.model.synthesis.SynthesisResult;
import fr.inra.oresing.rest.usecases.metadata.GetSynthesisUseCase;
import fr.inra.oresing.rest.usecases.metadata.GetSynthesisWithVariableUseCase;
import fr.inra.oresing.rest.usecases.metadata.BuildSynthesisUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/applications/{nameOrId}/synthesis")
@SecurityRequirement(name = "Bearer Authentication")
public class SynthesisResources {

    private final GetSynthesisUseCase getSynthesisUseCase;
    private final GetSynthesisWithVariableUseCase getSynthesisWithVariableUseCase;
    private final BuildSynthesisUseCase buildSynthesisUseCase;

    public SynthesisResources(
            GetSynthesisUseCase getSynthesisUseCase,
            GetSynthesisWithVariableUseCase getSynthesisWithVariableUseCase,
            BuildSynthesisUseCase buildSynthesisUseCase) {
        this.getSynthesisUseCase = getSynthesisUseCase;
        this.getSynthesisWithVariableUseCase = getSynthesisWithVariableUseCase;
        this.buildSynthesisUseCase = buildSynthesisUseCase;
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSynthesis(@PathVariable("nameOrId") final String nameOrId,
                                          @PathVariable("dataType") final String dataType) {
        try {
            final Map<String, List<OreSiSynthesis>> synthesis = getSynthesisUseCase.execute(nameOrId, dataType);
            final String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s", nameOrId, dataType), Charset.defaultCharset());
            Map<String, List<SynthesisResult>> synthesisResults = synthesis.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> e.getValue().stream().map(SynthesisResult::new).toList()
                            )
                    );
            return ResponseEntity.created(URI.create(uri)).body(synthesisResults);
        } catch (final InvalidDatasetContentException e) {
            final List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/{dataType}/{variable}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSynthesisWithVariable(@PathVariable("nameOrId") final String nameOrId,
                                                       @PathVariable("dataType") final String dataType,
                                                       @PathVariable("variable") final String variable) {
        try {
            final Map<String, List<OreSiSynthesis>> synthesis = getSynthesisWithVariableUseCase.execute(nameOrId, dataType, variable);
            final String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s/%s", nameOrId, dataType, variable), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(synthesis);
        } catch (final InvalidDatasetContentException e) {
            final List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_APPLICATION_MODIFY')")
    @PutMapping(value = "/{dataType}/{variable}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> buildSynthesis(@PathVariable("nameOrId") final String nameOrId,
                                            @PathVariable("dataType") final String dataType,
                                            @PathVariable("variable") final String variable) {
        try {
            final Map<String, List<OreSiSynthesis>> synthesis = buildSynthesisUseCase.execute(nameOrId, dataType, variable);
            final String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s/%s", nameOrId, dataType, variable), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(synthesis);
        } catch (final InvalidDatasetContentException e) {
            final List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_APPLICATION_MODIFY')")
    @PutMapping(value = "/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> buildSynthesisAll(@PathVariable("nameOrId") final String nameOrId,
                                               @PathVariable("dataType") final String dataType) {
        return buildSynthesis(nameOrId, dataType, null);
    }
}

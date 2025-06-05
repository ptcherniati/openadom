package fr.inra.oresing.rest.services;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.chart.Chart;
import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.domain.repository.synthesis.SynthesisRepository;
import fr.inra.oresing.persistence.DataSynthesisRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@Transactional(readOnly = true)
public class SynthesisService implements fr.inra.oresing.domain.services.synthesis.SynthesisService {

    final
    OreSiRepository repository;

    private final ServiceContainer serviceContainer;

    public SynthesisService(OreSiRepository repository, ServiceContainer serviceContainer) {
        this.repository = repository;
        this.serviceContainer = serviceContainer;
    }


    SynthesisRepository synthesisRepositoru(Application application) {
        return repository.getRepository(application).synthesisRepository();
    }

    @Override
    public int deleteSynthesis(final String nameOrId, final String dataType, final String variable) {
        final Application application = serviceContainer.applicationService().getApplication(nameOrId);
        return repository.getRepository(application).synthesisRepository().removeSynthesisByApplicationDatatypeAndVariable(application.getId(), dataType, variable);
    }

    @Override
    public int deleteSynthesis(final String nameOrId, final String dataType) {
        final Application application = serviceContainer.applicationService().getApplication(nameOrId);
        return repository.getRepository(application).synthesisRepository().removeSynthesisByApplicationDatatype(application.getId(), dataType);
    }

    @Transactional()
    public Map<String, List<OreSiSynthesis>> buildSynthesis(final String nameOrId, final String dataType, final String variable) {
        final Application application = serviceContainer.applicationService().getApplication(nameOrId);
        DataSynthesisRepository repo = repository.getRepository(application).synthesisRepository();
        if (variable == null) {
            repo.removeSynthesisByApplicationDatatype(application.getId(), dataType);
        } else {
            repo.removeSynthesisByApplicationDatatypeAndVariable(application.getId(), dataType, variable);
        }
        final boolean hasChartDescription = application.getConfiguration().dataDescription().get(dataType).componentDescriptions().entrySet().stream()
                .filter(entry -> Strings.isNullOrEmpty(variable) || entry.getKey().equals(variable))
                .anyMatch(entry -> entry.getValue().getChartDescription() != null);
        final String sql;
        if (hasChartDescription) {
            sql = application.getConfiguration().dataDescription().get(dataType).componentDescriptions().entrySet().stream()
                    .filter(entry -> Strings.isNullOrEmpty(variable) || entry.getKey().equals(variable))
                    .filter(entry -> entry.getValue().getChartDescription() != null)
                    .map(entry -> entry.getValue().getChartDescription().toSQL())
                    .collect(Collectors.joining(", \n"));
        } else {
            sql = Chart.toSQL(dataType);
        }
        final List<OreSiSynthesis> oreSiSynthesisList = new LinkedList<>();
        List<OreSiSynthesis> oreSiSynthesis = repo.buildSynthesis(sql, hasChartDescription);
        repo.storeAll(oreSiSynthesis.stream());

        return !hasChartDescription ? Map.of("__NO-CHART", oreSiSynthesis) : oreSiSynthesis.stream().collect(Collectors.groupingBy(OreSiSynthesis::getVariable));
    }

    @Override
    public Map<String, List<OreSiSynthesis>> getSynthesis(final String nameOrId, final String dataType) {
        final Application application = serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(nameOrId);
        if (Optional.of(application.getConfiguration())
                .map(Configuration::dataDescription)
                .map(datatypes -> datatypes.get(dataType))
                .map(StandardDataDescription::tags)
                .map(tags -> tags.stream().noneMatch(tag -> Tag.HiddenTag.instance().equals(tag)))
                .orElse(false)) {
            return repository.getRepository(application).synthesisRepository().selectSynthesisDatatype(application.getId(), dataType).stream()
                    .collect(Collectors.groupingBy(OreSiSynthesis::getVariable));
        }
        return null;
    }

    @Override
    public Map<String, List<OreSiSynthesis>> getSynthesis(final String nameOrId, final String dataName, final String componentName) {
        final Application application = serviceContainer.applicationService().getApplication(nameOrId);
        if (Optional.of(application.getConfiguration())
                .map(Configuration::dataDescription)
                .map(data -> data.get(dataName))
                .map(StandardDataDescription::componentDescriptions)
                .map(data -> data.get(componentName))
                .map(ComponentDescription::tags)
                .map(tags -> tags.stream().noneMatch(tag -> Tag.HiddenTag.instance().equals(tag)))
                .orElse(false)) {
            return repository.getRepository(application).synthesisRepository().selectSynthesisDatatypeAndVariable(application.getId(), dataName, componentName).stream()
                    .collect(Collectors.groupingBy(OreSiSynthesis::getVariable));
        }
        return null;
    }


}
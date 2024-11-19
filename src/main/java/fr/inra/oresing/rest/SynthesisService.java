package fr.inra.oresing.rest;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.domain.repository.synthesis.SynthesisRepository;
import fr.inra.oresing.persistence.ApplicationRepository;
import fr.inra.oresing.persistence.DataSynthesisRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Transactional(readOnly = true)
public class SynthesisService implements fr.inra.oresing.domain.services.synthesis.SynthesisService {

    @Autowired
    ApplicationRepository applicationRepository;
    @Autowired
    OreSiRepository repository;


    @Transactional(readOnly = false)
    public Map<String, List<OreSiSynthesis>> buildSynthesis(String nameOrId, String dataType, String component) {
        Application application = applicationRepository.findApplication(nameOrId);
        final SynthesisRepository synthesisRepository = synthesisRepositoru(application);
        if (component == null) {
            synthesisRepository.removeSynthesisByApplicationDatatype(application.getId(), dataType);
        } else {
            synthesisRepository.removeSynthesisByApplicationDatatypeAndVariable(application.getId(), dataType, component);
        }
        //TODO when defined synthesis section
  /*      boolean hasChartDescription = application.findData(dataType).getData().entrySet().stream()
                .filter(entry -> Strings.isNullOrEmpty(variable) || entry.getKey().equals(variable))
                .anyMatch(entry -> entry.getValue().getChartDescription() != null);
        String sql;
        if (hasChartDescription) {
            sql = application.getConfiguration().getDataTypes().get(dataType).getData().entrySet().stream()
                    .filter(entry -> Strings.isNullOrEmpty(variable) || entry.getKey().equals(variable))
                    .filter(entry -> entry.getValue().getChartDescription() != null)
                    .map(entry -> entry.getValue().getChartDescription().toSQL(entry.getKey(), dataType))
                    .collect(Collectors.joining(", \n"));
        } else {
            sql = Configuration.Chart.toSQL(dataType);
        }
        List<OreSiSynthesis> oreSiSynthesisList = new LinkedList<>();
        final List<OreSiSynthesis> oreSiSynthesis = repo.buildSynthesis(sql, hasChartDescription);
        repo.storeAll(oreSiSynthesis.stream());

        return !hasChartDescription ? Map.of("__NO-CHART", oreSiSynthesis) : oreSiSynthesis.stream().collect(Collectors.groupingBy(OreSiSynthesis::getVariable));
    */
        return null;
    }

    SynthesisRepository synthesisRepositoru(Application application) {
        return repository.getRepository(application).synthesisRepository();
    }


}
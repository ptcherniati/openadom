package fr.inra.oresing.domain.checker;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j

public class CheckerFactory {


  private final DataRepository dataRepository;

  public CheckerFactory(final DataRepository dataRepository) {
    super();
    this.dataRepository = dataRepository;
  }

  public ImmutableSet<LineChecker> getCheckers(final Application application, final String dataName, final PublishContext.PublishContextBuilder publishContextBuilder) {
    SiOreIllegalArgumentException.testExistsData(application, dataName);
    final StandardDataDescription dataDescription = application.getConfiguration().dataDescription().get(dataName);
    final ImmutableSet.Builder<LineChecker> checkers = ImmutableSet.builder();
    for (final Map.Entry<String, ComponentDescription> variableEntry : dataDescription.componentDescriptions().entrySet()) {
      final String column = variableEntry.getKey();
      final ComponentDescription componentDescription = variableEntry.getValue();
      if (componentDescription.checker() != null) {
        checkers.addAll(LineChecker.toLineChecker(
                dataRepository,
                publishContextBuilder,
                componentDescription.transformation(),
                variableEntry.getKey(),
                componentDescription.checker()));
      }
    }
    Map<String, CheckerDescription> validationCheckers = dataDescription.findValidationCheckers();
    validationCheckers.forEach((key, checkerDescription) -> {
        TransformationConfiguration transformation = null;
        if (checkerDescription instanceof TransformationConfiguration tc) {
            transformation = tc;
        }
        checkers.addAll(
                LineChecker.toLineChecker(
                        dataRepository,
                        publishContextBuilder,
                        transformation,
                        key,
                        checkerDescription
                )
        );
    });
    return checkers.build();
  }
}

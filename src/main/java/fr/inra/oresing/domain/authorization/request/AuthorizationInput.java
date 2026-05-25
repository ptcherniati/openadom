package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.domain.repository.authorization.OperationTypeHierarchy;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Getter
@Setter
@ToString(callSuper = true)
public class AuthorizationInput {
    public static final String FROM_DAY = "fromDay";
    public static final String TO_DAY = "toDay";
    public static final String FORMAT = "format";
    LocalDateTimeRange timeScope = LocalDateTimeRange.always();
    Set<OperationType> operationTypes = new HashSet<>();
    private Map<String, List<Ltree>> requiredAuthorizations = new HashMap<>();

    public AuthorizationInput(Map<String, List<Ltree>> requiredAuthorizations,
                              LocalDateTimeRange timeScope,
                              Set<OperationType> operationTypes) {
        this.requiredAuthorizations = requiredAuthorizations;
        this.timeScope = timeScope;
        // Ticket #521 - réponse Damien 2026-05-25 : hiérarchie stricte
        // ( delete > depot/publication > extraction ) + miroir auto
        // depot <-> publication encodés dans OperationTypeHierarchy .
        //
        // TODO ( à supprimer après confirmation Damien ) : ancienne magie
        // "publication implique delete" ( = en pratique , cocher publi
        // ajoutait suppression sans demander ) , retirée car contredit
        // la nouvelle règle "delete doit être coché explicitement" .
        // Code d'origine pour référence :
        //     if (operationTypes.contains(OperationType.publication)) {
        //         operationTypes.add(OperationType.depot);
        //         operationTypes.add(OperationType.delete);
        //     }
        //     if (operationTypes.contains(OperationType.depot) || operationTypes.contains(OperationType.delete)) {
        //         operationTypes.add(OperationType.extraction);
        //     }
        this.operationTypes = OperationTypeHierarchy.normalize(operationTypes);
    }

    public AuthorizationInput() {
    }

    public void setOperationTypes(Set<OperationType> operationTypes) {
        // Ticket #521 : hiérarchie stricte déléguée à OperationTypeHierarchy .
        // Ancien code ( à supprimer après confirmation Damien ) :
        //     if (operationTypes.contains(OperationType.publication)) {
        //         operationTypes.add(OperationType.depot);
        //     }
        //     if (operationTypes.contains(OperationType.depot) || operationTypes.contains(OperationType.delete)) {
        //         operationTypes.add(OperationType.extraction);
        //     }
        this.operationTypes = OperationTypeHierarchy.normalize(operationTypes);
    }

    public void setTimeScope(Map<String, String> dates) {
        this.timeScope = Optional.ofNullable(dates.get(FORMAT))
                .map(DatePattern::of)
                .map(datePattern -> {
                    Class<TemporalAccessor> type = datePattern.type();
                    DateTimeFormatter formatter = datePattern.formatter();
                    if (type.equals(LocalDate.class)) {
                        LocalDate fromDay = Optional.ofNullable(dates.get(FROM_DAY))
                                .map(from -> LocalDate.parse(from, formatter))
                                .orElse(LocalDate.MIN);
                        LocalDate toDay = Optional.ofNullable(dates.get(TO_DAY))
                                .map(from -> LocalDate.parse(from, formatter))
                                .orElse(LocalDate.MAX);
                        return LocalDateTimeRange.between(fromDay, toDay);
                    } else if (type.equals(LocalTime.class)) {
                        LocalTime fromDay = Optional.ofNullable(dates.get(FROM_DAY))
                                .map(from -> LocalTime.parse(from, formatter))
                                .orElse(LocalTime.MIN);
                        LocalTime toDay = Optional.ofNullable(dates.get(TO_DAY))
                                .map(from -> LocalTime.parse(from, formatter))
                                .orElse(LocalTime.MAX);
                        return LocalDateTimeRange.between(LocalDate.now().atTime(fromDay), LocalDate.now().atTime(toDay));
                    } else if (type.equals(LocalDateTime.class)) {
                        LocalDateTime fromDay = Optional.ofNullable(dates.get(FROM_DAY))
                                .map(from -> LocalDateTime.parse(from, formatter))
                                .orElse(LocalDateTime.MIN);
                        LocalDateTime toDay = Optional.ofNullable(dates.get(TO_DAY))
                                .map(from -> LocalDateTime.parse(from, formatter))
                                .orElse(LocalDateTime.MAX);
                        return LocalDateTimeRange.between(fromDay, toDay);
                    }
                    return null;
                })
                .orElse(null);
    }

    public AuthorizationInput withRestrictionWithDependants(String dataName, Function<String, Boolean> isVersionningStrategy) {
        // Ticket #521 - réponse Damien 2026-05-25 : on respecte la
        // hiérarchie stricte ( delete > depot/publication > extraction )
        // au lieu d'exploser tous les droits . Le paramètre
        // {@code isVersionningStrategy} reste dans la signature pour
        // ne pas casser les callers existants ( call-sites multiples ) ,
        // mais n'est plus consulté ici car la magie versionning
        // ( ajout auto de {@code delete} quand {@code depot/publication}
        // coché en mode versionning ) est retirée .
        //
        // TODO ( à supprimer après confirmation Damien ) : ancienne logique
        // d'explosion par operationType , à ré-introduire si la magie
        // versionning s'avère finalement requise pour certains scénarios
        // métier non couverts par le ticket #521 .
        //     getOperationTypes().stream()
        //             .flatMap(operationType -> {
        //                 final Boolean isVersionning = isVersionningStrategy.apply(dataName);
        //                 if(operationType==null){
        //                     return Stream.of();
        //                 }
        //                 if(OperationType.extraction.equals(operationType)) {
        //                     return Stream.of(operationType);
        //                 }
        //                 if(Set.of(OperationType.depot, OperationType.publication).contains(operationType)){
        //                     return isVersionning?
        //                             Stream.of(OperationType.depot, OperationType.publication, OperationType.delete, OperationType.extraction):
        //                             Stream.of(OperationType.depot, OperationType.publication, OperationType.extraction);
        //                 }
        //                 return Stream.of(OperationType.depot, OperationType.publication, OperationType.delete, OperationType.extraction);
        //             })
        //             .collect(Collectors.toSet())
        return new AuthorizationInput(
                getRequiredAuthorizations(),
                getTimeScope(),
                OperationTypeHierarchy.normalize(getOperationTypes())
        );
    }
}
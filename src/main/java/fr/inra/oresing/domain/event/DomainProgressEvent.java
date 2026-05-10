package fr.inra.oresing.domain.event;

/**
 * Événement de progression pur du domaine, émis par {@code RegisterReactiveResult}
 * pour indiquer l'avancement d'un import bundle.
 *
 * <p>La couche REST adapte cet événement en {@code ReactiveTypeProgress} pour
 * l'envoyer au flux Reactor.
 *
 * @param progress valeur entre 0.0 et 1.0 (ex. : {@code 0.55} = 55 %)
 */
public record DomainProgressEvent(double progress) implements ImportProgressEvent {
}
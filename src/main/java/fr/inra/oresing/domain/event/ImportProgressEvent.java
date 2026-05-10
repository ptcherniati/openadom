package fr.inra.oresing.domain.event;

/**
 * Interface marqueur représentant tout événement d'avancement ou de résultat
 * qu'une opération d'import/bundle peut émettre.
 *
 * <p>Le domaine émet des {@code ImportProgressEvent} sans connaître
 * l'infrastructure réactive ({@code ReactiveResult}, Reactor…).
 * La couche REST fait implémenter {@code ReactiveResult} par cette interface
 * et adapte le flux ({@code FluxSink}) via un simple {@code Consumer}.
 *
 * <p><b>Règle</b> : aucune dépendance {@code rest.*}, {@code persistence.*} ou
 * {@code org.springframework.*} dans cette interface.
 */
public interface ImportProgressEvent {
    // Interface marqueur — le domaine émet des événements de ce type
    // sans connaître les détails de l'infrastructure réactive
}
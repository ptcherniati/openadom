package fr.inra.oresing.persistence;

import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.HashMap;
import java.util.List;

/**
 * Représente une donnée correspondant à une valeur de type <code>ltree</code>.
 * <p>
 * Un ltree correspond à une séquence de labels séparés par des points. Les labels sont
 * contraingnants en terme de syntaxe et cette classe gère l'échappement.
 *<a href=" <p>
 * https://www.postgresql.org/docs/current/lt">...</a>ree.html
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class Uniqueness extends HashMap<String, List<String>> {
}
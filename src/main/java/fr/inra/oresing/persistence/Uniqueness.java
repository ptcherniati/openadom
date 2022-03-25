package fr.inra.oresing.persistence;

import lombok.Value;

import java.util.HashMap;
import java.util.List;

/**
 * Représente une donnée correspondant à une valeur de type <code>ltree</code>.
 *
 * Un ltree correspond à une séquence de labels séparés par des points. Les labels sont
 * contraingnants en terme de syntaxe et cette classe gère l'échappement.
 *
 * https://www.postgresql.org/docs/current/ltree.html
 */
@Value
public class Uniqueness extends HashMap<String, List<String>> {
}
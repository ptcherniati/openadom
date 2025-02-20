package fr.inra.oresing.domain.checker;

public interface CheckerTarget {

    String getInternationalizedKey(String key);

    /**
     * @deprecated utilisé dans le front? On devrait plutôt utilisé l'héritage.
     */

    @Deprecated
    String toHumanReadableString();
}

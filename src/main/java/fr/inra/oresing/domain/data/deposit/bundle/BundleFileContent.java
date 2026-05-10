package fr.inra.oresing.domain.data.deposit.bundle;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Représentation domaine d'un fichier CSV de bundle :
 * son nom, son contenu comme flux et les références liées.
 *
 * <p>Classe purement domaine, sans aucune dépendance SQL ou Spring.
 * La logique de génération de requêtes SQL reste dans
 * {@code persistence.data.read.bundle.FileContent}.
 */
public record BundleFileContent(List<String> refsLinked, String fileName, InputStream fileContent) {

    private static final Pattern FORBIDDEN_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>| ]");

    /**
     * Nettoie une chaîne pour qu'elle puisse être utilisée comme nom de fichier.
     *
     * @param pattern la chaîne à nettoyer
     * @return la chaîne avec les caractères interdits remplacés par {@code -}
     */
    public static String sanitizePatternForFilename(String pattern) {
        return FORBIDDEN_FILENAME_CHARS.matcher(pattern).replaceAll("-");
    }
}
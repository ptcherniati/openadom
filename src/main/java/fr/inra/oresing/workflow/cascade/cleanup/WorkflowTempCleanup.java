package fr.inra.oresing.workflow.cascade.cleanup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Nettoie les fichiers et repertoires temporaires d'un workflow d'import
 * (chunks bruts, chunks traités, fichier mergé). Remplace l'ancien
 * {@code WorkflowChunkCleanupService} du JAR file-processor.
 */
@Slf4j
@Component
public class WorkflowTempCleanup {

    /**
     * Supprime recursivement les chemins fournis. Les exceptions sont
     * loguees mais pas propagees : le cleanup ne doit jamais masquer
     * l'erreur metier qui l'a declenche.
     */
    public void cleanup(Path... paths) {
        for (Path path : paths) {
            if (path == null) {
                continue;
            }
            deleteRecursively(path);
        }
    }

    private void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(this::deleteSilently);
        } catch (IOException e) {
            log.warn("Echec du nettoyage de {} : {}", root, e.getMessage());
        }
    }

    private void deleteSilently(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("Impossible de supprimer {} : {}", p, e.getMessage());
        }
    }
}

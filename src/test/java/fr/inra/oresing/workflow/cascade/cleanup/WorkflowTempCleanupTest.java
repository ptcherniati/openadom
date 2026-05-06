package fr.inra.oresing.workflow.cascade.cleanup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires de WorkflowTempCleanup.
 * N'utilise pas Spring : instanciation directe via new.
 */
@DisplayName("WorkflowTempCleanup")
@Tag("domain.model")
class WorkflowTempCleanupTest {

    private final WorkflowTempCleanup cleanup = new WorkflowTempCleanup();

    @Test
    @DisplayName("cleanup(null) ne lève pas d'exception")
    void nullPathIsIgnored() {
        assertThatCode(() -> cleanup.cleanup((Path) null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cleanup sur un chemin inexistant est sans effet")
    void nonExistentPathIsIgnored(@TempDir Path tmp) {
        Path ghost = tmp.resolve("does-not-exist");
        assertThatCode(() -> cleanup.cleanup(ghost)).doesNotThrowAnyException();
        // toujours absent
        assertThat(ghost).doesNotExist();
    }

    @Test
    @DisplayName("cleanup supprime un fichier régulier")
    void deletesRegularFile(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("file.txt");
        Files.writeString(file, "content");
        assertThat(file).exists();

        cleanup.cleanup(file);

        assertThat(file).doesNotExist();
    }

    @Test
    @DisplayName("cleanup supprime récursivement un répertoire avec sous-fichiers")
    void deletesDirectoryRecursively(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("mydir");
        Path sub = dir.resolve("subdir");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("a.csv"), "a");
        Files.writeString(dir.resolve("b.csv"), "b");

        assertThat(dir).exists();
        cleanup.cleanup(dir);
        assertThat(dir).doesNotExist();
    }

    @Test
    @DisplayName("cleanup accepte plusieurs chemins dont certains null")
    void multiplePathsSomeNull(@TempDir Path tmp) throws IOException {
        Path f1 = tmp.resolve("f1.txt");
        Path f2 = tmp.resolve("f2.txt");
        Files.writeString(f1, "x");
        Files.writeString(f2, "y");

        cleanup.cleanup(f1, null, f2);

        assertThat(f1).doesNotExist();
        assertThat(f2).doesNotExist();
    }
}
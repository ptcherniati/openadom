package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import fr.inra.oresing.domain.data.DataValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link HierarchicalReferenceAsTree} et {@link ZipUtils}.
 */
@Tag("domain.model")
@DisplayName("HierarchicalReferenceAsTree et ZipUtils — utilitaires REST")
class HierarchicalReferenceAsTreeAndZipUtilsTest {

    // ─── HierarchicalReferenceAsTree ─────────────────────────────────────────

    private DataValue dv(String refType) {
        DataValue d = new DataValue();
        d.setReferenceType(refType);
        return d;
    }

    @Test
    @DisplayName("getChildren() retourne les enfants directs")
    void getChildrenReturnsDirectChildren() {
        DataValue root = dv("root");
        DataValue childA = dv("A");
        DataValue childB = dv("B");

        ImmutableSetMultimap<DataValue, DataValue> tree = ImmutableSetMultimap.<DataValue, DataValue>builder()
                .put(root, childA)
                .put(root, childB)
                .build();

        HierarchicalReferenceAsTree hierarchicalRef = new HierarchicalReferenceAsTree(
                tree, ImmutableSet.of(root));

        assertThat(hierarchicalRef.getChildren(root)).contains(childA, childB);
        assertThat(hierarchicalRef.roots()).contains(root);
    }

    @Test
    @DisplayName("getChildren() sur une feuille retourne vide")
    void getChildrenLeafEmpty() {
        DataValue leaf = dv("leaf");
        HierarchicalReferenceAsTree hierarchicalRef = new HierarchicalReferenceAsTree(
                ImmutableSetMultimap.of(), ImmutableSet.of(leaf));

        assertThat(hierarchicalRef.getChildren(leaf)).isEmpty();
    }

    @Test
    @DisplayName("record fields accessibles")
    void recordFieldsAccessible() {
        ImmutableSetMultimap<DataValue, DataValue> tree = ImmutableSetMultimap.of();
        ImmutableSet<DataValue> roots = ImmutableSet.of();
        HierarchicalReferenceAsTree ref = new HierarchicalReferenceAsTree(tree, roots);
        assertThat(ref.tree()).isSameAs(tree);
        assertThat(ref.roots()).isSameAs(roots);
    }

    // ─── ZipUtils ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("zipDirectory() crée un fichier ZIP contenant les fichiers du répertoire")
    void zipDirectory(@TempDir Path tempDir) throws IOException {
        Path sourceDir = tempDir.resolve("src");
        Files.createDirectory(sourceDir);
        Files.writeString(sourceDir.resolve("file1.txt"), "hello");
        Files.writeString(sourceDir.resolve("file2.csv"), "a,b,c");
        Path subDir = sourceDir.resolve("sub");
        Files.createDirectory(subDir);
        Files.writeString(subDir.resolve("nested.txt"), "nested content");

        Path zipFile = tempDir.resolve("output.zip");
        ZipUtils.zipDirectory(sourceDir, zipFile);

        assertThat(zipFile).exists();
        assertThat(Files.size(zipFile)).isPositive();

        // Vérifie que les 3 fichiers sont dans le ZIP
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                count++;
                assertThat(entry.getName()).doesNotContain("\\"); // slash Unix
                zis.closeEntry();
            }
        }
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("zipDirectory() répertoire vide crée un ZIP vide")
    void zipDirectoryEmpty(@TempDir Path tempDir) throws IOException {
        Path sourceDir = tempDir.resolve("empty");
        Files.createDirectory(sourceDir);
        Path zipFile = tempDir.resolve("empty.zip");

        ZipUtils.zipDirectory(sourceDir, zipFile);

        assertThat(zipFile).exists();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            assertThat(zis.getNextEntry()).isNull();
        }
    }
}

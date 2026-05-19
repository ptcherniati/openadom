package fr.inra.oresing.persistence.normalized;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.normalized.ReferenceJoin;
import fr.inra.oresing.domain.application.normalized.Sql;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des builders SQL du package persistence.normalized.
 * Aucun contexte Spring ni base de données.
 */
@Tag("domain.model")
@DisplayName("Persistence normalized SQL builders")
class PersistenceNormalizedBuildersTest {

    // ─── BuildIndexes ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BuildIndexes")
    class BuildIndexesTest {

        @Test
        @DisplayName("buildIndexes() joint les index par retour à la ligne")
        void joinsByNewline() {
            BuildIndexes b = new BuildIndexes(List.of("CREATE INDEX idx1;", "CREATE INDEX idx2;"));
            String result = b.buildIndexes();
            assertThat(result)
                    .contains("CREATE INDEX idx1;")
                    .contains("CREATE INDEX idx2;")
                    .contains("\n");
        }

        @Test
        @DisplayName("buildIndexes() sur liste vide retourne une chaîne vide")
        void emptyList() {
            BuildIndexes b = new BuildIndexes(List.of());
            assertThat(b.buildIndexes()).isEmpty();
        }

        @Test
        @DisplayName("buildIndexes() un seul index sans séparateur superflu")
        void singleIndex() {
            BuildIndexes b = new BuildIndexes(List.of("CREATE INDEX only;"));
            assertThat(b.buildIndexes()).isEqualTo("CREATE INDEX only;");
        }

        @Test
        @DisplayName("record equality")
        void equality() {
            BuildIndexes a = new BuildIndexes(List.of("idx1"));
            BuildIndexes b = new BuildIndexes(List.of("idx1"));
            assertThat(a).isEqualTo(b);
        }
    }

    // ─── SelectBuilder ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SelectBuilder")
    class SelectBuilderTest {

        @Test
        @DisplayName("buildSelects() filtre les nulls et joint par virgule+newline")
        void filtersNullsAndJoins() {
            List<String> withNull = new java.util.ArrayList<>();
            withNull.add("col1");
            withNull.add(null);
            withNull.add("col2");
            SelectBuilder sb = new SelectBuilder(withNull);
            String result = sb.buildSelects();
            assertThat(result).contains("col1")
                    .contains("col2")
                    .doesNotContain("null");
        }

        @Test
        @DisplayName("buildSelects() sur liste vide retourne une chaîne vide")
        void emptyList() {
            SelectBuilder sb = new SelectBuilder(List.of());
            assertThat(sb.buildSelects()).isEmpty();
        }

        @Test
        @DisplayName("buildSelects() avec un seul élément")
        void singleElement() {
            SelectBuilder sb = new SelectBuilder(List.of("id"));
            String result = sb.buildSelects();
            assertThat(result.trim()).isEqualTo("id");
        }

        @Test
        @DisplayName("record equality")
        void equality() {
            SelectBuilder a = new SelectBuilder(List.of("x", "y"));
            SelectBuilder b = new SelectBuilder(List.of("x", "y"));
            assertThat(a).isEqualTo(b);
        }
    }

    // ─── ForeignKeysBuilder ───────────────────────────────────────────────────

    @Nested
    @DisplayName("ForeignKeysBuilder")
    class ForeignKeysBuilderTest {

        @Test
        @DisplayName("buildForeignKeys() génère un ALTER TABLE pour chaque clé étrangère")
        void generateAlterTable() {
            ForeignKeysBuilder fk = new ForeignKeysBuilder(
                    "myschema", "mytable",
                    Map.of("reftype1", List.of("reftype1_id")));
            String result = fk.buildForeignKeys();
            assertThat(result).contains("ALTER TABLE")
                    .contains("myschema_dn.mytable")
                    .contains("reftype1__reftype1_id_fk")
                    .contains("FOREIGN KEY")
                    .contains("REFERENCES");
        }

        @Test
        @DisplayName("buildForeignKeys() sur map vide retourne une chaîne vide")
        void emptyMap() {
            ForeignKeysBuilder fk = new ForeignKeysBuilder("s", "t", Map.of());
            assertThat(fk.buildForeignKeys()).isEmpty();
        }

        @Test
        @DisplayName("buildForeignKeys() plusieurs colonnes pour un même reftype")
        void multipleColumns() {
            ForeignKeysBuilder fk = new ForeignKeysBuilder(
                    "s", "t",
                    Map.of("ref", List.of("col1", "col2")));
            String result = fk.buildForeignKeys();
            assertThat(result).contains("col1")
                    .contains("col2");
        }

        @Test
        @DisplayName("record equality")
        void equality() {
            ForeignKeysBuilder a = new ForeignKeysBuilder("s", "t", Map.of());
            ForeignKeysBuilder b = new ForeignKeysBuilder("s", "t", Map.of());
            assertThat(a).isEqualTo(b);
        }
    }

    // ─── ManyToManyBuilder ────────────────────────────────────────────────────

    @Nested
    @DisplayName("ManyToManyBuilder")
    class ManyToManyBuilderTest {

        @Test
        @DisplayName("buildManyToMany() génère un CREATE TABLE pour la table de jonction")
        void generateCreateTable() {
            ManyToManyBuilder m = new ManyToManyBuilder(
                    "myschema", "mytable",
                    Map.of("otherref", List.of("col1")));
            String result = m.buildManyToMany();
            assertThat(result).contains("CREATE TABLE")
                    .contains("myschema_dn.mytable_otherref")
                    .contains("mytable_id")
                    .contains("otherref_id")
                    .contains("INSERT INTO");
        }

        @Test
        @DisplayName("buildManyToMany() auto-join (tableName == foreignTable) ajoute suffixe _parent")
        void selfJoinSuffix() {
            ManyToManyBuilder m = new ManyToManyBuilder(
                    "s", "t",
                    Map.of("t", List.of("t_id")));
            String result = m.buildManyToMany();
            assertThat(result).contains("_parent");
        }

        @Test
        @DisplayName("buildManyToMany() sur map vide retourne une chaîne vide")
        void emptyMap() {
            ManyToManyBuilder m = new ManyToManyBuilder("s", "t", Map.of());
            assertThat(m.buildManyToMany()).isEmpty();
        }
    }

    // ─── PoliciesBuilder ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("PoliciesBuilder")
    class PoliciesBuilderTest {

        @Test
        @DisplayName("buildPolicies() sans timescope ni authorizationScope utilise USING(true)")
        void noTimescapeNoScope() {
            PoliciesBuilder pb = new PoliciesBuilder("s", "t", List.of(), Map.of());
            String result = pb.buildPolicies();
            assertThat(result).contains("ALTER TABLE IF EXISTS s_dn.t")
                    .contains("ENABLE ROW LEVEL SECURITY")
                    .contains("true");
        }

        @Test
        @DisplayName("buildPolicies() avec timescope ajoute la colonne timescope dans le SELECT")
        void withTimescope() {
            PoliciesBuilder pb = new PoliciesBuilder("s", "t", List.of("ts_column"), Map.of());
            String result = pb.buildPolicies();
            assertThat(result).contains("timescope");
        }

        @Test
        @DisplayName("buildPolicies() avec authorizationScope ajoute la colonne scope dans le SELECT")
        void withAuthorizationScope() {
            PoliciesBuilder pb = new PoliciesBuilder("s", "t", List.of(), Map.of("reftype1", "col1"));
            String result = pb.buildPolicies();
            assertThat(result).contains("reftype1")
                    .contains("col1_hk");
        }

        @Test
        @DisplayName("buildPolicies() timescope + scope combine les deux conditions")
        void withBoth() {
            PoliciesBuilder pb = new PoliciesBuilder(
                    "s", "t", List.of("ts"), Map.of("ref", "col"));
            String result = pb.buildPolicies();
            assertThat(result).contains("timescope")
                    .contains("ref")
                    .contains("AND");
        }

        @Test
        @DisplayName("record accessors retournent les bonnes valeurs")
        void recordAccessors() {
            List<String> ts = List.of("ts");
            Map<String, String> scopes = Map.of("k", "v");
            PoliciesBuilder pb = new PoliciesBuilder("sch", "tbl", ts, scopes);
            assertThat(pb.schemaName()).isEqualTo("sch");
            assertThat(pb.tableName()).isEqualTo("tbl");
            assertThat(pb.timescopes()).isEqualTo(ts);
            assertThat(pb.authorizationScopes()).isEqualTo(scopes);
        }
    }

    // ─── SchemaBuilder ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SchemaBuilder")
    class SchemaBuilderTest {

        private Application makeApp(String name) {
            Application app = new Application();
            app.setName(name);
            return app;
        }

        private Sql makeSql(String schema, String table) {
            return new Sql(schema, table, UUID.randomUUID());
        }

        @Test
        @DisplayName("sortSqlsByForeignKeyDependency() ordonne une liste sans dépendances")
        void sortNoDependencies() {
            Sql a = makeSql("s", "a");
            Sql b = makeSql("s", "b");
            List<Sql> sorted = SchemaBuilder.sortSqlsByForeignKeyDependency(List.of(a, b));
            assertThat(sorted).hasSize(2);
            assertThat(sorted.stream().map(Sql::tableName).toList())
                    .containsExactlyInAnyOrder("a", "b");
        }

        @Test
        @DisplayName("sortSqlsByForeignKeyDependency() ordonne une liste avec dépendance simple")
        void sortWithSimpleDependency() {
            Sql parent = makeSql("s", "parent");
            Sql child = new Sql("s", "child", UUID.randomUUID(),
                    List.of(), List.of(), List.of(), List.of(),
                    Map.of("parent", List.of("parent_id")),
                    List.of(), Map.of(), Map.of());
            List<Sql> sorted = SchemaBuilder.sortSqlsByForeignKeyDependency(List.of(child, parent));
            List<String> names = sorted.stream().map(Sql::tableName).filter(Objects::nonNull).toList();
            assertThat(names.indexOf("parent")).isLessThan(names.indexOf("child"));
        }

        @Test
        @DisplayName("sortSqlsByForeignKeyDependency() liste vide retourne liste vide")
        void sortEmpty() {
            List<Sql> sorted = SchemaBuilder.sortSqlsByForeignKeyDependency(List.of());
            assertThat(sorted).isEmpty();
        }

        @Test
        @DisplayName("buildSchema() contient DROP et CREATE SCHEMA")
        void buildSchemaContainsDdl() {
            Sql sql = makeSql("myapp", "ref1");
            Application app = makeApp("myapp");
            SchemaBuilder sb = new SchemaBuilder(List.of(sql), app);
            String schema = sb.buildSchema();
            assertThat(schema).contains("drop schema if exists myapp_dn cascade")
                    .contains("create schema myapp_dn");
        }

        @Test
        @DisplayName("record accessors retournent les bonnes valeurs après construction")
        void recordAccessors() {
            Sql sql = makeSql("s", "t");
            Application app = makeApp("myapp");
            SchemaBuilder sb = new SchemaBuilder(List.of(sql), app);
            assertThat(sb.application()).isSameAs(app);
            assertThat(sb.buildedSqls()).isNotNull();
        }
    }

    // ─── TableBuilder ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TableBuilder")
    class TableBuilderTest {

        @Test
        @DisplayName("createTable() génère un CREATE TABLE avec les noms attendus")
        void createTable() {
            Sql sql = new Sql("myapp", "myref", UUID.randomUUID(),
                    List.of("col1", "col2"),
                    List.of(),
                    List.of(new ReferenceJoin("joinTable", "joinCol")),
                    List.of("CREATE INDEX idx;"),
                    Map.of(),
                    List.of(),
                    Map.of(),
                    Map.of());
            TableBuilder tb = new TableBuilder(sql);
            String result = tb.createTable();
            assertThat(result).contains("create table")
                    .contains("myapp_dn.myref")
                    .contains("OWNER TO");
        }

        @Test
        @DisplayName("createTable() inclut les colonnes SELECT fournies")
        void createTableIncludesSelects() {
            Sql sql = new Sql("s", "t", UUID.randomUUID(),
                    List.of("colA", "colB"),
                    List.of(),
                    List.of(),
                    List.of(),
                    Map.of(),
                    List.of(),
                    Map.of(),
                    Map.of());
            String result = new TableBuilder(sql).createTable();
            assertThat(result).contains("colA")
                    .contains("colB");
        }

        @Test
        @DisplayName("record accessor sql() retourne l'objet SQL")
        void recordAccessor() {
            Sql sql = new Sql("s", "t", UUID.randomUUID());
            TableBuilder tb = new TableBuilder(sql);
            assertThat(tb.sql()).isSameAs(sql);
        }
    }

    // ─── FromBuilder ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FromBuilder")
    class FromBuilderTest {

        @Test
        @DisplayName("buildFrom() contient la table referencevalue du schéma")
        void buildFromContainsReferencevalue() {
            FromBuilder fb = new FromBuilder("myapp", List.of(), List.of());
            String result = fb.buildFrom();
            assertThat(result).contains("myapp.referencevalue");
        }

        @Test
        @DisplayName("buildFrom() avec refValuesTable non vide génère JSON_TABLE")
        void buildFromWithRefValues() {
            FromBuilder fb = new FromBuilder("s", List.of("extra"), List.of());
            String result = fb.buildFrom();
            assertThat(result).contains("JSON_TABLE");
        }

        @Test
        @DisplayName("buildFrom() avec referenceJoin génère le JSON_TABLE des refs")
        void buildFromWithReferenceJoin() {
            FromBuilder fb = new FromBuilder("s", List.of(),
                    List.of(new ReferenceJoin("joinTable", "joinCol")));
            String result = fb.buildFrom();
            assertThat(result).contains("JSON_TABLE")
                    .contains("refslinkedto")
                    .contains("joinTable");
        }

        @Test
        @DisplayName("record accessors")
        void recordAccessors() {
            List<String> refs = List.of("r");
            List<ReferenceJoin> joins = List.of(new ReferenceJoin("t", "c"));
            FromBuilder fb = new FromBuilder("sch", refs, joins);
            assertThat(fb.schemaName()).isEqualTo("sch");
            assertThat(fb.refValuesTable()).isEqualTo(refs);
            assertThat(fb.referenceJoin()).isEqualTo(joins);
        }
    }
}
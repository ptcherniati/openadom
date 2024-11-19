package fr.inra.oresing.persistence;

import org.flywaydb.core.internal.database.base.Schema;

public enum OreSiSqlSchema implements SqlSchema {

    /**
     * Le schéma dans lequel on stocke le cœur
     */
    MAIN;

    public static SqlTable application() {
        return new SqlTable(MAIN, "application");
    }

    public static SqlTable binaryFile(SqlSchema schema) {
        return new SqlTable(schema, "binaryfile");
    }

    public static SqlTable referencevalue(SqlSchema schema) {
        return new SqlTable(schema, "referencevalue");
    }

    public static SqlTable authorization(SqlSchema schema) {
        return new SqlTable(schema, "oresiauthorization");
    }

    public static SqlTable oreSiUser() {
        return new SqlTable(MAIN, "oreSiUser");
    }

    @Override
    public String getName() {
        return "public";
    }
}
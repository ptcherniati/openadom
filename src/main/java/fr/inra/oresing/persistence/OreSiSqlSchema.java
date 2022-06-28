package fr.inra.oresing.persistence;

public enum OreSiSqlSchema implements SqlSchema {

    /**
     * Le schéma dans lequel on stocke le cœur
     */
    MAIN;

    public SqlTable application() {
        return new SqlTable(MAIN, "application");
    }

    public SqlTable bvinaryFile() {
        return new SqlTable(MAIN, "application");
    }

    public SqlTable oreSiUser() {
        return new SqlTable(MAIN, "oreSiUser");
    }

    @Override
    public String getName() {
        return "public";
    }
}
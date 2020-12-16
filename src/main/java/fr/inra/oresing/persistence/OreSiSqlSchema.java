package fr.inra.oresing.persistence;

public enum OreSiSqlSchema implements SqlSchema {

    /**
     * Le schéma dans lequel on stocke le cœur
     */
    MAIN;

    public SqlTable application() {
        return new SqlTable(MAIN, "application");
    }

    @Override
    public String getSqlIdentifier() {
        return "public";
    }
}

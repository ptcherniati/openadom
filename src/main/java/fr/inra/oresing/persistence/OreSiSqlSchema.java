package fr.inra.oresing.persistence;

public enum OreSiSqlSchema implements SqlSchema {

    /**
     * Le schéma dans lequel on stocke le cœur
     */
    MAIN;

    public SqlTable data() {
        return new SqlTable(MAIN, "data");
    }

    public SqlTable referenceValue() {
        return new SqlTable(MAIN, "referenceValue");
    }

    @Override
    public String getSqlIdentifier() {
        return "public";
    }
}

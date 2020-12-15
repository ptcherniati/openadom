package fr.inra.oresing.persistence;

public enum OreSiSqlSchema implements SqlSchema {

    /**
     * Le schéma dans lequel on stocke le cœur
     */
    MAIN;

    @Deprecated
    public SqlTable data() {
        return new SqlTable(MAIN, "data");
    }

    @Deprecated
    public SqlTable referenceValue() {
        return new SqlTable(MAIN, "referenceValue");
    }

    public SqlTable application() {
        return new SqlTable(MAIN, "application");
    }

    @Deprecated
    public SqlTable binaryFile() {
        return new SqlTable(MAIN, "binaryFile");
    }

    @Override
    public String getSqlIdentifier() {
        return "public";
    }
}

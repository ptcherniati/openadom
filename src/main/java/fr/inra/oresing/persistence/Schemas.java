package fr.inra.oresing.persistence;

/**
 * Constantes des schemas Postgres utilises par openadom .
 *
 * <p>Centraliser ici evite les hardcodes "oa_audit" / "oa_staging" disperses
 * dans les SQL strings et facilite un eventuel renaming futur ( 1 seul
 * endroit a changer ) .
 *
 * <ul>
 *   <li>{@link #BUSINESS} ( public ) : tables metier durables</li>
 *   <li>{@link #AUDIT} : logs append-only + compensation_log queue</li>
 *   <li>{@link #STAGING} : tables techniques import ephemeres</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
public final class Schemas {

    /** Schema metier ( tables durables , transactionnelles ) . */
    public static final String BUSINESS = "public";

    /** Schema observability + compensation ( workflow_log , user_session_log , compensation_log ) . */
    public static final String AUDIT = "oa_audit";

    /** Schema tables techniques import transitoires ( referencevalue_import_shared ) . */
    public static final String STAGING = "oa_staging";

    private Schemas() { /* constants holder */ }
}

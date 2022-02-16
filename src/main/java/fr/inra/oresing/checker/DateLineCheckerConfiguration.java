package fr.inra.oresing.checker;

/**
 * Configuration pour un checker de type "Date"
 */
public interface DateLineCheckerConfiguration extends LineCheckerConfiguration {

    /**
     * Le format dans lequel doit être la la date qui sera validée (par exemple, "dd/MM/yyyy")
     */
    String getPattern();

    /**
     * La {@link fr.inra.oresing.model.Duration} pour cette donnée.
     */
    String getDuration();
}

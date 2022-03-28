package fr.inra.oresing.checker;

import io.swagger.annotations.ApiModelProperty;

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
    @ApiModelProperty(notes = "the duration of the data value. Use sql pattern duration.", required = true, example = "1 MONTHS")
    String getDuration();
}
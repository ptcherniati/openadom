package fr.inra.oresing.model.internationalization;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Getter
@Setter
@ToString
public class InternationalizationImpl {
    @ApiModelProperty(notes = "How to translate this name in differents locales",required = false)
    Internationalization internationalizationName;
    @ApiModelProperty(notes = "Some columns used as key and the reference to translation in other columns",required = false)
    Map<String, Internationalization> internationalizedColumns;
}
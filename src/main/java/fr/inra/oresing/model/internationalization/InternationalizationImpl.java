package fr.inra.oresing.model.internationalization;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Getter
@Setter
@ToString
public class InternationalizationImpl {
    Internationalization internationalizationName;
    Map<String, Internationalization> internationalizedColumns;
}
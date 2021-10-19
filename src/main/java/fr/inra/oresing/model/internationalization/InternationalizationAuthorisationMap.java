package fr.inra.oresing.model.internationalization;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class InternationalizationAuthorisationMap {
    Map<String, InternationalizationAuthorisationName> dataGroups;
}
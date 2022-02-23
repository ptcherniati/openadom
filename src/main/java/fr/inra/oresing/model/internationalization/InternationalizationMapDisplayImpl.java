package fr.inra.oresing.model.internationalization;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Getter
@Setter
@ToString
public class InternationalizationMapDisplayImpl extends InternationalizationImpl{
   Map<String, InternationalizationDisplay> internationalizationDisplays;
}
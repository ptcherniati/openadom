package fr.inra.oresing.model.internationalization;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.annotation.Nullable;
import java.util.Map;

@Getter
@Setter
@ToString
public class InternationalizationMap {
    @Nullable
    InternationalizationApplicationMap application;
    @Nullable
    Map<String, InternationalizationReferenceMap> references;
    @Nullable
    Map<String, InternationalizationDataTypeMap> dataTypes;

}
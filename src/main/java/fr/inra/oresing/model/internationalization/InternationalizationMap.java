package fr.inra.oresing.model.internationalization;

import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty(notes = "The internationalization description from section Application", required = false)
    InternationalizationApplicationMap application;
    @Nullable
    @ApiModelProperty(notes = "The internationalization description from section references", required = false)
    Map<String, InternationalizationReferenceMap> references;
    @Nullable
    @ApiModelProperty(notes = "The internationalization description from section dataTypes", required = false)
    Map<String, InternationalizationDataTypeMap> dataTypes;

}
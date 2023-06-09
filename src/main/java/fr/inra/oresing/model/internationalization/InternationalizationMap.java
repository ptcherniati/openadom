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
   @Nullable
    @ApiModelProperty(notes = "The internationalization for tags.\n" +
            "Labels can be used in the document to identify groups and enable filters or groupings.", required = false)
    Map<String, Internationalization> internationalizedTags;

}
package fr.inra.oresing.rest;

import lombok.Value;

import javax.annotation.Nullable;
import java.util.Set;

@Value
public class DownloadDatasetQuery {

    String applicationNameOrId;

    String dataType;

    @Nullable
    Set<String> variableComponentIds;
}

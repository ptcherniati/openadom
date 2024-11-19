package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.rest.OreSiResources;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import org.springframework.web.util.UriUtils;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DataVersioningResult(UUID dataId, List<ApplicationResult.DataSynthesis> dataSynthesis, String uri) {

    public DataVersioningResult {
        Objects.requireNonNull(dataId);
        dataSynthesis= dataSynthesis== null?List.of():List.copyOf(dataSynthesis);
    }

    public static DataVersioningResult of(String nameOrId, String dataName, UUID dataId, List<ApplicationResult.DataSynthesis> dataSynthesis) {
        final String uri = UriUtils.encodePath(String.format(OreSiResources.DATA_SERVICE_PATH_PATTERN, nameOrId, dataName), Charset.defaultCharset());
        return new DataVersioningResult(dataId,dataSynthesis,uri);
    }
}

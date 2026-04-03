package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.rest.filesenderclient.BuildBundleReport;
import fr.inra.oresing.rest.data.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class WriteUploadBundleUseCase {

    private final DataService dataService;

    public BuildBundleReport execute(String instanceUrl, String nameOrId, boolean withData, Locale locale, Path tempZipDirectory) throws IOException {
        return dataService.writeUploadBundle(instanceUrl, nameOrId, withData, locale, tempZipDirectory);
    }
}

package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.rest.data.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class ReadEntryUseCase {

    private final DataService dataService;

    public void execute(File zipBundleFile, String entryName, Consumer<InputStream> consumer) throws IOException {
        dataService.readEntry(zipBundleFile, entryName, consumer);
    }
}

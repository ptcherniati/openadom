package fr.inra.oresing.rest;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.persistence.OreSiRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static fr.inra.oresing.OreSiUtils.toJson;

@Component
public class OreSiService {

    @Autowired
    private OreSiRepository repo;

    @Transactional
    protected UUID storeFile(MultipartFile file) throws IOException {
        // creation du fichier
        BinaryFile binaryFile = new BinaryFile();
        binaryFile.setName(file.getOriginalFilename());
        binaryFile.setSize(file.getSize());
        binaryFile.setData(file.getBytes());
        UUID result = repo.store(binaryFile);
        return result;
    }

    @Transactional
    public UUID createApplication(String name, MultipartFile configurationFile) throws IOException {
        Application app = new Application();
        app.setName(name);
        UUID result = repo.store(app);

        changeApplicationConfiguration(app, configurationFile);

        return result;
    }

    @Transactional
    public UUID changeApplicationConfiguration(Application app, MultipartFile configurationFile) throws IOException {
        // on essaie de parser le fichier, si tout ce passe bien, on remplace ou ajoute le fichier

        UUID confId = storeFile(configurationFile);
        // on supprime l'ancien fichier vu que tout c'est bien passé
        repo.deleteBinaryFile(app.getConfigFile());

        app.setConfigFile(confId);
        repo.store(app);

        Configuration conf = Configuration.read(configurationFile.getBytes());
        app.setReferenceType(new ArrayList<>(conf.getReferences().keySet()));
        app.setDataType(new ArrayList<>(conf.getDataset().keySet()));

        app.setConfiguration(conf);

        repo.store(app);

        return confId;
    }

    @Transactional
    public UUID addReference(Application app, String refType, MultipartFile file) throws IOException {
        UUID fileId = storeFile(file);

        Configuration conf = app.getConfiguration();
        Configuration.ReferenceDescription ref = conf.getReferences().get(refType);

        CsvSchema.Builder schemaBuilder = CsvSchema.builder();
        ref.getColumns().entrySet().forEach(e -> {
            schemaBuilder.addColumn(e.getKey());
        });
        CsvSchema schema = schemaBuilder.setColumnSeparator(ref.getSeparator()).build();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(file.getBytes())))) {
            for (int i=0 ; i < ref.getLineToSkip(); i++) {
                reader.readLine();
            }

            CsvMapper mapper = new CsvMapper();
            mapper.readerFor(Map.class).with(schema).readValues(reader).forEachRemaining(line -> {
                ReferenceValue e = new ReferenceValue();
                e.setBinaryFile(fileId);
                e.setReferenceType(refType);
                e.setApplication(app.getId());
                e.setRefValues((Map<String, String>) line);
                repo.store(e);
            });
        }

        return fileId;
    }
}

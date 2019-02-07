package fr.inra.oresing.rest;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import fr.inra.oresing.checker.Checker;
import fr.inra.oresing.checker.CheckerException;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.ReferenceChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.persistence.OreSiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OreSiService {

    @Autowired
    private OreSiRepository repo;

    @Autowired
    private CheckerFactory checkerFactory;

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

    private Checker getChecker(Configuration.ColumnDescription desc, Application app) {
        if (desc == null || desc.getChecker() == null) {
            return checkerFactory.getChecker("Dummy");
        }
        return checkerFactory.getChecker(desc.getChecker(), app.getId());
    }

    @Transactional
    public UUID addData(Application app, String dataType, MultipartFile file) throws IOException, CheckerException {
        UUID fileId = storeFile(file);

        Configuration conf = app.getConfiguration();
        Configuration.DatasetDescription dataSet = conf.getDataset().get(dataType);

        Map<String, Checker> checkers = new HashMap<>();
        for (Map.Entry<String, Configuration.ColumnDescription> e : dataSet.getReferences().entrySet()) {
            checkers.put(e.getKey(), getChecker(e.getValue(), app));
        }

        for (Map.Entry<String, Configuration.DataDescription> e : dataSet.getData().entrySet()) {
            checkers.put(e.getKey(), getChecker(e.getValue(), app));
            if (e.getValue() != null)
            for (Map.Entry<String, Configuration.ColumnDescription> a : e.getValue().getAccuracy().entrySet()) {
                checkers.put(a.getKey(), getChecker(a.getValue(), app));
            }
        }

        CsvSchema.Builder schemaBuilder = CsvSchema.builder();
        schemaBuilder.setColumnSeparator(dataSet.getSeparator());
        schemaBuilder.setUseHeader(true);
        schemaBuilder.setReorderColumns(true);

        checkers.keySet().forEach(schemaBuilder::addColumn);
        CsvSchema schema = schemaBuilder.build();

        List<String> error = new LinkedList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(file.getBytes())))) {
            for (int i=0 ; i < dataSet.getLineToSkip(); i++) {
                reader.readLine();
            }

            CsvMapper mapper = new CsvMapper();
            mapper.readerFor(Map.class).with(schema).readValues(reader).forEachRemaining(line -> {
                Map<String, String> values = (Map<String, String>) line;
                List<UUID> refsLinkedTo = new ArrayList<>();
                values.forEach((k, v) -> {
                    try {
                        Checker checker = checkers.get(k);
                        if (checker == null) {
                            throw new CheckerException(String.format("Unknown column: '%s'", k));
                        }
                        Object result = checker.check(v);
                        if (checker instanceof ReferenceChecker) {
                            refsLinkedTo.add((UUID)result);
                        }
                    } catch (CheckerException eee) {
                        log.debug("Validation problem", eee);
                        error.add(eee.getMessage());
                    }
                });

                Data e = new Data();
                e.setBinaryFile(fileId);
                e.setDataType(dataType);
                e.setApplication(app.getId());
                e.setRefsLinkedTo(refsLinkedTo);
                e.setDataValues(values);
                repo.store(e);
            });
        }

        if (!error.isEmpty()) {
            throw new CheckerException("Parsing error:\n" + String.join("\n\t", error));
        }

        return fileId;
    }

}

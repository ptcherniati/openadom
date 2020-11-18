package fr.inra.oresing.rest;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.checker.Checker;
import fr.inra.oresing.checker.CheckerException;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.ReferenceChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ApplicationRight;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.persistence.AuthRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.roles.OreSiRoleToAccessDatabase;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
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
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OreSiService {

    @Autowired
    private OreSiRepository repo;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private CheckerFactory checkerFactory;

    @Transactional
    protected UUID storeFile(Application app, MultipartFile file) throws IOException {
        authRepository.setRole(OreSiApiRequestContext.get().getRequestClient().getRole());
        // creation du fichier
        BinaryFile binaryFile = new BinaryFile();
        binaryFile.setApplication(app.getId());
        binaryFile.setName(file.getOriginalFilename());
        binaryFile.setSize(file.getSize());
        binaryFile.setData(file.getBytes());
        UUID result = repo.store(binaryFile);
        return result;
    }

    @Transactional
    public UUID createApplication(String name, MultipartFile configurationFile) throws IOException {
        try {
            OreSiRequestClient requestClient = OreSiApiRequestContext.get().getRequestClient();
            OreSiRoleToAccessDatabase userRole = requestClient.getRole();

            authRepository.setRole(userRole);
            Application app = new Application();
            app.setName(name);
            UUID result = repo.store(app);

            // on repasse admin pour la creation des roles associes a la nouvelle application
            authRepository.resetRole();
            authRepository.createRightForApplication(app);

            // on met l'utilisateur courant dans dans le group admin de cette application
            authRepository.addUserRight(requestClient.getId(), app.getId(), ApplicationRight.ADMIN);

            // on enregistre le fichier sous l'identite de l'utilisateur
            authRepository.setRole(userRole);
            changeApplicationConfiguration(app, configurationFile);

            return result;
        } catch (BadSqlGrammarException eee) {
            if (StringUtils.contains(eee.getMessage(), "permission denied")) {
                throw new SecurityException(eee);
            }
            throw eee;
        }
    }

    @Transactional
    public UUID changeApplicationConfiguration(Application app, MultipartFile configurationFile) throws IOException {
        authRepository.setRole(OreSiApiRequestContext.get().getRequestClient().getRole());
        // on essaie de parser le fichier, si tout ce passe bien, on remplace ou ajoute le fichier

        UUID oldConfigId = app.getConfigFile();
        UUID confId = storeFile(app, configurationFile);

        app.setConfigFile(confId);
        repo.store(app);

        // on supprime l'ancien fichier vu que tout c'est bien passé
        repo.deleteBinaryFile(oldConfigId);

        Configuration conf = Configuration.read(configurationFile.getBytes());
        app.setReferenceType(new ArrayList<>(conf.getReferences().keySet()));
        app.setDataType(new ArrayList<>(conf.getDataset().keySet()));

        app.setConfiguration(conf);

        repo.store(app);

        return confId;
    }

    @Transactional
    public UUID addReference(Application app, String refType, MultipartFile file) throws IOException {
        authRepository.setRole(OreSiApiRequestContext.get().getRequestClient().getRole());
        UUID fileId = storeFile(app, file);

        Configuration conf = app.getConfiguration();
        Configuration.ReferenceDescription ref = conf.getReferences().get(refType);

        CsvSchema.Builder schemaBuilder = CsvSchema.builder();
        ref.getColumns().entrySet().forEach(e -> {
            schemaBuilder.addColumn(e.getKey());
        });
        CsvSchema schema = schemaBuilder.setColumnSeparator(ref.getSeparator()).build();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(file.getBytes())))) {
            for (int i = 0; i < ref.getLineToSkip(); i++) {
                reader.readLine();
            }

            CsvMapper mapper = new CsvMapper();
            List<ReferenceValue> refs = mapper.readerFor(Map.class).with(schema).readValues(reader).readAll().stream()
                    .map(line -> {
                        ReferenceValue e = new ReferenceValue();
                        e.setBinaryFile(fileId);
                        e.setReferenceType(refType);
                        e.setApplication(app.getId());
                        e.setRefValues((Map<String, String>) line);
                        return e;
                    }).collect(Collectors.toList());

            refs.forEach(repo::store);
        }

        return fileId;
    }

    @Transactional
    public UUID addData(Application app, String dataType, MultipartFile file) throws IOException, CheckerException {
        authRepository.setRole(OreSiApiRequestContext.get().getRequestClient().getRole());
        UUID fileId = storeFile(app, file);

        // recuperation de la configuration pour ce type de donnees
        Configuration conf = app.getConfiguration();
        Configuration.DatasetDescription dataSet = conf.getDataset().get(dataType);

        // ajout des contraintes sur les champs de type referenciel
        Map<String, Checker> checkers = new HashMap<>();
        for (Map.Entry<String, Configuration.ColumnDescription> e : dataSet.getReferences().entrySet()) {
            checkers.put(e.getKey(), checkerFactory.getChecker(e.getValue(), app));
        }

        // ajout des contraintes sur les champs de data
        for (Map.Entry<String, Configuration.DataDescription> e : dataSet.getData().entrySet()) {
            checkers.put(e.getKey(), checkerFactory.getChecker(e.getValue(), app));
            if (e.getValue() != null) {
                // ajout de contraintes sur les champs de precisions
                for (Map.Entry<String, Configuration.ColumnDescription> a : e.getValue().getAccuracy().entrySet()) {
                    checkers.put(a.getKey(), checkerFactory.getChecker(a.getValue(), app));
                }
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
            for (int i = 0; i < dataSet.getLineToSkip(); i++) {
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
                            refsLinkedTo.add((UUID) result);
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

    @Transactional
    public List<Map<String, String>> findData(Application app, String dataType, MultiValueMap<String, String> params) {
        authRepository.setRole(OreSiApiRequestContext.get().getRequestClient().getRole());
        // recuperation de la configuration pour ce type de donnees
        Configuration conf = app.getConfiguration();
        Configuration.DatasetDescription dataSet = conf.getDataset().get(dataType);

        // ajout des contraintes sur les champs de type referenciel
        Map<String, Checker> checkers = new HashMap<>();

        for (Map.Entry<String, Configuration.ColumnDescription> e : dataSet.getReferences().entrySet()) {
            Checker checker = checkerFactory.getChecker(e.getValue(), app);
            if (checker instanceof ReferenceChecker) {
                checkers.put(e.getKey(), checker);
            }
        }

        List<UUID>[] nuppletRefs = params.entrySet().stream()
                .map(e -> e.getValue().stream().map(value -> getRefid(checkers, e.getKey(), value))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
                .toArray(List[]::new);

        List<Data> data = repo.findData(app.getId(), dataType, nuppletRefs);
        List<Map<String, String>> result = data.stream().map(Data::getDataValues).collect(Collectors.toList());

        return result;
    }

    protected UUID getRefid(Map<String, Checker> checkers, String refType, String value) {
        try {
            try{
                return UUID.fromString(value);
            } catch (IllegalArgumentException eee) {
                Checker checker = checkers.get(refType);
                if (checker == null) {
                    throw new IllegalArgumentException(refType + " has no reference table");
                }
                return checker.check(value);
            }
        } catch (CheckerException eee) {
            throw new IllegalArgumentException(eee);
        }
    }
}
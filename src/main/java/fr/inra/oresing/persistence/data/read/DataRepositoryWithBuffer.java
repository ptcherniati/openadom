package fr.inra.oresing.persistence.data.read;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.Node;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.repository.data.DataRepository;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record DataRepositoryWithBuffer(
        Application application,
        DataRepository repository,
        Path tempDir
)
        implements fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer {

    public static final String PREFIX_FOR_HIERARCHICAL = "hierarchical";
    public static final String PREFIX_FOR_DISPLAY = "display";

    public DataRepositoryWithBuffer(Application application, DataRepository dataRepository) {
        this(application, dataRepository, createTempDir());
    }

    private static Path createTempDir() {
        try {
            String uniqueId = UUID.randomUUID().toString();
            return Files.createTempDirectory("data_repo_buffer_" + uniqueId);
        } catch (IOException e) {
            throw new OreSiTechnicalException("Impossible de créer le répertoire temporaire", e);
        }
    }

    @Override
    public Map<String, Map<String, String>> findDisplayByReferenceType(String referenceType) {
        return getDataFromFileOrRepository(fileWithPrefix(referenceType, PREFIX_FOR_DISPLAY),
                stream -> stream
                        .filter(parts->parts.length>3)
                        .collect(Collectors.groupingBy(
                        parts -> parts[1],
                        Collectors.toMap(parts -> parts[2], parts -> parts[3])
                ))
        );
    }

    @Override
    public Map<String, String> findDisplayByReferenceTypeAndNaturalKey(String referenceType, String naturalKey) {
        return getDataFromFileOrRepository(fileWithPrefix(referenceType, PREFIX_FOR_DISPLAY),
                stream -> stream.filter(parts -> parts[1].equals(naturalKey))
                        .collect(Collectors.toMap(parts -> parts[2], parts -> parts[3]))
        );
    }

    @Override
    public String findDisplayByReferenceTypeAndNaturalKeyAndLocale(String referenceType, String naturalKey, String locale) {
        return getDataFromFileOrRepository(fileWithPrefix(referenceType, PREFIX_FOR_DISPLAY),
                stream -> stream.filter(parts -> parts[1].equals(naturalKey) && parts[2].equals(locale))
                        .map(parts -> parts[3])
                        .findFirst()
                        .orElse(null)
        );
    }

    public List<Ltree> getHierarchicalKeyForEntry(Map.Entry<String, List<Ltree>> scopeEntry) {
        String referenceType = scopeEntry.getKey();
        List<String> availableKeys = new ArrayList<>();

        // Collecter toutes les clés disponibles

        return scopeEntry.getValue().stream()
                .map(keyForScope -> {
                    String hierarchicalKey = getDataFromFileOrRepository(
                            fileWithPrefix(referenceType, PREFIX_FOR_HIERARCHICAL),
                            stream -> stream
                                    .map(parts -> {
                                        availableKeys.add(parts[1]);
                                        return parts;
                                    }) // Collecter toutes les clés disponibles
                                    .filter(parts -> parts[1].equals(keyForScope.toString()) || parts[2].equals(keyForScope.toString()))
                                    .map(parts -> parts[2])
                                    .findFirst()
                                    .orElse(null)
                    );

                    if (hierarchicalKey == null) {
                        String availableKeysMessage = String.join(", ", availableKeys);
                        throw new IllegalArgumentException(//TODO throw sioretechnicalException
                                String.format("Clé non trouvée pour le type de référence: %s, clé: %s. Clés disponibles: %s",
                                        referenceType, keyForScope, availableKeysMessage)
                        );
                    }

                    return Ltree.fromSql(hierarchicalKey);
                })
                .toList();
    }

    @Override
    public Map<String, List<Ltree>> checkHierarchicalKey(Map<String, List<Ltree>> requiredAuthorizations) throws SiOreIllegalArgumentException {
        return requiredAuthorizations.entrySet().stream()
                .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                this::getHierarchicalKeyForEntry
                        )
                );
    }

    private <T> T getDataFromFileOrRepository(String filePrefix, Function<Stream<String[]>, T> streamProcessor) {
        Path filePath = tempDir.resolve(filePrefix + ".tsv");
        if (Files.exists(filePath)) {
            return loadFromDisk(filePath, streamProcessor);
        }

        if (filePrefix.endsWith(PREFIX_FOR_DISPLAY)) {
            Map<String, Map<String, String>> data = repository.findDisplayByNaturalKey(filePrefix.replace("_" + PREFIX_FOR_DISPLAY, ""));
            saveToDisk(filePrefix, data, this::dataToStreamDisplay);
            return streamProcessor.apply(validateAndProcessStream(dataToStreamDisplay(data), 4));
        } else if (filePrefix.endsWith(PREFIX_FOR_HIERARCHICAL)) {
            String dataName = filePrefix.replace("_" + PREFIX_FOR_HIERARCHICAL, "");
            List<String> parents = new LinkedList<>();
            parents.add(dataName);
            String parentName = application().findParentNode(dataName).map(Node::nodeName).orElse(null);
            while(parentName!=null){
                parents.add(parentName);
                parentName = application().findParentNode(parentName).map(Node::nodeName).orElse(null);
            }


            Map<String, String> data = repository.findHierarchicalKeysByKeyForReferenceTypes(parents);
            saveToDisk(filePrefix, data, this::dataToStreamHierarchical);
            return streamProcessor.apply(validateAndProcessStream(dataToStreamHierarchical(data), 3));
        }

        throw new IllegalArgumentException("Type de données non reconnu : " + filePrefix);
    }

    private Stream<String[]> validateAndProcessStream(Stream<String[]> stream, int minLength) {
        return stream.map(parts -> {
            if (parts.length < minLength) {
                throw new IllegalArgumentException("Format de ligne invalide : " + String.join("\t", parts));
            }
            return parts;
        });
    }


    private <T> T loadFromDisk(Path filePath, Function<Stream<String[]>, T> streamProcessor) {
        try (Stream<String> lines = Files.lines(filePath)) {
            return streamProcessor.apply(lines.map(line -> line.split("\t")));
        } catch (IOException e) {
            throw new UncheckedIOException("Erreur lors de la lecture du fichier : " + filePath, e);
        }
    }

    private Stream<String[]> dataToStreamDisplay(Map<String, Map<String, String>> data) {
        AtomicInteger index = new AtomicInteger(0);
        return data.entrySet().stream()
                .flatMap(entry -> entry.getValue().entrySet().stream()
                        .map(localeEntry -> new String[]{
                                String.valueOf(index.getAndIncrement()),
                                entry.getKey(),
                                localeEntry.getKey(),
                                localeEntry.getValue()
                        }));
    }

    private Stream<String[]> dataToStreamHierarchical(Map<String, String> data) {
        AtomicInteger index = new AtomicInteger(0);
        return data.entrySet().stream()
                .flatMap(entry -> Stream.of(
                        new String[]{String.valueOf(index.getAndIncrement()), entry.getKey(), entry.getValue()},
                        new String[]{String.valueOf(index.getAndIncrement()), entry.getValue(), entry.getValue()}
                ));
    }

    private <T> void saveToDisk(String filePrefix, T data, Function<T, Stream<String[]>> dataToStreamFunction) {
        Path filePath = tempDir.resolve(filePrefix + ".tsv");
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            dataToStreamFunction.apply(data).forEach(parts -> {
                try {
                    writer.write(String.join("\t", parts));
                    writer.newLine();
                } catch (IOException e) {
                    throw new UncheckedIOException("Erreur lors de l'écriture des données", e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Erreur lors de la sauvegarde des données dans : " + filePath, e);
        }
    }

    private String fileWithPrefix(String referenceType, String prefix) {
        return "%s_%s".formatted(referenceType, prefix);
    }

    public void cleanup() {
        try(Stream<Path> pathStream = Files.walk(tempDir)) {
            pathStream
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new UncheckedIOException("Erreur lors du nettoyage du répertoire temporaire", e);
        }
    }

    @Override
    public Stream<DataValue> findAllByReferenceTypeStream(String referenceName) {
        return repository().findAllByReferenceTypeStream(referenceName);
    }
}
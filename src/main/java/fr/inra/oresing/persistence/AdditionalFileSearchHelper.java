package fr.inra.oresing.persistence;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.AdditionalFileDescription;
import fr.inra.oresing.domain.application.configuration.FieldDescription;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import lombok.Getter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AdditionalFileSearchHelper {
    AdditionalFilesInfos additionalFilesInfos;
    Application application;
    private final AtomicInteger i = new AtomicInteger();
    @Getter
    private MapSqlParameterSource paramSource = new MapSqlParameterSource();

    public AdditionalFileSearchHelper(final Application application, final AdditionalFilesInfos additionalFilesInfos) {
        super();
        this.application = application;
        this.additionalFilesInfos = additionalFilesInfos;
        paramSource = new MapSqlParameterSource("applicationId", application.getId());
    }

    public AdditionalFileSearchHelper() {
    }

    private String addArgumentAndReturnSubstitution(final Object value) {
        final int i = this.i.incrementAndGet();
        final String paramName = String.format("arg%d", i);
        paramSource.addValue(paramName, value);
        return String.format(":%s", paramName);
    }

    String filterBy() {
        final List<String> where = new LinkedList<>();
        Optional.ofNullable(additionalFilesInfos.getUuids())
                .filter(uuids -> !CollectionUtils.isEmpty(uuids))
                .ifPresent(list -> where.add(list.stream()
                        .map(this::addArgumentAndReturnSubstitution)
                        .collect(Collectors.joining(",", " (\nid in (", ")\n) "))
                ));
        Optional.ofNullable(additionalFilesInfos.getFileNames())
                .filter(fileNames -> !CollectionUtils.isEmpty(fileNames))
                .ifPresent(list -> where.add(list.stream()
                        .map(this::addArgumentAndReturnSubstitution)
                        .collect(Collectors.joining(",", " (\nfilename in (", ")\n) "))
                ));
        Optional.ofNullable(additionalFilesInfos.getAuthorizations())
                .filter(authorizations -> !CollectionUtils.isEmpty(authorizations))
                .ifPresent(list -> where.add(list.stream()
                        .map(this::addArgumentAndReturnSubstitution)
                        .collect(Collectors.joining(",", " (\nassociate @> ARRAY[", "]\n) "))
                ));
        Optional.ofNullable(additionalFilesInfos.getAdditionalFilesInfos())
                .filter(additionalFileInfos -> !CollectionUtils.isEmpty(additionalFileInfos))
                .ifPresent(list -> where.add(list.entrySet().stream()
                        .map(this::whereForAdditionalFileName)
                        .collect(Collectors.joining(" or ", "(", ")"))
                ));

        String byFileType = Optional.ofNullable(additionalFilesInfos.getFiletype())
                .map(this::addArgumentAndReturnSubstitution)
                .map(substitution -> String.format(" AND filetype = %s", substitution))
                .orElse("");
        return CollectionUtils.isEmpty(where) ? "" : where.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" or ", "(", ")" + byFileType));
    }

    private String whereForAdditionalFileName(final Map.Entry<String, ? extends AdditionalFilesInfos.AdditionalFileInfos> entry) {
        final String additionalFileName = entry.getKey();
        AdditionalFilesInfos.AdditionalFileInfos additionalFileInfos = entry.getValue();
        Set<AdditionalFilesInfos.FieldFilters> fieldFilters = additionalFileInfos.getFieldFilters();
        AdditionalFileDescription additionalFileDescription = application.getConfiguration().additionalFiles().get(additionalFileName);
        final List<String> where = new LinkedList<>();
        where.add("fileType=" + addArgumentAndReturnSubstitution(additionalFileName));
        if (!CollectionUtils.isEmpty(fieldFilters)) {
            Optional.of(fieldFilters)
                    .map(filters -> filters.stream()
                            .map(filter -> whereForField(filter, additionalFileDescription.formFields().get(filter.field)))
                            .collect(Collectors.joining(" and ", "(", ")")))
                    .ifPresent(where::add);
        }
        return where.stream()
                        .filter(Objects::nonNull).collect(Collectors
                                .joining(" and ", "(", ")"));
    }

    private String whereForField(final AdditionalFilesInfos.FieldFilters filter, final FieldDescription additionalFileFieldFormat) {
        final boolean isRegExp = filter.isRegExp != null && filter.isRegExp;
        final List<String> filters = new LinkedList<>();
        if (!Strings.isNullOrEmpty(filter.filter)) {
            filters.add(String.format(
                            "fileinfos #> '{\"%s\"}'  @@ ('$ like_regex \"'||%s||'\"')::jsonpath",
                            JsonTableInApplicationSchemaRepositoryTemplate.escapeSql(filter.getField()),
                            /*String.formFields(isRegExp ? "~ %s" : "ilike '%%'||%s||'%%'", */
                            addArgumentAndReturnSubstitution(filter.getFilter())//)
                    )
            );

        } else if (filter.intervalValues != null && List.of("date", "time", "datetime").contains(filter.type)) {
            if (!Strings.isNullOrEmpty(filter.intervalValues.from) || !Strings.isNullOrEmpty(filter.intervalValues.to)) {
                filters.add(
                        String.format(
                                "fileinfos #> '{\"%1$s\"}'@@ ('$ >= \"date:'||%2$s||'\" && $ <= \"date:'||%3$s||'Z\"')::jsonpath",
                                JsonTableInApplicationSchemaRepositoryTemplate.escapeSql(filter.getField()),
                                addArgumentAndReturnSubstitution(Strings.isNullOrEmpty(filter.intervalValues.from) ? "0" : filter.intervalValues.from),
                                addArgumentAndReturnSubstitution(Strings.isNullOrEmpty(filter.intervalValues.to) ? "9" : filter.intervalValues.to)
                        )
                );
            }
        } else if (filter.intervalValues != null && "numeric".equals(filter.type)) {
            if (!Strings.isNullOrEmpty(filter.intervalValues.from) || !Strings.isNullOrEmpty(filter.intervalValues.to)) {
                //fileinfos #> '{"t","value"}'@@ '$. double() >= 1 && $. double() <= 2'
                final List<String> filterList = new LinkedList<>();
                if (!Strings.isNullOrEmpty(filter.intervalValues.from)) {
                    filterList.add(String.format(
                                    "$. double() >= '||%s||'",
                                    addArgumentAndReturnSubstitution(filter.intervalValues.from)
                            )
                    );
                }
                if (!Strings.isNullOrEmpty(filter.intervalValues.to)) {
                    filterList.add(String.format(
                                    "$. double() <= '||%s||'",
                                    addArgumentAndReturnSubstitution(filter.intervalValues.to)
                            )
                    );
                }
                filters.add(
                        String.format("fileinfos #> '{\"%s\"}'@@ ('%s')::jsonpath",
                                JsonTableInApplicationSchemaRepositoryTemplate.escapeSql(filter.getField()),
                                String.join(" && ", filterList)
                        )
                );
            }
        }
        if (CollectionUtils.isEmpty(filters)) {
            return "";
        }
        return filters.stream()
                .filter(f -> !Strings.isNullOrEmpty(f))
                .collect(Collectors.joining(" AND ", "(", ")"));
    }

    public String buildWhereRequest() {
        return additionalFilesInfos == null ? null : filterBy();

    }

    public void addAdditionalFilesToZip(final AdditionalBinaryFile additionalBinaryFile, final ZipOutputStream zipOutputStream, String folder) throws IOException {
        String folder1 = folder;
        if (folder1 == null) {
            folder1 = "";
        }
        final List<MemoryFile> memoryFiles = getMemoriesFiles(additionalBinaryFile);
        for (final MemoryFile memoryFile : memoryFiles) {
            if (memoryFile.contents.length == 0) {
                continue;
            }
            final ZipEntry zipEntry = new ZipEntry(String.format("%s%s", folder1, memoryFile.fileName));
            zipOutputStream.putNextEntry(zipEntry);
            zipOutputStream.write(memoryFile.contents);
            zipOutputStream.flush();
            zipOutputStream.closeEntry();
        }
    }

    private static List<MemoryFile> getMemoriesFiles(final AdditionalBinaryFile additionalBinaryFile) {
        final List<MemoryFile> memoryFiles = new LinkedList<>();
        memoryFiles.add(formatFileInfos(additionalBinaryFile));
        memoryFiles.add(fileToMemoryFile(additionalBinaryFile));
        return memoryFiles;
    }

    private static MemoryFile fileToMemoryFile(final AdditionalBinaryFile additionalBinaryFile) {
        return new MemoryFile(additionalBinaryFile.getFileType(), additionalBinaryFile.getFileName(), additionalBinaryFile.getFileName(), additionalBinaryFile.getData());
    }

    private static MemoryFile formatFileInfos(final AdditionalBinaryFile additionalBinaryFile) {
        return new MemoryFile(additionalBinaryFile.getFileType(),
                additionalBinaryFile.getFileName(),
                additionalBinaryFile.getFileName().replaceAll("\\.[^\\.]*", "") + "_infos.txt",
                Optional.ofNullable(additionalBinaryFile.getFileInfos()).orElse(new HashMap<>()).entrySet().stream()
                        .map(e -> String.format("%s : %s", e.getKey(), e.getValue()))
                        .collect(Collectors.joining("\n"))
                        .getBytes(StandardCharsets.UTF_8));
    }

    public static class MemoryFile {
        public final String fileName;
        public final byte[] contents;

        public MemoryFile(final String fileTypeName, final String fileNameForFolder, final String fileName, final byte[] contents) {
            super();
            this.fileName = buildPath(fileTypeName, fileNameForFolder, fileName);
            this.contents = contents;
        }

        public static String buildPath(final String fileTypeName, String fileNameForFolder, final String fileName) {
            String fileNameForFolder1 = fileNameForFolder.replaceAll("\\.[^\\.]*", "");
            return String.format("%s/%s/%s", fileTypeName, fileNameForFolder1, fileName);
        }
    }
}
package fr.inra.oresing.persistence;

import com.google.common.base.Strings;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.model.additionalfiles.AdditionalFilesInfos;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.util.CollectionUtils;
import org.testcontainers.shaded.org.apache.commons.lang.StringEscapeUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AdditionalFileSearchHelper {
    AdditionalFilesInfos additionalFilesInfos;
    Application application;
    private AtomicInteger i = new AtomicInteger();

    public MapSqlParameterSource getParamSource() {
        return paramSource;
    }

    private MapSqlParameterSource paramSource = new MapSqlParameterSource();

    private String addArgumentAndReturnSubstitution(Object value) {
        int i = this.i.incrementAndGet();
        String paramName = String.format("arg%d", i);
        paramSource.addValue(paramName, value);
        return String.format(":%s", paramName);
    }

    public AdditionalFileSearchHelper(Application application, AdditionalFilesInfos additionalFilesInfos) {
        this.application = application;
        this.additionalFilesInfos = additionalFilesInfos;
        this.paramSource = new MapSqlParameterSource("applicationId", application.getId());
    }

    public AdditionalFileSearchHelper() {
        super();
    }

    String filterBy() {
        List<String> where = new LinkedList<>();
        Optional.ofNullable(this.additionalFilesInfos.getUuids())
                .filter(uuids -> !CollectionUtils.isEmpty(uuids))
                .ifPresent(list -> {
                    where.add(list.stream()
                            .map(this::addArgumentAndReturnSubstitution)
                            .collect(Collectors.joining(",", " (\nid in (", ")\n) "))
                    );
                });
        Optional.ofNullable(this.additionalFilesInfos.getFileNames())
                .filter(fileNames -> !CollectionUtils.isEmpty(fileNames))
                .ifPresent(list -> {
                    where.add(list.stream()
                            .map(this::addArgumentAndReturnSubstitution)
                            .collect(Collectors.joining(",", " (\nfilename in (", ")\n) "))
                    );
                });
        Optional.ofNullable(this.additionalFilesInfos.getAuthorizations())
                .filter(authorizations -> !CollectionUtils.isEmpty(authorizations))
                .ifPresent(list -> {
                    where.add(list.stream()
                            .map(this::addArgumentAndReturnSubstitution)
                            .collect(Collectors.joining(",", " (\nassociate @> ARRAY[", "]\n) "))
                    );
                });
        Optional.ofNullable(this.additionalFilesInfos.getAdditionalFilesInfos())
                .filter(additionalFileInfos -> !CollectionUtils.isEmpty(additionalFileInfos))
                .ifPresent(list -> {
                    where.add(list.entrySet().stream()
                            .map(this::whereForAdditionalFileName)
                            .collect(Collectors.joining(" or ", "(", ")"))
                    );
                });

        final String byFileType = Optional.ofNullable(additionalFilesInfos.getFiletype())
                .map(this::addArgumentAndReturnSubstitution)
                .map(substitution -> String.format(" AND filetype = %s", substitution))
                .orElse("");
        return CollectionUtils.isEmpty(where) ? "" : where.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" or ", "(", ")" + byFileType));
    }

    private String whereForAdditionalFileName(Map.Entry<String, AdditionalFilesInfos.AdditionalFileInfos> entry) {
        String additionalFileName = entry.getKey();
        final AdditionalFilesInfos.AdditionalFileInfos additionalFileInfos = entry.getValue();
        final Set<AdditionalFilesInfos.FieldFilters> fieldFilters = additionalFileInfos.getFieldFilters();
        final Configuration.AdditionalFileDescription additionalFileDescription = application.getConfiguration().getAdditionalFiles().get(additionalFileName);
        List<String> where = new LinkedList<>();
        where.add("fileType=" + addArgumentAndReturnSubstitution(additionalFileName));
        if (!CollectionUtils.isEmpty(fieldFilters)) {
            Optional.ofNullable(fieldFilters)
                    .map(filters -> filters.stream()
                            .map(filter -> whereForField(filter, additionalFileDescription.getFormat().get(filter.field)))
                            .collect(Collectors.joining(" and ", "(", ")")))
                    .ifPresent(whereElement -> where.add(whereElement));
        }
        return CollectionUtils.isEmpty(where) ? "" : where.stream()
                .filter(Objects::nonNull).collect(Collectors
                        .joining(" and ", "(", ")"));
    }

    private String whereForField(AdditionalFilesInfos.FieldFilters filter, Configuration.AdditionalFileFieldFormat additionalFileFieldFormat) {
        boolean isRegExp = filter.isRegExp != null && filter.isRegExp;
        List<String> filters = new LinkedList<>();
        if (!Strings.isNullOrEmpty(filter.filter)) {
            filters.add(String.format(
                            "fileinfos #> '{\"%s\"}'  @@ ('$ like_regex \"'||%s||'\"')::jsonpath",
                            StringEscapeUtils.escapeSql(filter.getField()),
                            /*String.format(isRegExp ? "~ %s" : "ilike '%%'||%s||'%%'", */
                            addArgumentAndReturnSubstitution(filter.getFilter())//)
                    )
            );

        } else if (filter.intervalValues != null && List.of("date", "time", "datetime").contains(filter.type)) {
            if (!Strings.isNullOrEmpty(filter.intervalValues.from) || !Strings.isNullOrEmpty(filter.intervalValues.to)) {
                filters.add(
                        String.format(
                                "fileinfos #> '{\"%1$s\"}'@@ ('$ >= \"date:'||%2$s||'\" && $ <= \"date:'||%2$s||'Z\"')::jsonpath",
                                StringEscapeUtils.escapeSql(filter.getField()),
                                addArgumentAndReturnSubstitution(Strings.isNullOrEmpty(filter.intervalValues.from) ? "0" : filter.intervalValues.from),
                                addArgumentAndReturnSubstitution(Strings.isNullOrEmpty(filter.intervalValues.to) ? "9" : filter.intervalValues.to)
                        )
                );
            }
        } else if (filter.intervalValues != null && "numeric".equals(filter.type)) {
            if (!Strings.isNullOrEmpty(filter.intervalValues.from) || !Strings.isNullOrEmpty(filter.intervalValues.to)) {
                //fileinfos #> '{"t","value"}'@@ '$. double() >= 1 && $. double() <= 2'
                List<String> filterList = new LinkedList<>();
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
                if (!CollectionUtils.isEmpty(filterList)) {
                    filters.add(
                            String.format("fileinfos #> '{\"%s\"}'@@ ('%s')::jsonpath",
                                    StringEscapeUtils.escapeSql(filter.getField()),
                                    filterList.stream().collect(Collectors.joining(" && "))
                            )
                    );
                }
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

    public byte[] zip(List<AdditionalBinaryFile> additionalBinaryFiles) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            addAdditionalFilesToZip(additionalBinaryFiles, zipOutputStream, "");
        }
        return byteArrayOutputStream.toByteArray();
    }

    public void addAdditionalFilesToZip(List<AdditionalBinaryFile> additionalBinaryFiles, ZipOutputStream zipOutputStream, String folder) throws IOException {
        if(folder==null){
            folder="";
        }
        List<MemoryFile> memoryFiles = additionalBinaryFiles.stream()
                .map(this::getMemoriesFiles)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        for (MemoryFile memoryFile : memoryFiles) {
            ZipEntry zipEntry = new ZipEntry(String.format("%s%s",folder, memoryFile.fileName));
            zipOutputStream.putNextEntry(zipEntry);
            zipOutputStream.write(memoryFile.contents);
            zipOutputStream.closeEntry();
        }
    }

    private List<MemoryFile> getMemoriesFiles(AdditionalBinaryFile additionalBinaryFile) {
        List<MemoryFile> memoryFiles = new LinkedList<>();
        memoryFiles.add(formatFileInfos(additionalBinaryFile));
        memoryFiles.add(fileToMemoryFile(additionalBinaryFile));
        return memoryFiles;
    }

    private MemoryFile fileToMemoryFile(AdditionalBinaryFile additionalBinaryFile) {
        return new MemoryFile(additionalBinaryFile.getFileType(), additionalBinaryFile.getFileName(), additionalBinaryFile.getFileName(), additionalBinaryFile.getData());
    }

    private MemoryFile formatFileInfos(AdditionalBinaryFile additionalBinaryFile) {
        try {
            return new MemoryFile(additionalBinaryFile.getFileType(),
                    additionalBinaryFile.getFileName(),
                    additionalBinaryFile.getFileName().replaceAll("\\.[^\\.]*", "") + "_infos.txt",
                    additionalBinaryFile.getFileInfos().entrySet().stream()
                            .map(e -> String.format("%s : %s", e.getKey(), e.getValue()))
                            .collect(Collectors.joining("\n"))
                            .getBytes("UTF8"));
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    public static class MemoryFile {
        public String fileName;
        public byte[] contents;

        public String buildPath(String fileTypeName, String fileNameForFolder, String fileName) {
            fileNameForFolder = fileNameForFolder.replaceAll("\\.[^\\.]*", "");
            return String.format("%s/%s/%s", fileTypeName, fileNameForFolder, fileName);
        }

        public MemoryFile(String fileTypeName, String fileNameForFolder, String fileName, byte[] contents) {
            this.fileName = buildPath(fileTypeName, fileNameForFolder, fileName);
            this.contents = contents;
        }
    }
}
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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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

    String filterBy() {
        List<String> where = new LinkedList<>();
        where.add(Optional.ofNullable(this.additionalFilesInfos.getUuids())
                .filter(uuids -> !CollectionUtils.isEmpty(uuids))
                .orElseGet(LinkedHashSet::new)
                .stream()
                .map(this::addArgumentAndReturnSubstitution)
                .collect(Collectors.joining(",", " (id in (", ")) "))
        );
        where.add(Optional.ofNullable(this.additionalFilesInfos.getAdditionalFilesInfos())
                .filter(additionalFileInfos -> !CollectionUtils.isEmpty(additionalFileInfos))
                .orElseGet(LinkedHashMap::new).entrySet().stream()
                .map(this::whereForAdditionalFileName)
                .collect(Collectors.joining(" or ", "(",")"))
        );
        return where.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" or ","(",")"));
    }

    private String whereForAdditionalFileName(Map.Entry<String, AdditionalFilesInfos.AdditionalFileInfos> entry) {
        String additionalFileName = entry.getKey();
        final AdditionalFilesInfos.AdditionalFileInfos additionalFileInfos = entry.getValue();
        final Set<AdditionalFilesInfos.FieldFilters> fieldFilters = additionalFileInfos.getFieldFilters();
        final Configuration.AdditionalFileDescription additionalFileDescription = application.getConfiguration().getAdditionalFiles().get(additionalFileName);
        List<String> where = new LinkedList<>();
        where.add("fileType=" + addArgumentAndReturnSubstitution(additionalFileName));
        where.add(Optional.ofNullable(fieldFilters)
                .map(filters -> filters.stream()
                        .map(filter -> whereForField(filter, additionalFileDescription.getFormat().get(filter.field)))
                        .collect(Collectors.joining(" and ", "(", ")")))
                .orElse(null)
        );
        return where.stream()
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
                filters.add(
                        String.format("fileinfos #> '{\"%s\"}'@@ ('%s')::jsonpath",
                                StringEscapeUtils.escapeSql(filter.getField()),
                                filterList.stream().collect(Collectors.joining(" && "))
                        )
                );
            }
        }
        return filters.stream()
                .filter(f -> !Strings.isNullOrEmpty(f))
                .collect(Collectors.joining(" AND ", "(", ")"));
    }

    public String buildRequest(String sqlStart, String sqlEnd) {
        return String.join( "\n ",
                "where ",
                String.join(" or ", filterBy()),
                sqlEnd
        );

    }

    public byte[] zip(List<AdditionalBinaryFile> additionalBinaryFiles)throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try(ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            /*for (MemoryFile memoryFile : memoryFiles) {
                ZipEntry zipEntry = new ZipEntry(memoryFile.fileName);
                zipOutputStream.putNextEntry(zipEntry);
                zipOutputStream.write(memoryFile.contents);
                zipOutputStream.closeEntry();
            }*/
        }
        return byteArrayOutputStream.toByteArray();
    }

    public static class MemoryFile {
        public String fileName;
        public byte[] contents;
    }
}
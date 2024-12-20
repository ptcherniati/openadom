package fr.inra.oresing.persistence.data.read.bundle;

public record FileContent(String fileName, String fileContent) {
    public static final String EXPORT_REGISTER_DATA_CSV_SQL = """
            WITH data_config AS (
              SELECT
                configuration #> ARRAY['datadescription', '%1$s', 'naturalkey'] AS naturalkey,
                configuration #> ARRAY['datadescription', '%1$s', 'componentdescriptions'] AS components,
                configuration #> ARRAY['datadescription', '%1$s', 'separator'] AS separator,
                (configuration #>> ARRAY['datadescription', '%1$s', 'headerline'])::int AS headerline,
                (configuration #>> ARRAY['datadescription', '%1$s', 'firstrowline'])::int AS firstrowline
              FROM application
              WHERE name = '%2$s'
            ),
            file_contents AS (
              SELECT
                rv.binaryfile,
                bf.updatedate,
                convert_from(decode(encode(bf.filedata, 'escape'), 'base64'), 'UTF8') AS content
              FROM %2$s.referencevalue rv
              JOIN %2$s.binaryfile bf ON bf.id = rv.binaryfile
              WHERE rv.referencetype = '%1$s'
              ORDER BY bf.updatedate
            ),
            parsed_files AS (
              SELECT
                fc.updatedate,
                (
                  SELECT string_agg(
                    regexp_replace(
                      trim(
                        translate(
                          lower(col),
                          'àáâãäåòóôõöøèéêëçìíîïùúûüÿñ',
                          'aaaaaaooooooeeeeciiiiuuuuyn'
                        )
                      ),
                      '[^a-z0-9]+', '_', 'g'
                    ),
                    '||'
                  )
                  FROM unnest(string_to_array(line, dc.separator::text)) WITH ORDINALITY t(col, idx)
                  WHERE idx <= array_length(array(SELECT jsonb_array_elements_text(dc.naturalkey)), 1)
                ) AS key_values,
                line,
                row_number() OVER (PARTITION BY fc.binaryfile ORDER BY (string_to_array(fc.content, E'\\n'))[1]) AS row_num
              FROM file_contents fc
              CROSS JOIN data_config dc,
              unnest(string_to_array(fc.content, E'\\n')) AS line
              WHERE line != ''
            ),
            processed_files AS (
              SELECT
                pf.updatedate,
                pf.key_values,
                pf.line,
                pf.row_num,
                dc.headerline,
                dc.firstrowline,
                ROW_NUMBER() OVER (PARTITION BY pf.key_values ORDER BY pf.updatedate DESC) AS version_rank
              FROM parsed_files pf
              CROSS JOIN data_config dc
              WHERE dc.headerline = 1 AND dc.firstrowline = 2
                 OR pf.row_num = dc.headerline
                 OR pf.row_num < dc.firstrowline
                 OR pf.row_num >= dc.firstrowline
            )
            SELECT
              format('%3$s.csv', '%1$s') fileName,
              COALESCE(
                (SELECT line FROM processed_files WHERE row_num = headerline LIMIT 1),
                ''
              ) || E'\\n' ||
              string_agg(
                CASE WHEN row_num != headerline THEN line ELSE '' END,
                E'\\n'
                ORDER BY updatedate
              ) AS fileContent
            FROM processed_files
            WHERE version_rank = 1
              AND (row_num != headerline OR row_num = (SELECT MIN(row_num) FROM processed_files WHERE row_num = headerline))
            GROUP BY headerline;""";

    public static final String EXPORT_PUBLISHED_DATA_AS_CSF_SQL = """
            WITH app_config AS (
              SELECT\s
                configuration #>> ARRAY['datadescription', '%1$s', 'submission', 'filenameparsing', 'pattern'] AS filename_pattern,
                configuration #> ARRAY['datadescription', '%1$s', 'submission', 'filenameparsing', 'authorizationscopes'] AS authorizationscopes,
                (configuration #>> ARRAY['datadescription', '%1$s', 'submission', 'filenameparsing', 'startdate'])::int AS startdate_index,
                (configuration #>> ARRAY['datadescription', '%1$s', 'submission', 'filenameparsing', 'enddate'])::int AS enddate_index
              FROM application
              WHERE name = '%2$s'
            ),
            binaryfile_data AS (
              SELECT DISTINCT ON (bf.id)
                bf.id,
                bf.params,
                bf.name AS original_name,
                bf.filedata,
                ac.filename_pattern,
                ac.authorizationscopes,
                ac.startdate_index,
                ac.enddate_index
              FROM %2$s.referencevalue rv
              JOIN %2$s.binaryfile bf ON bf.id = rv.binaryfile
              CROSS JOIN app_config ac
              WHERE rv.referencetype = '%1$s' AND (bf.params->>'published')::boolean = true
            ),
            filename_generation AS (
              SELECT\s
                bd.id,
                bd.filedata,
                bd.original_name,
                format(
                  regexp_replace(bd.filename_pattern, '\\(.*?\\)', '%3$ss', 'g'),
                  COALESCE(bd.params#>>'{binaryfiledataset,requiredauthorizations,projet}', ''),
                  COALESCE(bd.params#>>'{binaryfiledataset,requiredauthorizations,sites}', ''),
                  COALESCE(to_char((bd.params#>>'{binaryfiledataset,from}')::timestamp, 'YYYY-MM-DD'), ''),
                  COALESCE(to_char((bd.params#>>'{binaryfiledataset,to}')::timestamp, 'YYYY-MM-DD'), '')
                ) AS generated_filename
              FROM binaryfile_data bd
            )
            SELECT\s
              COALESCE(NULLIF(fg.generated_filename, ''), fg.original_name) AS fileName,
              convert_from(decode(encode(fg.filedata, 'escape'), 'base64'), 'UTF8') AS fileContent
            FROM filename_generation fg;""";
}
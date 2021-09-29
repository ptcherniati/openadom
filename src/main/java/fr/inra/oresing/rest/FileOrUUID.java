package fr.inra.oresing.rest;

import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFileDataset;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.DataRow;
import lombok.Getter;
import lombok.Setter;
import org.assertj.core.util.Strings;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.util.CollectionUtils;
import org.testcontainers.shaded.org.apache.commons.lang.StringEscapeUtils;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Getter
@Setter
public class FileOrUUID {
    @Nullable
    UUID fileid;
    @Nullable
    BinaryFileDataset binaryfiledataset;
    Boolean topublish = false;

}

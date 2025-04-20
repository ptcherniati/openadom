package fr.inra.oresing.rest;

import fr.inra.oresing.domain.BinaryFileDataset;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class AdditionalFileOrUUID {
    @Nullable
    UUID fileid;
    Map<String, String> fields;
    @Nullable
    Map<String, List<BinaryFileDataset>> associates;
}
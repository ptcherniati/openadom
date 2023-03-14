package fr.inra.oresing.rest;

import fr.inra.oresing.model.BinaryFileDataset;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;
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
package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;
import java.util.UUID;

@Getter
@Setter
public class FileOrUUID {
    @Nullable
    UUID fileid;
    @Nullable
    BinaryFileDataset binaryfiledataset;
    Boolean topublish = false;

}
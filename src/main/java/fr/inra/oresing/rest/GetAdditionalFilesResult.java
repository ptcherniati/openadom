package fr.inra.oresing.rest;

import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.additionalfiles.AdditionalBinaryFile;
import lombok.Value;

import java.util.List;

@Value
public class GetAdditionalFilesResult {
    String additionalFileName;
    List<AdditionalBinaryFile> additionalBinaryFiles;
    Configuration.AdditionalFileDescription description;
}
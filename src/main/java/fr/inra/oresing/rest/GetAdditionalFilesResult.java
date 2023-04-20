package fr.inra.oresing.rest;

import fr.inra.oresing.model.Configuration;
import lombok.Value;

import java.util.List;
import java.util.SortedSet;

@Value
public class GetAdditionalFilesResult {
    SortedSet<GetGrantableResult.User> users;
    String additionalFileName;
    List<AdditionalBinaryFileResult> additionalBinaryFiles;
    Configuration.AdditionalFileDescription description;
    List<String> fileNames;
}
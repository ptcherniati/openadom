package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.persistence.BinaryFileInfos;

import java.util.Optional;

public record FileNameResolver(
        AuthorizationPublicationService builder) implements State {
    public ParamsResolver resolveFileName(String fileName){
        BinaryFileDataset binaryFileDataset = params() == null ? new BinaryFileDataset() : params().binaryfiledataset();
        BinaryFileDataset resolvedBinaryFileDataset = Optional.ofNullable(dataDescription())
                .map(StandardDataDescription::submission)
                .map(submission -> submission.parseFileName(
                        fileName,
                        binaryFileDataset))
                .orElse(binaryFileDataset);
        FileOrUUID params = builder.params;
        if(params != null){
            builder.params =  params.withParams(new BinaryFileInfos(resolvedBinaryFileDataset));
        }else{
            builder.params = new FileOrUUID(null, resolvedBinaryFileDataset, true);
        }
        return new ParamsResolver
                (builder());
    }
}

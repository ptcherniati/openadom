package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.domain.services.synthesis.SynthesisService;

import java.util.Set;

public record UnPublishedVersions(AuthorizationPublicationService builder) implements State {

    public CheckRights unPublishVersions(
            Set<BinaryFile> filesToStore,
            DataRepository dataRepository,
            BinaryFileRepository binaryFileRepository,
            SynthesisService synthesisService) {

        if (builder().isRepository()) {
            if (params() != null && !params().topublish()) {
                if (binaryFile().getParams() != null && binaryFile().getParams().published()) {
                    binaryFile().markAsPublished(false);
                    filesToStore.add(binaryFile());
                    assert builder().hasRightForPublishOrUnPublish();
                    builder().unPublishVersions(filesToStore, dataRepository, binaryFileRepository, synthesisService);
                }
            } else if (params() != null && params().binaryfiledataset() != null) {
                BinaryFile publishedVersion = builder().getPublishedVersion(binaryFileRepository);
                if (publishedVersion != null && publishedVersion.getParams().published()) {
                    filesToStore.add(publishedVersion);
                    assert builder().hasRightForPublishOrUnPublish();
                    builder().unPublishVersions(filesToStore, dataRepository, binaryFileRepository, synthesisService);
                }
            }
        }
        return new CheckRights(builder());
    }
}

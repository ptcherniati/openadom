package fr.inra.oresing.rest.filesenderclient;

import java.nio.file.Path;

public interface FileRepository {
    String postTransfer(FileInfos fileInfos) throws Exception;
}

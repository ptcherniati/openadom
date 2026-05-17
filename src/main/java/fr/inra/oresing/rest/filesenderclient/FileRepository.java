package fr.inra.oresing.rest.filesenderclient;

import java.io.IOException;

public interface FileRepository {
    String postTransfer(FileInfos fileInfos) throws IOException;
}

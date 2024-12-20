package fr.inra.oresing.rest.filesenderclient;

public interface FileRepository {
    String postTransfer(FileInfos fileInfos) throws Exception;
}

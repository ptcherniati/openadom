package fr.inra.oresing.rest.filesenderclient;

import java.nio.file.Path;

public record FileInfos(String applicationName,
                        String dataName,
                        Path fileName,
                        String recipient,
                        String subject,
                        String message) {
}

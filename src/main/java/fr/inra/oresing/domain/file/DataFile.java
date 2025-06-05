package fr.inra.oresing.domain.file;

import java.io.InputStream;

public record DataFile(FileOrUUID params, InputStream inputData) {
}
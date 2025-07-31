package fr.inra.oresing.domain.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public record DataFile(File file, Long fileSize, String fileName) {
   public InputStream inputStream() throws FileNotFoundException {
        return file.exists() ? new FileInputStream(file) : null;
    }
}
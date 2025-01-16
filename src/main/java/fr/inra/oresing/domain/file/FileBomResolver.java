package fr.inra.oresing.domain.file;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FileBomResolver extends InputStream {
    private final BOMInputStream bomInputStream;

    public FileBomResolver(BOMInputStream bomInputStream) {
        this.bomInputStream = bomInputStream;
    }

    public static FileBomResolver of(final InputStream originalStream) throws IOException {
        return new FileBomResolver(
                BOMInputStream.builder()
                        .setInputStream(originalStream)
                        .setByteOrderMarks(ByteOrderMark.UTF_8)
                        .get()
        );
    }

    public static FileBomResolver of(final byte[] byteArray) throws IOException {
        return FileBomResolver.of(new ByteArrayInputStream(byteArray));
    }

    public static FileBomResolver of(final String text) throws IOException {
        ByteArrayInputStream textToByte = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        BOMInputStream bomInputStream = BOMInputStream.builder()
                .setInputStream(textToByte)
                .get();
        return FileBomResolver.of(bomInputStream);
    }

    @Override
    public int read() throws IOException {
        return bomInputStream.read();
    }
}

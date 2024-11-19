package fr.inra.oresing.domain.file;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FileBomResolver extends InputStream {
    private BOMInputStream bomInputStream;

    public FileBomResolver(BOMInputStream bomInputStream) {
        this.bomInputStream = bomInputStream;
    }

    public static final FileBomResolver of(final InputStream originalStream) {
        return new FileBomResolver(
                new BOMInputStream(originalStream, ByteOrderMark.UTF_8)
        );
    }

    public static final FileBomResolver of(final byte[] byteArray) {
        return FileBomResolver.of(new ByteArrayInputStream(byteArray));
    }

    public static final FileBomResolver of(final String text) {
        ByteArrayInputStream textToByte = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        BOMInputStream bomInputStream = new BOMInputStream(textToByte);
        return FileBomResolver.of(bomInputStream);
    }

    @Override
    public int read() throws IOException {
        return bomInputStream.read();
    }
}

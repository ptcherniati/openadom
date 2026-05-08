package fr.inrae.ore.cascade.api.defaults.db;
import java.io.IOException;
import java.io.Writer;
@FunctionalInterface
public interface RowSerializer<T> {
    void serialize(T record, Writer writer, RowSerializerContext ctx) throws IOException;
}

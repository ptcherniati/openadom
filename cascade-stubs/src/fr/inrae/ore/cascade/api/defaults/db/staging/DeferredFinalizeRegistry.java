package fr.inrae.ore.cascade.api.defaults.db.staging;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
public interface DeferredFinalizeRegistry {
    void register(ThrowingConsumer<Connection> action);
    @FunctionalInterface
    interface ThrowingConsumer<T> { void accept(T t) throws SQLException; }
}

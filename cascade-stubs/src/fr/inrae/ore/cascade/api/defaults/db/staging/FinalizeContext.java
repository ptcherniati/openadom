package fr.inrae.ore.cascade.api.defaults.db.staging;
import java.sql.Connection;
public interface FinalizeContext {
    Connection connection();
    String correlationId();
}

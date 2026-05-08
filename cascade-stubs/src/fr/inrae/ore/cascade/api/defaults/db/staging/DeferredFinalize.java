package fr.inrae.ore.cascade.api.defaults.db.staging;
import java.sql.SQLException;
public interface DeferredFinalize {
    void execute() throws SQLException;
    void cleanup() throws SQLException;
}

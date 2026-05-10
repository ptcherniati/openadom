package fr.inra.oresing.domain.sql;

/**
 * Instructions SQL DML utilisées dans les politiques de sécurité Row Level Security.
 *
 * <p>Migré depuis {@code persistence.SqlPolicy.Statement} pour rendre le domaine indépendant
 * de la couche de persistance.
 */
public enum SqlStatement {
    ALL, SELECT, INSERT, UPDATE, DELETE
}
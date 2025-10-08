package fr.inra.oresing.persistence.normalized;

import fr.inra.oresing.domain.application.normalized.Sql;

public record TableBuilder(Sql sql) {
    public String createTable() {
        return """
                --create table %2$s
                
                create table %1$s_dn.%2$s as (
                    select
                        referencevalue.id,
                        referencevalue.naturalkey,
                        referencevalue.hierarchicalkey,
                        %3$s
                
                    FROM %4$s
                    WHERE referencetype = '%2$s'
                    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
                );
                ALTER TABLE IF EXISTS  %1$s_dn.%2$s
                    OWNER TO "%9$s_applicationManager";
                
                GRANT SELECT ON TABLE %1$s_dn.%2$s TO PUBLIC;
                
                -- primary key
                ALTER TABLE %1$s_dn.%2$s
                    ALTER COLUMN id SET NOT NULL;
                ALTER TABLE %1$s_dn.%2$s
                    ADD CONSTRAINT %2$s_pk PRIMARY KEY (id);
                
                -- indexes
                %5$s 
                
                -- foreignKeys
                %6$s 
                
                -- many to many  
                %7$s
                
                -- policies
                %8$s
                
                """
                .formatted(
                        sql().schemaName(),
                        sql().tableName(),
                        new SelectBuilder(sql().select()).buildSelects(),
                        new FromBuilder(sql().schemaName(),sql().refValuesTable(), sql().referenceJoin())
                                .buildFrom(),
                        new BuildIndexes(sql().indexes()).buildIndexes(),
                        new ForeignKeysBuilder(sql.schemaName(), sql.tableName(), sql.foreignKeys())
                                .buildForeignKeys(),
                        new PoliciesBuilder(sql.schemaName(), sql.tableName(), sql.timescopes(), sql.authorizationScopes())
                                .buildPolicies(),
                        new ManyToManyBuilder(sql.schemaName(), sql.tableName(), sql.normalizedJoinManyToManies())
                                .buildManyToMany(),
                        sql.applicationId()
                );
    }
}
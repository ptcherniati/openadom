drop schema if exists %1$s_dn cascade;
create schema %1$s_dn;
-- génération des tables
create table %1$s_dn.referenceDisplay as
    (select id,
            hierarchicalkey,
            COALESCE(
                    NULLIF(refvalues ->> '__display_fr', ''),
                    refvalues ->> '__display_default'
            ) display_fr,
            COALESCE(
                    NULLIF(refvalues ->> '__display_en', ''),
                    NULLIF(refvalues ->> '__display_fr', ''),
                    refvalues ->> '__display_default'
            ) display_en
     from %1$s.referencevalue);


%2$



drop table %1$s_dn.referenceDisplay;
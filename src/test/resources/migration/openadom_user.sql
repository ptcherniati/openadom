-- openadom_user.sql
-- Script pour créer l'utilisateur '"openAdomTechUser"' avec les privilèges appropriés

-- Création du rôle "openAdomTechUser"
CREATE ROLE "openAdomTechUser" WITH
    LOGIN
    PASSWORD 'z2I<i}qclq)D?xqT'  -- À remplacer par un mot de passe sécurisé en production
    CREATEROLE
    CREATEDB;

-- Configuration du paramètre createrole_self_grant
ALTER ROLE "openAdomTechUser" SET createrole_self_grant TO 'inherit,set';

-- Accorder les droits nécessaires sur le schéma public à "openAdomTechUser"
-- Le schéma reste la propriété du database owner ; openAdomTechUser opère via des grants
-- et l'appartenance aux rôles applicatifs (GRANT ... TO "openAdomTechUser" WITH INHERIT TRUE).
GRANT USAGE, CREATE ON SCHEMA public TO "openAdomTechUser";

-- Accorder les privilèges nécessaires sur la base de données
GRANT ALL PRIVILEGES ON DATABASE test TO "openAdomTechUser";
ALTER ROLE "openAdomTechUser" BYPASSRLS;
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

-- Transférer la propriété du schéma public à "openAdomTechUser"
ALTER SCHEMA public OWNER TO "openAdomTechUser";

-- Accorder les privilèges nécessaires sur la base de données
GRANT ALL PRIVILEGES ON DATABASE openadom TO "openAdomTechUser";
ALTER ROLE "openAdomTechUser" BYPASSRLS;
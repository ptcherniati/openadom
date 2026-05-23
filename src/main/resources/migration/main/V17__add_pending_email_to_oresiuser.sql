-- =================================================================================
-- V17 : ajout colonne `pending_email` a OreSiUser pour decoupler le changement
--       d'email de sa validation .
--
-- Avant : updateAccount ecrivait directement le nouvel email + state=pending
--         AVANT la validation de la cle . Si l'utilisateur entrait une mauvaise
--         cle , l'email etait deja change ( perdu ) , le compte stuck en pending ,
--         et le 401 sur la validation deconnectait l'utilisateur . Compte trappe .
--
-- Apres : phase 1 stocke le nouvel email dans `pending_email` sans toucher
--         `email` ni `accountstate` ; phase 2 ( validation cle ) swap atomique
--         pending_email -> email . En cas de cle incorrecte , rien n'est
--         modifie -> retry possible , aucune perte de donnees .
--
-- Type : TEXT NULL ( pas de citext ni domain_email a ce stade -> contrainte
--        format appliquee a l'app layer ; on accepte la divergence pour ne pas
--        casser des inserts en cas d'email invalide pre-validation cote front ) .
-- Index : aucun ( colonne quasi toujours NULL , pas de query dessus ) .
-- Nom : pendingemail ( minuscules , sans underscore ) pour s'aligner sur la
--       convention des autres colonnes ( accountstate , creationdate , etc ) .
--       Le mapping Jackson cote JsonRowMapper utilise PropertyNamingStrategies
--       .LOWER_CASE : un nom avec underscore casserait la deserialisation
--       depuis to_jsonb(t) vers le champ Java pendingEmail .
-- =================================================================================

ALTER TABLE OreSiUser
    ADD COLUMN IF NOT EXISTS pendingemail TEXT NULL;

COMMENT ON COLUMN OreSiUser.pendingemail IS
    'Email cible d''un changement en cours , avant validation de la cle . NULL = pas de changement en attente .';

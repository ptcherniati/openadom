-- #487 Phase 3 : ajout du sujet de mail au payload du traitement.
--
-- Pendant la validation, le gestionnaire peut désormais saisir le sujet
-- du mail envoyé au demandeur ( en plus du corps existant ). On le
-- persiste pour réafficher exactement le mail envoyé en mode
-- consultation lecture seule.
ALTER TABLE RightsRequest
    ADD COLUMN IF NOT EXISTS treatmentMailSubject text;

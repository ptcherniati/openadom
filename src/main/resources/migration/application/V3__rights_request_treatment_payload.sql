-- #487 Phase 3 : persistance du contenu du traitement.
--
-- Le marqueur `setted` + l'identité `treatedBy` ( cf. V2 ) ne suffisent
-- pas à recharger la page de traitement en consultation seule : on a
-- besoin de la décision rendue ( APPROVED / REJECTED ), du commentaire
-- interne saisi par le gestionnaire, du texte du mail envoyé au
-- demandeur, et de la liste des autorisations attribuées le cas échéant.
--
-- Les champs sont nullables ( pas de FK sur linkedAuthorizationIds pour
-- les mêmes raisons que treatedBy, cf. V2 : éviter tout AccessExclusive
-- sur la table référencée par d'autres flux ).
ALTER TABLE RightsRequest
    ADD COLUMN IF NOT EXISTS treatmentDecision        text,
    ADD COLUMN IF NOT EXISTS treatmentComment         text,
    ADD COLUMN IF NOT EXISTS treatmentMailBody        text,
    ADD COLUMN IF NOT EXISTS linkedAuthorizationIds   uuid[];

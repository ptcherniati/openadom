-- ----------------------------------------------------------------------------
-- V6 : composite index ( referencetype , binaryfile ) on referencevalue
-- ----------------------------------------------------------------------------
-- Speeds up getReferencedBinaryFiles ( BinaryFileRepository ) which filters
-- by ( referencetype = X AND binaryfile IN ( Y... ) ) before joining
-- reference_reference + referencevalue2 . Without this index , the planner
-- falls back to a btree scan on ( referencetype ) alone , then filters in
-- memory by binaryfile - 3-5s for a single id on a 9.9M-row dataset .
--
-- The index has the columns in this order because referencetype is the
-- equality predicate and binaryfile is an IN-list ; PostgreSQL can use the
-- composite for both predicates .
--
-- ANALYZE is enqueued so the planner picks up cardinality stats before
-- the next query reuses the index .

CREATE INDEX IF NOT EXISTS referencevalue_referencetype_binaryfile_idx
    ON ${applicationSchema}.referencevalue (referencetype, binaryfile);

ANALYZE ${applicationSchema}.referencevalue;

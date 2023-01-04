ALTER TABLE IF EXISTS public.application
    ADD COLUMN version integer NOT NULL GENERATED ALWAYS AS ((configuration->'application'->>'version')::integer) STORED;
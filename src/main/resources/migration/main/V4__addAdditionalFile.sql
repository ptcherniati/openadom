ALTER TABLE IF EXISTS public.application
    ADD COLUMN  IF NOT EXISTS additionalFile text[];
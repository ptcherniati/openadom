ALTER TABLE referencevalue
    ADD CONSTRAINT "uk_reference" UNIQUE (referencetype, compositekey);
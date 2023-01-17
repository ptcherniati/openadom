
CREATE INDEX helping_rls ON ${applicationSchema}.data USING gist (
    ${requiredAuthorizationsAttributesIndex}
    ((data."authorization").datagroups[1]),
    COALESCE((data."authorization").timescope, '(,)'::tsrange)
);

ANALYSE ${applicationSchema}.data;

CREATE OR REPLACE FUNCTION update_configuration() RETURNS void AS $$
DECLARE
    r RECORD;
BEGIN
    RAISE NOTICE 'Updating configuration ...';

    FOR r IN
        select name, jsonb_object_keys(configuration #> '{datatypes}')  "datatype" from application
        LOOP

            update application set configuration = jsonb_set(
                    configuration,
                    Array['datatypes', r.datatype::text, 'authorization', 'columnsdescription', 'extraction', 'forRequest'],'true',true)
            where application.name = r.name;
            update application set configuration = jsonb_set(
                    configuration,
                    Array['datatypes', r.datatype::text, 'authorization', 'columnsdescription', 'extraction', 'forPublic'],'true',true)
            where application.name = r.name;

            RAISE NOTICE 'updating %, % ...',
                quote_ident(r.name), quote_ident(r.datatype);

        END LOOP;
    RETURN ;
END;
$$ LANGUAGE plpgsql;

select update_configuration();
DROP  FUNCTION update_configuration;
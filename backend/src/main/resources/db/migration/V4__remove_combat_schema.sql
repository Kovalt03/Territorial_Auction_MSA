-- combat-service cutover: monolith no longer owns building, island/vault, unit, or siege data.
-- trophy_logs keeps siege_id as an external scalar reference without a database foreign key.
ALTER TABLE public.trophy_logs
    DROP CONSTRAINT IF EXISTS trophy_logs_siege_id_fkey;

DROP TABLE IF EXISTS public.siege_structures;
DROP TABLE IF EXISTS public.siege_forces;
DROP TABLE IF EXISTS public.siege_results;
DROP TABLE IF EXISTS public.unit_research;
DROP TABLE IF EXISTS public.unit_instances;
DROP TABLE IF EXISTS public.siege_events;
DROP TABLE IF EXISTS public.attack_tokens;

DROP TABLE IF EXISTS public.building_instances;
DROP TABLE IF EXISTS public.building_castle_limits;
DROP TABLE IF EXISTS public.building_level_specs;
DROP TABLE IF EXISTS public.home_islands;
DROP TABLE IF EXISTS public.island_grades;

DROP TABLE IF EXISTS public.unit_type_level_specs;
DROP TABLE IF EXISTS public.unit_types;
DROP TABLE IF EXISTS public.building_types;
DROP TABLE IF EXISTS public.global_vaults;

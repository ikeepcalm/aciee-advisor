--liquibase formatted sql

--changeset aciee:024-invite-join splitStatements:false
CREATE FUNCTION core.join_by_invite(invite_token text, target_subgroup integer) RETURNS text
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE
    me      integer := core.app_user_id();
    invite  record;
    request integer;
BEGIN
    SELECT i.id, i.group_id, i.max_uses, i.created_by, i.expires_at > now() AS live, g.max_size,
           upper(ay.during) > current_date AS year_open, lower(ay.during) AS year_start, upper(ay.during) AS year_end
    INTO invite
    FROM core.group_invite i
    JOIN core.study_group g ON g.id = i.group_id
    JOIN core.academic_year ay ON ay.id = g.academic_year_id
    WHERE i.token_hash = sha256(convert_to(invite_token, 'UTF8'))
    FOR NO KEY UPDATE OF i, g;
    IF me IS NULL OR NOT FOUND OR NOT invite.live THEN
        RETURN 'invite_expired';
    END IF;
    IF NOT invite.year_open THEN
        RETURN 'year_ended';
    END IF;
    IF EXISTS (SELECT 1 FROM core.group_membership m
               WHERE m.user_id = me AND m.valid_during && daterange(current_date, NULL)) THEN
        RETURN 'already_member';
    END IF;
    IF CASE WHEN target_subgroup IS NULL
            THEN EXISTS (SELECT 1 FROM core.subgroup s WHERE s.group_id = invite.group_id)
            ELSE NOT EXISTS (SELECT 1 FROM core.subgroup s WHERE s.id = target_subgroup AND s.group_id = invite.group_id) END THEN
        RETURN 'invalid_subgroup';
    END IF;
    IF core.invite_uses(invite.id) >= invite.max_uses THEN
        RETURN 'invite_used_up';
    END IF;
    IF (SELECT count(*) FROM core.group_membership m
        WHERE m.group_id = invite.group_id AND m.valid_during && daterange(current_date, NULL)) >= invite.max_size THEN
        RETURN 'group_full';
    END IF;

    UPDATE core.membership_request
    SET subgroup_id = target_subgroup, invite_id = invite.id, status = 'approved', decided_by = invite.created_by, decided_at = now()
    WHERE user_id = me AND group_id = invite.group_id AND status = 'pending' AND kind = 'regular' AND offering_id IS NULL
    RETURNING id INTO request;
    IF request IS NULL THEN
        INSERT INTO core.membership_request (user_id, group_id, subgroup_id, kind, invite_id, status, decided_by, decided_at)
        VALUES (me, invite.group_id, target_subgroup, 'regular', invite.id, 'approved', invite.created_by, now())
        RETURNING id INTO request;
    END IF;
    INSERT INTO core.group_membership (user_id, group_id, subgroup_id, kind, source, request_id, valid_during)
    VALUES (me, invite.group_id, target_subgroup, 'regular', 'request', request,
            daterange(greatest(current_date, invite.year_start), invite.year_end));
    RETURN 'joined';
END
$$;

GRANT EXECUTE ON FUNCTION core.join_by_invite(text, integer) TO aciee_ident;
--rollback DROP FUNCTION core.join_by_invite(text, integer);

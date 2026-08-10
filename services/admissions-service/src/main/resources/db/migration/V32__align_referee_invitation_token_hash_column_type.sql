-- Author: Tinashe K

ALTER TABLE applicant_referee_invitations
    ALTER COLUMN token_hash TYPE varchar(64);

ALTER TABLE applicant_referee_invitations_aud
    ALTER COLUMN token_hash TYPE varchar(64);

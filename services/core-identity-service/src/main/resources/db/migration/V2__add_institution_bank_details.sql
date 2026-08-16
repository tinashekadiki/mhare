ALTER TABLE institution_profile
    ADD COLUMN bank_details_json jsonb;

ALTER TABLE institution_profile_aud
    ADD COLUMN bank_details_json jsonb;

UPDATE institution_profile
SET bank_details_json = jsonb_build_object(
        'bankName', 'CBZ BANK',
        'branchName', 'KWAME NKRUMAH AVENUE, HARARE',
        'accountNumber', '01120770100042/52',
        'branchSortCode', '6101',
        'swiftCode', 'COBZZWHAXXX'
    )
WHERE code = 'UZ'
  AND bank_details_json IS NULL;

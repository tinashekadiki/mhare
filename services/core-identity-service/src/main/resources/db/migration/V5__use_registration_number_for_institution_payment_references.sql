UPDATE institution_profile profile
SET bank_details_json = jsonb_set(
        profile.bank_details_json,
        '{accounts}',
        (
            SELECT jsonb_agg(
                    account.value || jsonb_build_object(
                        'paymentReferenceInstructions',
                        'After accepting this offer, eMhare will generate your registration number. Quote that registration number as the payment reference.'
                    )
                    ORDER BY account.ordinality
                )
            FROM jsonb_array_elements(profile.bank_details_json -> 'accounts')
                WITH ORDINALITY AS account(value, ordinality)
        ),
        false
    )
WHERE profile.code = 'UZ'
  AND jsonb_typeof(profile.bank_details_json -> 'accounts') = 'array';

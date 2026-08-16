UPDATE institution_profile
SET bank_details_json = (COALESCE(bank_details_json, '{}'::jsonb)
        - 'bankName'
        - 'branchName'
        - 'accountName'
        - 'accountNumber'
        - 'branchSortCode'
        - 'swiftCode'
        - 'paymentReferenceInstructions')
    || jsonb_build_object(
        'accounts', jsonb_build_array(
            jsonb_strip_nulls(jsonb_build_object(
                'currencyCode', 'ZWG',
                'bankName', 'CBZ BANK',
                'branchName', COALESCE(bank_details_json ->> 'branchName', 'KWAME NKRUMAH AVENUE, HARARE'),
                'accountName', bank_details_json ->> 'accountName',
                'accountNumber', '01120770100052',
                'branchSortCode', COALESCE(bank_details_json ->> 'branchSortCode', '6101'),
                'swiftCode', COALESCE(bank_details_json ->> 'swiftCode', 'COBZZWHAXXX'),
                'paymentReferenceInstructions', COALESCE(
                    bank_details_json ->> 'paymentReferenceInstructions',
                    'Use your eMhare application number as the payment reference')
            )),
            jsonb_strip_nulls(jsonb_build_object(
                'currencyCode', 'USD',
                'bankName', 'CBZ BANK',
                'branchName', COALESCE(bank_details_json ->> 'branchName', 'KWAME NKRUMAH AVENUE, HARARE'),
                'accountName', bank_details_json ->> 'accountName',
                'accountNumber', '01120770100249',
                'branchSortCode', COALESCE(bank_details_json ->> 'branchSortCode', '6101'),
                'swiftCode', COALESCE(bank_details_json ->> 'swiftCode', 'COBZZWHAXXX'),
                'paymentReferenceInstructions', COALESCE(
                    bank_details_json ->> 'paymentReferenceInstructions',
                    'Use your eMhare application number as the payment reference')
            )),
            jsonb_build_object(
                'currencyCode', 'ZWG',
                'bankName', 'BancABC',
                'accountNumber', '10099183902014',
                'paymentReferenceInstructions', COALESCE(
                    bank_details_json ->> 'paymentReferenceInstructions',
                    'Use your eMhare application number as the payment reference')
            ),
            jsonb_build_object(
                'currencyCode', 'USD',
                'bankName', 'BancABC',
                'accountNumber', '10099186633010',
                'paymentReferenceInstructions', COALESCE(
                    bank_details_json ->> 'paymentReferenceInstructions',
                    'Use your eMhare application number as the payment reference')
            )
        )
    )
WHERE code = 'UZ'
  AND NOT (COALESCE(bank_details_json, '{}'::jsonb) ? 'accounts');

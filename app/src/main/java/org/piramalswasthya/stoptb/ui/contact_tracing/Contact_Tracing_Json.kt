package org.piramalswasthya.stoptb.ui.contact_tracing

val Contact_Tracing_Json =
"""
    {
      "success": true,
      "message": null,
      "data": [
        {
          "formId": 4,
          "formUuid": "CONTACT_TRACING_SELECTOR",
          "formName": "Contact Tracing and TPT - Type Selector",
          "formType": "CONTACT_TRACING_SELECTOR",
          "isActive": true,
          "followUpDelayDays": 0,
          "versionNumber": 1,
          "triggerRule": {
            "description": "Opens for all confirmed TB cases. Beneficiary card shows a button to navigate to family/contacts of confirmed cases for household contact tracing.",
            "triggerCondition": "TB_CASE_STATUS = CONFIRMED"
          },
          "globalRule": {
            "description": "If a contact tests positive for active TB at any step in ANY downstream contact-tracing form, the system immediately redirects the field team / Outbreak Response Team to create a new TB Confirmed Case record for that contact, bypassing the TPT flow, pre-filled with the contact's available details.",
            "actionType": "REDIRECT_AND_CREATE_RECORD",
            "targetFormUuid": "TB_CONFIRMED_CASE_FORM",
            "bypassFlow": "TPT_FLOW"
          },
          "sections": [
            {
              "sectionId": 40,
              "sectionUuid": "CTS_SEC_A",
              "sectionName": "Contact Tracing Initiation",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": true,
              "displayOrder": 1,
              "hasSubmitButton": true,
              "questions": [
                {
                  "questionId": 401,
                  "questionUuid": "CTS_Q1",
                  "questionText": "Contact Tracing Initiated",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": true,
                  "displayOrder": 1,
                  "maxLength": null,
                  "defaultValue": "YES",
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    {
                      "optionId": 4001,
                      "optionLabel": "Yes",
                      "optionLabelHindi": null,
                      "optionValue": "YES",
                      "optionValueHindi": null,
                      "displayOrder": 1,
                      "conditions": [
                        {
                          "conditionId": 401,
                          "actionType": "SHOW_QUESTION",
                          "targetQuestionId": 402,
                          "targetQuestionUuid": "CTS_Q2",
                          "targetSectionId": null,
                          "targetSectionUuid": null
                        },
                        {
                          "conditionId": 402,
                          "actionType": "SHOW_QUESTION",
                          "targetQuestionId": 405,
                          "targetQuestionUuid": "CTS_Q5",
                          "targetSectionId": null,
                          "targetSectionUuid": null
                        }
                      ]
                    },
                    {
                      "optionId": 4002,
                      "optionLabel": "No",
                      "optionLabelHindi": null,
                      "optionValue": "NO",
                      "optionValueHindi": null,
                      "displayOrder": 2,
                      "conditions": [
                        {
                          "conditionId": 403,
                          "actionType": "DISABLE_QUESTION",
                          "targetQuestionId": 402,
                          "targetQuestionUuid": "CTS_Q2",
                          "targetSectionId": null,
                          "targetSectionUuid": null
                        },
                        {
                          "conditionId": 404,
                          "actionType": "DISABLE_QUESTION",
                          "targetQuestionId": 405,
                          "targetQuestionUuid": "CTS_Q5",
                          "targetSectionId": null,
                          "targetSectionUuid": null
                        },
                        {
                          "conditionId": 405,
                          "actionType": "SKIP_TO_SUBMIT",
                          "targetQuestionId": null,
                          "targetQuestionUuid": null,
                          "targetSectionId": null,
                          "targetSectionUuid": null
                        }
                      ]
                    }
                  ],
                  "validations": []
                },
                {
                  "questionId": 402,
                  "questionUuid": "CTS_Q2",
                  "questionText": "Occupation of the index case",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 2,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 4010, "optionLabel": "Unknown", "optionValue": "UNKNOWN", "displayOrder": 1, "conditions": [] },
                    {
                      "optionId": 4011,
                      "optionLabel": "Student",
                      "optionValue": "STUDENT",
                      "displayOrder": 2,
                      "conditions": [
                        {
                          "conditionId": 406,
                          "actionType": "SHOW_QUESTION",
                          "targetQuestionId": 404,
                          "targetQuestionUuid": "CTS_Q4",
                          "targetSectionId": null,
                          "targetSectionUuid": null
                        }
                      ]
                    },
                    { "optionId": 4012, "optionLabel": "Unemployed", "optionValue": "UNEMPLOYED", "displayOrder": 3, "conditions": [] },
                    { "optionId": 4013, "optionLabel": "Homemaker", "optionValue": "HOMEMAKER", "displayOrder": 4, "conditions": [] },
                    { "optionId": 4014, "optionLabel": "Farmer", "optionValue": "FARMER", "displayOrder": 5, "conditions": [] },
                    {
                      "optionId": 4015,
                      "optionLabel": "Laborer / Daily Wage Worker",
                      "optionValue": "LABORER_DAILY_WAGE",
                      "displayOrder": 6,
                      "conditions": [
                        { "conditionId": 407, "actionType": "SHOW_QUESTION", "targetQuestionId": 403, "targetQuestionUuid": "CTS_Q3", "targetSectionId": null, "targetSectionUuid": null }
                      ]
                    },
                    {
                      "optionId": 4016,
                      "optionLabel": "Self-employed / Business",
                      "optionValue": "SELF_EMPLOYED_BUSINESS",
                      "displayOrder": 7,
                      "conditions": [
                        { "conditionId": 408, "actionType": "SHOW_QUESTION", "targetQuestionId": 403, "targetQuestionUuid": "CTS_Q3", "targetSectionId": null, "targetSectionUuid": null }
                      ]
                    },
                    {
                      "optionId": 4017,
                      "optionLabel": "Government Employee",
                      "optionValue": "GOVERNMENT_EMPLOYEE",
                      "displayOrder": 8,
                      "conditions": [
                        { "conditionId": 409, "actionType": "SHOW_QUESTION", "targetQuestionId": 403, "targetQuestionUuid": "CTS_Q3", "targetSectionId": null, "targetSectionUuid": null }
                      ]
                    },
                    {
                      "optionId": 4018,
                      "optionLabel": "Private Employee",
                      "optionValue": "PRIVATE_EMPLOYEE",
                      "displayOrder": 9,
                      "conditions": [
                        { "conditionId": 410, "actionType": "SHOW_QUESTION", "targetQuestionId": 403, "targetQuestionUuid": "CTS_Q3", "targetSectionId": null, "targetSectionUuid": null }
                      ]
                    },
                    {
                      "optionId": 4019,
                      "optionLabel": "Health Care Worker",
                      "optionValue": "HEALTH_CARE_WORKER",
                      "displayOrder": 10,
                      "conditions": [
                        { "conditionId": 411, "actionType": "SHOW_QUESTION", "targetQuestionId": 403, "targetQuestionUuid": "CTS_Q3", "targetSectionId": null, "targetSectionUuid": null }
                      ]
                    },
                    { "optionId": 4020, "optionLabel": "Retired / Pensioner", "optionValue": "RETIRED_PENSIONER", "displayOrder": 11, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 403,
                  "questionUuid": "CTS_Q3",
                  "questionText": "Name and Address of employment",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 3,
                  "maxLength": 500,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": false,
                  "enabledIf": {
                    "questionUuid": "CTS_Q2",
                    "inValues": ["LABORER_DAILY_WAGE", "SELF_EMPLOYED_BUSINESS", "GOVERNMENT_EMPLOYEE", "PRIVATE_EMPLOYEE", "HEALTH_CARE_WORKER"]
                  },
                  "options": [],
                  "validations": [
                    { "validationId": 40, "validationType": "MAX_LENGTH", "validationParam": "500", "errorMessage": "Must be 500 characters or fewer" },
                    { "validationId": 41, "validationType": "ALLOWED_CHARS", "validationParam": "ALPHANUMERIC_SYMBOLS", "errorMessage": "Alphanumeric characters and symbols only" }
                  ]
                },
                {
                  "questionId": 404,
                  "questionUuid": "CTS_Q4",
                  "questionText": "Name and Address of Institution",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 4,
                  "maxLength": 500,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": false,
                  "enabledIf": {
                    "questionUuid": "CTS_Q2",
                    "inValues": ["STUDENT"]
                  },
                  "options": [],
                  "validations": [
                    { "validationId": 42, "validationType": "MAX_LENGTH", "validationParam": "500", "errorMessage": "Must be 500 characters or fewer" },
                    { "validationId": 43, "validationType": "ALLOWED_CHARS", "validationParam": "ALPHANUMERIC_SYMBOLS", "errorMessage": "Alphanumeric characters and symbols only" }
                  ]
                },
                {
                  "questionId": 405,
                  "questionUuid": "CTS_Q5",
                  "questionText": "Type of Contact tracing",
                  "questionTextHindi": null,
                  "questionType": "CHECKBOX",
                  "allowMultiple": true,
                  "isMandatory": true,
                  "displayOrder": 5,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "mandatoryIf": { "questionUuid": "CTS_Q1", "equals": "YES" },
                  "options": [
                    {
                      "optionId": 4030,
                      "optionLabel": "Household",
                      "optionValue": "HOUSEHOLD",
                      "displayOrder": 1,
                      "conditions": [
                        { "conditionId": 412, "actionType": "OPEN_FORM", "targetFormUuid": "HHC_CONTACT_TRACING", "targetQuestionId": null, "targetQuestionUuid": null, "targetSectionId": null, "targetSectionUuid": null }
                      ]
                    },
                    {
                      "optionId": 4031,
                      "optionLabel": "Community",
                      "optionValue": "COMMUNITY",
                      "displayOrder": 2,
                      "conditions": [
                        { "conditionId": 413, "actionType": "SHOW_QUESTION", "targetQuestionId": 406, "targetQuestionUuid": "CTS_Q6", "targetSectionId": null, "targetSectionUuid": null },
                        { "conditionId": 414, "actionType": "OPEN_FORM", "targetFormUuid": "COMMUNITY_CONTACT_TRACING", "targetQuestionId": null, "targetQuestionUuid": null, "targetSectionId": null, "targetSectionUuid": null }
                      ]
                    },
                    {
                      "optionId": 4032,
                      "optionLabel": "Occupational",
                      "optionValue": "OCCUPATIONAL",
                      "displayOrder": 3,
                      "conditions": [
                        { "conditionId": 415, "actionType": "SHOW_QUESTION", "targetQuestionId": 407, "targetQuestionUuid": "CTS_Q7", "targetSectionId": null, "targetSectionUuid": null },
                        { "conditionId": 416, "actionType": "OPEN_FORM", "targetFormUuid": "OCCUPATIONAL_CONTACT_TRACING", "targetQuestionId": null, "targetQuestionUuid": null, "targetSectionId": null, "targetSectionUuid": null }
                      ]
                    }
                  ],
                  "validations": [],
                  "note": "Multiple types can be selected simultaneously. Each selection opens its respective sub-form."
                },
                {
                  "questionId": 406,
                  "questionUuid": "CTS_Q6",
                  "questionText": "No. of Community contacts",
                  "questionTextHindi": null,
                  "questionType": "NUMBER",
                  "isMandatory": true,
                  "displayOrder": 6,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "CTS_Q5", "containsValue": "COMMUNITY" },
                  "options": [],
                  "validations": [
                    { "validationId": 44, "validationType": "NUMERIC_ONLY", "validationParam": null, "errorMessage": "Only numerical values allowed" }
                  ]
                },
                {
                  "questionId": 407,
                  "questionUuid": "CTS_Q7",
                  "questionText": "No. of Occupational Contacts",
                  "questionTextHindi": null,
                  "questionType": "NUMBER",
                  "isMandatory": true,
                  "displayOrder": 7,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "CTS_Q5", "containsValue": "OCCUPATIONAL" },
                  "options": [],
                  "validations": [
                    { "validationId": 45, "validationType": "NUMERIC_ONLY", "validationParam": null, "errorMessage": "Only numerical values allowed" }
                  ]
                },
                {
                  "questionId": 408,
                  "questionUuid": "CTS_REMARKS",
                  "questionText": "Any other significant Information",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": false,
                  "displayOrder": 8,
                  "maxLength": 500,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 46, "validationType": "MIN_MAX_WORDS", "validationParam": "250-500", "errorMessage": "Please provide between 250 and 500 words" }
                  ]
                }
              ]
            }
          ]
        },

        {
          "formId": 5,
          "formUuid": "HHC_CONTACT_TRACING",
          "formName": "Household Contact (HHC) Tracing Form",
          "formType": "CONTACT_TRACING_HOUSEHOLD",
          "isActive": true,
          "followUpDelayDays": 30,
          "versionNumber": 1,
          "enabledIf": { "sourceFormUuid": "CONTACT_TRACING_SELECTOR", "questionUuid": "CTS_Q5", "containsValue": "HOUSEHOLD" },
          "sections": [
            {
              "sectionId": 50,
              "sectionUuid": "HHC_SEC_A",
              "sectionName": "Contact Details & Symptom Screening",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": true,
              "displayOrder": 1,
              "hasSubmitButton": false,
              "questions": [
                {
                  "questionId": 501,
                  "questionUuid": "HHC_Q1",
                  "questionText": "Name of Contact",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 1,
                  "maxLength": 100,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 50, "validationType": "MAX_LENGTH", "validationParam": "100", "errorMessage": "Must be 100 characters or fewer" },
                    { "validationId": 51, "validationType": "FORCE_UPPERCASE", "validationParam": null, "errorMessage": null }
                  ]
                },
                {
                  "questionId": 502,
                  "questionUuid": "HHC_Q2",
                  "questionText": "Does contact have any TB symptoms?",
                  "questionTextHindi": null,
                  "questionType": "CHECKBOX",
                  "allowMultiple": true,
                  "isMandatory": true,
                  "displayOrder": 2,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "autoPopulateNote": "Provision to auto-populate this and related fields if the family member has already been screened in the application.",
                  "options": [
                    { "optionId": 5001, "optionLabel": "Coughing > 2 weeks", "optionValue": "COUGH_GT_2W", "displayOrder": 1, "conditions": [ { "conditionId": 501, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment.", "targetQuestionId": null, "targetQuestionUuid": null }, { "conditionId": 502, "actionType": "DISABLE_QUESTION", "targetQuestionId": 507, "targetQuestionUuid": "HHC_Q7", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 5002, "optionLabel": "Blood in Sputum", "optionValue": "BLOOD_SPUTUM", "displayOrder": 2, "conditions": [ { "conditionId": 503, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 504, "actionType": "DISABLE_QUESTION", "targetQuestionId": 507, "targetQuestionUuid": "HHC_Q7", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 5003, "optionLabel": "Fever > 2 weeks", "optionValue": "FEVER_GT_2W", "displayOrder": 3, "conditions": [ { "conditionId": 505, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 506, "actionType": "DISABLE_QUESTION", "targetQuestionId": 507, "targetQuestionUuid": "HHC_Q7", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 5004, "optionLabel": "Rise of fever in evening", "optionValue": "EVENING_FEVER", "displayOrder": 4, "conditions": [ { "conditionId": 507, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 508, "actionType": "DISABLE_QUESTION", "targetQuestionId": 507, "targetQuestionUuid": "HHC_Q7", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 5005, "optionLabel": "Loss of Appetite", "optionValue": "LOSS_APPETITE", "displayOrder": 5, "conditions": [ { "conditionId": 509, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 510, "actionType": "DISABLE_QUESTION", "targetQuestionId": 507, "targetQuestionUuid": "HHC_Q7", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 5006, "optionLabel": "Loss of Weight", "optionValue": "LOSS_WEIGHT", "displayOrder": 6, "conditions": [ { "conditionId": 511, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 512, "actionType": "DISABLE_QUESTION", "targetQuestionId": 507, "targetQuestionUuid": "HHC_Q7", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 5007, "optionLabel": "Night Sweats", "optionValue": "NIGHT_SWEATS", "displayOrder": 7, "conditions": [ { "conditionId": 513, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 514, "actionType": "DISABLE_QUESTION", "targetQuestionId": 507, "targetQuestionUuid": "HHC_Q7", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 5008, "optionLabel": "History of TB", "optionValue": "HISTORY_TB", "displayOrder": 8, "conditions": [ { "conditionId": 515, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 516, "actionType": "DISABLE_QUESTION", "targetQuestionId": 507, "targetQuestionUuid": "HHC_Q7", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 5009, "optionLabel": "Currently taking Anti-TB drugs", "optionValue": "ON_ANTI_TB_DRUGS", "displayOrder": 9, "conditions": [ { "conditionId": 517, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 518, "actionType": "DISABLE_QUESTION", "targetQuestionId": 507, "targetQuestionUuid": "HHC_Q7", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 5010, "optionLabel": "None", "optionValue": "NONE", "displayOrder": 10, "conditions": [], "isExclusive": true }
                  ],
                  "validations": []
                },
                {
                  "questionId": 503,
                  "questionUuid": "HHC_Q3",
                  "questionText": "Referred for TB screening",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": true,
                  "displayOrder": 3,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 5020, "optionLabel": "Yes", "optionValue": "YES", "displayOrder": 1, "conditions": [ { "conditionId": 519, "actionType": "SHOW_QUESTION", "targetQuestionId": 504, "targetQuestionUuid": "HHC_Q4" } ] },
                    { "optionId": 5021, "optionLabel": "No", "optionValue": "NO", "displayOrder": 2, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 504,
                  "questionUuid": "HHC_Q4",
                  "questionText": "Referral facility for screening",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 4,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "HHC_Q3", "equals": "YES" },
                  "note": "Option to go for direct submission as all next fields of screening and TPT initiation may require a few days.",
                  "options": [
                    { "optionId": 5030, "optionLabel": "TB Screening Camp", "optionValue": "TB_SCREENING_CAMP", "displayOrder": 1, "conditions": [] },
                    { "optionId": 5031, "optionLabel": "Medical College", "optionValue": "MEDICAL_COLLEGE", "displayOrder": 2, "conditions": [] },
                    { "optionId": 5032, "optionLabel": "HWC", "optionValue": "HWC", "displayOrder": 3, "conditions": [] },
                    { "optionId": 5033, "optionLabel": "PHC", "optionValue": "PHC", "displayOrder": 4, "conditions": [] },
                    { "optionId": 5034, "optionLabel": "CHC", "optionValue": "CHC", "displayOrder": 5, "conditions": [] },
                    { "optionId": 5035, "optionLabel": "District Hospital", "optionValue": "DISTRICT_HOSPITAL", "displayOrder": 6, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 505,
                  "questionUuid": "HHC_Q5",
                  "questionText": "Screening done at referral facility",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": false,
                  "displayOrder": 5,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "editablePostSubmission": true,
                  "options": [
                    { "optionId": 5040, "optionLabel": "Yes", "optionValue": "YES", "displayOrder": 1, "conditions": [ { "conditionId": 520, "actionType": "SHOW_QUESTION", "targetQuestionId": 506, "targetQuestionUuid": "HHC_Q6" } ] },
                    { "optionId": 5041, "optionLabel": "No", "optionValue": "NO", "displayOrder": 2, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 506,
                  "questionUuid": "HHC_Q6",
                  "questionText": "Screening result",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": false,
                  "displayOrder": 6,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "HHC_Q5", "equals": "YES" },
                  "options": [
                    {
                      "optionId": 5050,
                      "optionLabel": "TB confirmed",
                      "optionValue": "TB_CONFIRMED",
                      "displayOrder": 1,
                      "conditions": [
                        { "conditionId": 521, "actionType": "MOVE_CARD_TO_LIST", "targetList": "CONFIRMED_TB_LIST" },
                        { "conditionId": 522, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" }
                      ]
                    },
                    { "optionId": 5051, "optionLabel": "No TB", "optionValue": "NO_TB", "displayOrder": 2, "conditions": [ { "conditionId": 523, "actionType": "ENABLE_QUESTION", "targetQuestionId": 507, "targetQuestionUuid": "HHC_Q7" } ] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 507,
                  "questionUuid": "HHC_Q7",
                  "questionText": "Advised to take TPT",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": true,
                  "displayOrder": 7,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "HHC_Q6", "equals": "NO_TB" },
                  "options": [
                    { "optionId": 5060, "optionLabel": "Yes", "optionValue": "YES", "displayOrder": 1, "conditions": [ { "conditionId": 524, "actionType": "SHOW_QUESTION", "targetQuestionId": 508, "targetQuestionUuid": "HHC_Q8" } ] },
                    { "optionId": 5061, "optionLabel": "No", "optionValue": "NO", "displayOrder": 2, "conditions": [ { "conditionId": 525, "actionType": "GO_TO_SUBMIT" } ] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 508,
                  "questionUuid": "HHC_Q8",
                  "questionText": "Contact eligible for TPT?",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 8,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "HHC_Q7", "equals": "YES" },
                  "options": [
                    { "optionId": 5070, "optionLabel": "Child under 5 years - HHC of pulmonary TB case", "optionValue": "CHILD_UNDER_5", "displayOrder": 1, "conditions": [ { "conditionId": 526, "actionType": "ENABLE_QUESTION", "targetQuestionId": 509, "targetQuestionUuid": "HHC_Q9" } ] },
                    { "optionId": 5071, "optionLabel": "Child 5-14 years - HHC of pulmonary TB case", "optionValue": "CHILD_5_14", "displayOrder": 2, "conditions": [ { "conditionId": 527, "actionType": "ENABLE_QUESTION", "targetQuestionId": 509, "targetQuestionUuid": "HHC_Q9" } ] },
                    { "optionId": 5072, "optionLabel": "Person 15 years and above - HHC of pulmonary TB case", "optionValue": "PERSON_15_PLUS", "displayOrder": 3, "conditions": [ { "conditionId": 528, "actionType": "ENABLE_QUESTION", "targetQuestionId": 509, "targetQuestionUuid": "HHC_Q9" } ] },
                    { "optionId": 5073, "optionLabel": "HIV positive", "optionValue": "HIV_POSITIVE", "displayOrder": 4, "conditions": [ { "conditionId": 529, "actionType": "ENABLE_QUESTION", "targetQuestionId": 509, "targetQuestionUuid": "HHC_Q9" } ] },
                    { "optionId": 5074, "optionLabel": "Person living in institutional setting (prison, shelter home, de-addiction centre)", "optionValue": "INSTITUTIONAL_SETTING", "displayOrder": 5, "conditions": [ { "conditionId": 530, "actionType": "ENABLE_QUESTION", "targetQuestionId": 509, "targetQuestionUuid": "HHC_Q9" } ] },
                    { "optionId": 5075, "optionLabel": "Already on anti-TB treatment (active TB confirmed)", "optionValue": "ON_ANTI_TB_TREATMENT", "displayOrder": 6, "conditions": [ { "conditionId": 531, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 532, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 5076, "optionLabel": "Completed TB treatment in the past (previous history of TB)", "optionValue": "COMPLETED_TB_TREATMENT_PAST", "displayOrder": 7, "conditions": [ { "conditionId": 533, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 534, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 5077, "optionLabel": "Already completed a course of TPT", "optionValue": "COMPLETED_TPT_COURSE", "displayOrder": 8, "conditions": [ { "conditionId": 535, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 536, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 5078, "optionLabel": "Active TB not yet ruled out - screening pending", "optionValue": "TB_NOT_RULED_OUT", "displayOrder": 9, "conditions": [ { "conditionId": 537, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 538, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 5079, "optionLabel": "Contraindication to TPT drugs (liver disease, drug allergy)", "optionValue": "CONTRAINDICATION_TPT", "displayOrder": 10, "conditions": [ { "conditionId": 539, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 540, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 5080, "optionLabel": "Active TB ruled out but clinical review needed before initiating (abnormalities detected)", "optionValue": "CLINICAL_REVIEW_NEEDED", "displayOrder": 11, "conditions": [ { "conditionId": 541, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 542, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 5081, "optionLabel": "Insufficient information to determine eligibility", "optionValue": "INSUFFICIENT_INFO", "displayOrder": 12, "conditions": [ { "conditionId": 543, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 544, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] }
                  ],
                  "note": "If option 6-12 selected, do not enable any TPT-related fields; provide option for direct submission.",
                  "validations": []
                },
                {
                  "questionId": 509,
                  "questionUuid": "HHC_Q9",
                  "questionText": "TPT start date",
                  "questionTextHindi": null,
                  "questionType": "DATE",
                  "isMandatory": true,
                  "displayOrder": 9,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "HHC_Q8", "inValues": ["CHILD_UNDER_5", "CHILD_5_14", "PERSON_15_PLUS", "HIV_POSITIVE", "INSTITUTIONAL_SETTING"] },
                  "options": [],
                  "validations": [
                    { "validationId": 52, "validationType": "DATE_NOT_FUTURE", "validationParam": "TODAY", "errorMessage": "Cannot be a future date" },
                    { "validationId": 53, "validationType": "DATE_NOT_BEFORE", "validationParam": "CONTACT_SCREENING_DATE", "errorMessage": "Cannot be before date of screening (contact screening)" }
                  ]
                },
                {
                  "questionId": 510,
                  "questionUuid": "HHC_Q10",
                  "questionText": "TPT Regimen advised",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": true,
                  "displayOrder": 10,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "HHC_Q9", "isNotEmpty": true },
                  "options": [
                    { "optionId": 5090, "optionLabel": "6H — Isoniazid daily for 6 months", "optionValue": "6H", "displayOrder": 1, "conditions": [] },
                    { "optionId": 5091, "optionLabel": "3HP — Isoniazid + Rifapentine weekly for 3 months", "optionValue": "3HP", "displayOrder": 2, "conditions": [] },
                    { "optionId": 5092, "optionLabel": "3HR — Isoniazid + Rifampicin daily for 3 months", "optionValue": "3HR", "displayOrder": 3, "conditions": [] },
                    { "optionId": 5093, "optionLabel": "1HP — Isoniazid + Rifapentine daily for 1 month", "optionValue": "1HP", "displayOrder": 4, "conditions": [] },
                    { "optionId": 5094, "optionLabel": "6H (Modified dose) — Isoniazid daily for 6 months with dose adjustment for weight or age", "optionValue": "6H_MODIFIED", "displayOrder": 5, "conditions": [] }
                  ],
                  "validations": []
                }
              ]
            },
            {
              "sectionId": 51,
              "sectionUuid": "HHC_SEC_B",
              "sectionName": "Follow-up and Outcome",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": false,
              "displayOrder": 2,
              "hasSubmitButton": false,
              "questions": [
                {
                  "questionId": 511,
                  "questionUuid": "HHC_Q11",
                  "questionText": "Follow-up visit number",
                  "questionTextHindi": null,
                  "questionType": "READONLY_NUMBER",
                  "isMandatory": false,
                  "displayOrder": 1,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "autoPopulated": true,
                  "autoPopulateLogic": "Auto-increment 1-6, automatically calculated by system monthly",
                  "options": [],
                  "validations": []
                },
                {
                  "questionId": 512,
                  "questionUuid": "HHC_Q12",
                  "questionText": "Follow-up visit date",
                  "questionTextHindi": null,
                  "questionType": "DATE",
                  "isMandatory": true,
                  "displayOrder": 2,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 54, "validationType": "DATE_NOT_FUTURE", "validationParam": "TODAY", "errorMessage": "Cannot be a future date" },
                    { "validationId": 55, "validationType": "DATE_NOT_BEFORE", "validationParam": "HHC_Q9", "errorMessage": "Cannot be before TPT start date" }
                  ]
                },
                {
                  "questionId": 513,
                  "questionUuid": "HHC_Q13",
                  "questionText": "TPT outcome status",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 3,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 5100, "optionLabel": "Completed", "optionValue": "COMPLETED", "displayOrder": 1, "conditions": [ { "conditionId": 545, "actionType": "ENABLE_QUESTION", "targetQuestionId": 515, "targetQuestionUuid": "HHC_Q15" } ] },
                    { "optionId": 5101, "optionLabel": "Lost to follow-up", "optionValue": "LOST_TO_FOLLOWUP", "displayOrder": 2, "conditions": [] },
                    { "optionId": 5102, "optionLabel": "Developed active TB during TPT", "optionValue": "DEVELOPED_ACTIVE_TB", "displayOrder": 3, "conditions": [ { "conditionId": 546, "actionType": "REDIRECT_TO_FORM", "targetFormUuid": "TB_PRESUMPTIVE_CASE", "note": "Redirects volunteer to create new TB Presumptive Case record and closes this TPT case" } ] },
                    { "optionId": 5103, "optionLabel": "Died during TPT", "optionValue": "DIED_DURING_TPT", "displayOrder": 4, "conditions": [ { "conditionId": 547, "actionType": "ENABLE_QUESTION", "targetQuestionId": 515, "targetQuestionUuid": "HHC_Q15" }, { "conditionId": 548, "actionType": "ENABLE_QUESTION", "targetQuestionId": 516, "targetQuestionUuid": "HHC_Q16" } ] },
                    { "optionId": 5104, "optionLabel": "Other", "optionValue": "OTHER", "displayOrder": 5, "conditions": [ { "conditionId": 549, "actionType": "ENABLE_QUESTION", "targetQuestionId": 514, "targetQuestionUuid": "HHC_Q14" } ] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 514,
                  "questionUuid": "HHC_Q14",
                  "questionText": "Other",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": false,
                  "displayOrder": 4,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "HHC_Q13", "equals": "OTHER" },
                  "options": [],
                  "validations": []
                },
                {
                  "questionId": 515,
                  "questionUuid": "HHC_Q15",
                  "questionText": "Date of TPT outcome",
                  "questionTextHindi": null,
                  "questionType": "DATE",
                  "isMandatory": true,
                  "displayOrder": 5,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "HHC_Q13", "inValues": ["COMPLETED", "DIED_DURING_TPT"] },
                  "disabledIf": { "questionUuid": "HHC_Q8", "inValues": ["ON_ANTI_TB_TREATMENT", "COMPLETED_TB_TREATMENT_PAST", "COMPLETED_TPT_COURSE", "TB_NOT_RULED_OUT", "CONTRAINDICATION_TPT", "CLINICAL_REVIEW_NEEDED", "INSUFFICIENT_INFO"] },
                  "options": [],
                  "validations": [
                    { "validationId": 56, "validationType": "DATE_NOT_FUTURE", "validationParam": "TODAY", "errorMessage": "Cannot be a future date" }
                  ]
                },
                {
                  "questionId": 516,
                  "questionUuid": "HHC_Q16",
                  "questionText": "Cause of death",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 6,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "HHC_Q13", "equals": "DIED_DURING_TPT" },
                  "options": [],
                  "validations": []
                }
              ]
            },
            {
              "sectionId": 52,
              "sectionUuid": "HHC_SEC_C",
              "sectionName": "Location & Metadata",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": true,
              "displayOrder": 3,
              "hasSubmitButton": false,
              "questions": [
                {
                  "questionId": 517,
                  "questionUuid": "HHC_Q17",
                  "questionText": "Latitude",
                  "questionTextHindi": null,
                  "questionType": "READONLY_NUMBER",
                  "isMandatory": false,
                  "displayOrder": 1,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "autoPopulated": true,
                  "options": [],
                  "validations": [
                    { "validationId": 57, "validationType": "FORMAT", "validationParam": "DECIMAL_DEGREES", "errorMessage": "e.g. 21.1458" }
                  ]
                },
                {
                  "questionId": 518,
                  "questionUuid": "HHC_Q18",
                  "questionText": "Longitude",
                  "questionTextHindi": null,
                  "questionType": "READONLY_NUMBER",
                  "isMandatory": false,
                  "displayOrder": 2,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "autoPopulated": true,
                  "options": [],
                  "validations": [
                    { "validationId": 58, "validationType": "FORMAT", "validationParam": "DECIMAL_DEGREES", "errorMessage": "e.g. 21.1458" }
                  ]
                },
                {
                  "questionId": 519,
                  "questionUuid": "HHC_Q19",
                  "questionText": "DigiPin",
                  "questionTextHindi": null,
                  "questionType": "READONLY_TEXT",
                  "isMandatory": false,
                  "displayOrder": 3,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "autoPopulated": true,
                  "autoPopulateLogic": "Auto-generated from Lat/Long using DIGIPIN encoding algorithm (open-source, offline-capable)",
                  "displayFormat": "XXXX-XXXX-XX",
                  "options": [],
                  "validations": []
                },
                {
                  "questionId": 520,
                  "questionUuid": "HHC_Q20",
                  "questionText": "Timestamp",
                  "questionTextHindi": null,
                  "questionType": "READONLY_TEXT",
                  "isMandatory": false,
                  "displayOrder": 4,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "autoPopulated": true,
                  "autoPopulateLogic": "System-captured date and time",
                  "options": [],
                  "validations": []
                }
              ]
            },
            {
              "sectionId": 53,
              "sectionUuid": "HHC_SEC_D",
              "sectionName": "Additional Information",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": false,
              "displayOrder": 4,
              "hasSubmitButton": true,
              "questions": [
                {
                  "questionId": 521,
                  "questionUuid": "HHC_REMARKS",
                  "questionText": "Any other significant Information",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": false,
                  "displayOrder": 1,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 59, "validationType": "MIN_MAX_WORDS", "validationParam": "250-500", "errorMessage": "Please provide between 250 and 500 words" }
                  ]
                }
              ]
            }
          ]
        },

        {
          "formId": 6,
          "formUuid": "COMMUNITY_CONTACT_TRACING",
          "formName": "Community Contact Tracing Form",
          "formType": "CONTACT_TRACING_COMMUNITY",
          "isActive": true,
          "followUpDelayDays": 30,
          "versionNumber": 1,
          "definition": "People outside the household who spend significant time in close proximity to the index case in shared community spaces (religious gatherings, community halls, markets, transport, etc.)",
          "enabledIf": { "sourceFormUuid": "CONTACT_TRACING_SELECTOR", "questionUuid": "CTS_Q5", "containsValue": "COMMUNITY" },
          "sections": [
            {
              "sectionId": 60,
              "sectionUuid": "COM_SEC_A",
              "sectionName": "Contact Details",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": true,
              "displayOrder": 1,
              "hasSubmitButton": false,
              "questions": [
                {
                  "questionId": 601,
                  "questionUuid": "COM_Q1",
                  "questionText": "Name of Contact",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 1,
                  "maxLength": 100,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 60, "validationType": "MAX_LENGTH", "validationParam": "100", "errorMessage": "Must be 100 characters or fewer" },
                    { "validationId": 61, "validationType": "FORCE_UPPERCASE", "validationParam": null, "errorMessage": null }
                  ]
                },
                {
                  "questionId": 602,
                  "questionUuid": "COM_Q2",
                  "questionText": "Relationship to Index Case",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 2,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 6001, "optionLabel": "Neighbor", "optionValue": "NEIGHBOR", "displayOrder": 1, "conditions": [] },
                    { "optionId": 6002, "optionLabel": "Friend", "optionValue": "FRIEND", "displayOrder": 2, "conditions": [] },
                    { "optionId": 6003, "optionLabel": "Fellow worshipper (temple/mosque/church)", "optionValue": "FELLOW_WORSHIPPER", "displayOrder": 3, "conditions": [] },
                    { "optionId": 6004, "optionLabel": "Fellow commuter", "optionValue": "FELLOW_COMMUTER", "displayOrder": 4, "conditions": [] },
                    { "optionId": 6005, "optionLabel": "Community group member", "optionValue": "COMMUNITY_GROUP_MEMBER", "displayOrder": 5, "conditions": [] },
                    { "optionId": 6006, "optionLabel": "Fellow patient (clinic/hospital)", "optionValue": "FELLOW_PATIENT", "displayOrder": 6, "conditions": [] },
                    {
                      "optionId": 6007,
                      "optionLabel": "Other",
                      "optionValue": "OTHER",
                      "displayOrder": 7,
                      "conditions": [
                        { "conditionId": 601, "actionType": "SHOW_QUESTION", "targetQuestionId": 603, "targetQuestionUuid": "COM_Q3" }
                      ]
                    }
                  ],
                  "validations": []
                },
                {
                  "questionId": 603,
                  "questionUuid": "COM_Q3",
                  "questionText": "Other",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": false,
                  "displayOrder": 3,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "COM_Q2", "equals": "OTHER" },
                  "options": [],
                  "validations": []
                },
                {
                  "questionId": 604,
                  "questionUuid": "COM_Q4",
                  "questionText": "Age of Contact",
                  "questionTextHindi": null,
                  "questionType": "NUMBER",
                  "isMandatory": true,
                  "displayOrder": 4,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 62, "validationType": "RANGE", "validationParam": "0-99", "errorMessage": "Age must be between 0 and 99" }
                  ]
                },
                {
                  "questionId": 605,
                  "questionUuid": "COM_Q5",
                  "questionText": "Gender",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 5,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 6010, "optionLabel": "Male", "optionValue": "MALE", "displayOrder": 1, "conditions": [] },
                    { "optionId": 6011, "optionLabel": "Female", "optionValue": "FEMALE", "displayOrder": 2, "conditions": [] },
                    { "optionId": 6012, "optionLabel": "Transgender", "optionValue": "TRANSGENDER", "displayOrder": 3, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 606,
                  "questionUuid": "COM_Q6",
                  "questionText": "Mobile number not available",
                  "questionTextHindi": null,
                  "questionType": "CHECKBOX",
                  "allowMultiple": false,
                  "isMandatory": false,
                  "displayOrder": 6,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    {
                      "optionId": 6020,
                      "optionLabel": "Mobile number not available",
                      "optionValue": "TRUE",
                      "displayOrder": 1,
                      "conditions": [
                        { "conditionId": 602, "actionType": "DISABLE_QUESTION", "targetQuestionId": 607, "targetQuestionUuid": "COM_Q7" },
                        { "conditionId": 603, "actionType": "SET_DEFAULT_VALUE", "targetQuestionId": 607, "targetQuestionUuid": "COM_Q7", "value": "9999999999", "note": "Handled by default in the backend" }
                      ]
                    }
                  ],
                  "validations": []
                },
                {
                  "questionId": 607,
                  "questionUuid": "COM_Q7",
                  "questionText": "Mobile Number",
                  "questionTextHindi": null,
                  "questionType": "NUMBER",
                  "isMandatory": true,
                  "displayOrder": 7,
                  "maxLength": 10,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 63, "validationType": "EXACT_LENGTH", "validationParam": "10", "errorMessage": "Must be exactly 10 digits" }
                  ]
                },
                {
                  "questionId": 608,
                  "questionUuid": "COM_Q8",
                  "questionText": "Contact's address",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 8,
                  "maxLength": 200,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 64, "validationType": "MAX_LENGTH", "validationParam": "200", "errorMessage": "Must be 200 characters or fewer" },
                    { "validationId": 65, "validationType": "ALLOWED_CHARS", "validationParam": "ALPHANUMERIC_SYMBOLS", "errorMessage": "Alphanumeric characters and symbols only" }
                  ]
                },
                {
                  "questionId": 609,
                  "questionUuid": "COM_Q9",
                  "questionText": "Community setting of exposure",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": true,
                  "isMandatory": true,
                  "displayOrder": 9,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 6030, "optionLabel": "Place of worship", "optionValue": "PLACE_OF_WORSHIP", "displayOrder": 1, "conditions": [] },
                    { "optionId": 6031, "optionLabel": "Community hall", "optionValue": "COMMUNITY_HALL", "displayOrder": 2, "conditions": [] },
                    { "optionId": 6032, "optionLabel": "Market/bazaar", "optionValue": "MARKET_BAZAAR", "displayOrder": 3, "conditions": [] },
                    { "optionId": 6033, "optionLabel": "Public transport (bus/train/shared auto)", "optionValue": "PUBLIC_TRANSPORT", "displayOrder": 4, "conditions": [] },
                    { "optionId": 6034, "optionLabel": "Water collection point", "optionValue": "WATER_COLLECTION_POINT", "displayOrder": 5, "conditions": [] },
                    { "optionId": 6035, "optionLabel": "Community health/ASHA meeting", "optionValue": "ASHA_MEETING", "displayOrder": 6, "conditions": [] },
                    { "optionId": 6036, "optionLabel": "De-addiction centre", "optionValue": "DE_ADDICTION_CENTRE", "displayOrder": 7, "conditions": [] },
                    { "optionId": 6037, "optionLabel": "Shelter home", "optionValue": "SHELTER_HOME", "displayOrder": 8, "conditions": [] },
                    { "optionId": 6038, "optionLabel": "Prison/correctional facility", "optionValue": "PRISON_CORRECTIONAL", "displayOrder": 9, "conditions": [] },
                    {
                      "optionId": 6039,
                      "optionLabel": "Other",
                      "optionValue": "OTHER",
                      "displayOrder": 10,
                      "conditions": [
                        { "conditionId": 604, "actionType": "SHOW_QUESTION", "targetQuestionId": 610, "targetQuestionUuid": "COM_Q10" }
                      ]
                    }
                  ],
                  "validations": []
                },
                {
                  "questionId": 610,
                  "questionUuid": "COM_Q10",
                  "questionText": "Other",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 10,
                  "maxLength": 100,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "COM_Q9", "containsValue": "OTHER" },
                  "options": [],
                  "validations": [
                    { "validationId": 66, "validationType": "MAX_LENGTH", "validationParam": "100", "errorMessage": "Must be 100 characters or fewer" }
                  ]
                },
                {
                  "questionId": 611,
                  "questionUuid": "COM_Q11",
                  "questionText": "Estimated duration of regular contact per day",
                  "questionTextHindi": null,
                  "questionType": "NUMBER",
                  "isMandatory": true,
                  "displayOrder": 11,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "unit": "HOURS",
                  "options": [],
                  "validations": []
                },
                {
                  "questionId": 612,
                  "questionUuid": "COM_Q12",
                  "questionText": "Does contact have any TB symptoms?",
                  "questionTextHindi": null,
                  "questionType": "CHECKBOX",
                  "allowMultiple": true,
                  "isMandatory": true,
                  "displayOrder": 12,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 6040, "optionLabel": "Coughing > 2 weeks", "optionValue": "COUGH_GT_2W", "displayOrder": 1, "conditions": [ { "conditionId": 605, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 606, "actionType": "DISABLE_QUESTION", "targetQuestionId": 617, "targetQuestionUuid": "COM_Q17", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 6041, "optionLabel": "Blood in Sputum", "optionValue": "BLOOD_SPUTUM", "displayOrder": 2, "conditions": [ { "conditionId": 607, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 608, "actionType": "DISABLE_QUESTION", "targetQuestionId": 617, "targetQuestionUuid": "COM_Q17", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 6042, "optionLabel": "Fever > 2 weeks", "optionValue": "FEVER_GT_2W", "displayOrder": 3, "conditions": [ { "conditionId": 609, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 610, "actionType": "DISABLE_QUESTION", "targetQuestionId": 617, "targetQuestionUuid": "COM_Q17", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 6043, "optionLabel": "Rise of fever in evening", "optionValue": "EVENING_FEVER", "displayOrder": 4, "conditions": [ { "conditionId": 611, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 612, "actionType": "DISABLE_QUESTION", "targetQuestionId": 617, "targetQuestionUuid": "COM_Q17", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 6044, "optionLabel": "Loss of Appetite", "optionValue": "LOSS_APPETITE", "displayOrder": 5, "conditions": [ { "conditionId": 613, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 614, "actionType": "DISABLE_QUESTION", "targetQuestionId": 617, "targetQuestionUuid": "COM_Q17", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 6045, "optionLabel": "Loss of Weight", "optionValue": "LOSS_WEIGHT", "displayOrder": 6, "conditions": [ { "conditionId": 615, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 616, "actionType": "DISABLE_QUESTION", "targetQuestionId": 617, "targetQuestionUuid": "COM_Q17", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 6046, "optionLabel": "Night Sweats", "optionValue": "NIGHT_SWEATS", "displayOrder": 7, "conditions": [ { "conditionId": 617, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 618, "actionType": "DISABLE_QUESTION", "targetQuestionId": 617, "targetQuestionUuid": "COM_Q17", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 6047, "optionLabel": "History of TB", "optionValue": "HISTORY_TB", "displayOrder": 8, "conditions": [ { "conditionId": 619, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 620, "actionType": "DISABLE_QUESTION", "targetQuestionId": 617, "targetQuestionUuid": "COM_Q17", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 6048, "optionLabel": "Currently taking Anti-TB drugs", "optionValue": "ON_ANTI_TB_DRUGS", "displayOrder": 9, "conditions": [ { "conditionId": 621, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 622, "actionType": "DISABLE_QUESTION", "targetQuestionId": 617, "targetQuestionUuid": "COM_Q17", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 6049, "optionLabel": "None", "optionValue": "NONE", "displayOrder": 10, "conditions": [], "isExclusive": true }
                  ],
                  "validations": []
                },
                {
                  "questionId": 613,
                  "questionUuid": "COM_Q13",
                  "questionText": "Referred for TB screening",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": true,
                  "displayOrder": 13,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 6050, "optionLabel": "Yes", "optionValue": "YES", "displayOrder": 1, "conditions": [ { "conditionId": 623, "actionType": "SHOW_QUESTION", "targetQuestionId": 614, "targetQuestionUuid": "COM_Q14" } ] },
                    { "optionId": 6051, "optionLabel": "No", "optionValue": "NO", "displayOrder": 2, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 614,
                  "questionUuid": "COM_Q14",
                  "questionText": "Referral facility for screening",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 14,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "COM_Q13", "equals": "YES" },
                  "note": "Option to go for direct submission as all next fields of screening and TPT initiation may require a few days.",
                  "options": [
                    { "optionId": 6060, "optionLabel": "TB Screening Camp", "optionValue": "TB_SCREENING_CAMP", "displayOrder": 1, "conditions": [] },
                    { "optionId": 6061, "optionLabel": "Medical College", "optionValue": "MEDICAL_COLLEGE", "displayOrder": 2, "conditions": [] },
                    { "optionId": 6062, "optionLabel": "HWC", "optionValue": "HWC", "displayOrder": 3, "conditions": [] },
                    { "optionId": 6063, "optionLabel": "PHC", "optionValue": "PHC", "displayOrder": 4, "conditions": [] },
                    { "optionId": 6064, "optionLabel": "CHC", "optionValue": "CHC", "displayOrder": 5, "conditions": [] },
                    { "optionId": 6065, "optionLabel": "District Hospital", "optionValue": "DISTRICT_HOSPITAL", "displayOrder": 6, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 615,
                  "questionUuid": "COM_Q15",
                  "questionText": "Screening done at referral facility",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": false,
                  "displayOrder": 15,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "editablePostSubmission": true,
                  "options": [
                    { "optionId": 6070, "optionLabel": "Yes", "optionValue": "YES", "displayOrder": 1, "conditions": [ { "conditionId": 624, "actionType": "SHOW_QUESTION", "targetQuestionId": 616, "targetQuestionUuid": "COM_Q16" } ] },
                    { "optionId": 6071, "optionLabel": "No", "optionValue": "NO", "displayOrder": 2, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 616,
                  "questionUuid": "COM_Q16",
                  "questionText": "Screening result",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": false,
                  "displayOrder": 16,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "COM_Q15", "equals": "YES" },
                  "options": [
                    { "optionId": 6080, "optionLabel": "TB confirmed", "optionValue": "TB_CONFIRMED", "displayOrder": 1, "conditions": [ { "conditionId": 625, "actionType": "MOVE_CARD_TO_LIST", "targetList": "CONFIRMED_TB_LIST" }, { "conditionId": 626, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 6081, "optionLabel": "No TB", "optionValue": "NO_TB", "displayOrder": 2, "conditions": [ { "conditionId": 627, "actionType": "ENABLE_QUESTION", "targetQuestionId": 617, "targetQuestionUuid": "COM_Q17" } ] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 617,
                  "questionUuid": "COM_Q17",
                  "questionText": "Advised to take TPT",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": true,
                  "displayOrder": 17,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "COM_Q16", "equals": "NO_TB" },
                  "options": [
                    { "optionId": 6090, "optionLabel": "Yes", "optionValue": "YES", "displayOrder": 1, "conditions": [ { "conditionId": 628, "actionType": "SHOW_QUESTION", "targetQuestionId": 618, "targetQuestionUuid": "COM_Q18" } ] },
                    { "optionId": 6091, "optionLabel": "No", "optionValue": "NO", "displayOrder": 2, "conditions": [ { "conditionId": 629, "actionType": "GO_TO_SUBMIT" } ] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 618,
                  "questionUuid": "COM_Q18",
                  "questionText": "Contact eligible for TPT?",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 18,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "COM_Q17", "equals": "YES" },
                  "options": [
                    { "optionId": 6100, "optionLabel": "Child under 5 years - HHC of pulmonary TB case", "optionValue": "CHILD_UNDER_5", "displayOrder": 1, "conditions": [ { "conditionId": 630, "actionType": "ENABLE_QUESTION", "targetQuestionId": 619, "targetQuestionUuid": "COM_Q19" } ] },
                    { "optionId": 6101, "optionLabel": "Child 5-14 years - HHC of pulmonary TB case", "optionValue": "CHILD_5_14", "displayOrder": 2, "conditions": [ { "conditionId": 631, "actionType": "ENABLE_QUESTION", "targetQuestionId": 619, "targetQuestionUuid": "COM_Q19" } ] },
                    { "optionId": 6102, "optionLabel": "Person 15 years and above - HHC of pulmonary TB case", "optionValue": "PERSON_15_PLUS", "displayOrder": 3, "conditions": [ { "conditionId": 632, "actionType": "ENABLE_QUESTION", "targetQuestionId": 619, "targetQuestionUuid": "COM_Q19" } ] },
                    { "optionId": 6103, "optionLabel": "HIV positive", "optionValue": "HIV_POSITIVE", "displayOrder": 4, "conditions": [ { "conditionId": 633, "actionType": "ENABLE_QUESTION", "targetQuestionId": 619, "targetQuestionUuid": "COM_Q19" } ] },
                    { "optionId": 6104, "optionLabel": "Person living in institutional setting (prison, shelter home, de-addiction centre)", "optionValue": "INSTITUTIONAL_SETTING", "displayOrder": 5, "conditions": [ { "conditionId": 634, "actionType": "ENABLE_QUESTION", "targetQuestionId": 619, "targetQuestionUuid": "COM_Q19" } ] },
                    { "optionId": 6105, "optionLabel": "Already on anti-TB treatment (active TB confirmed)", "optionValue": "ON_ANTI_TB_TREATMENT", "displayOrder": 6, "conditions": [ { "conditionId": 635, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 636, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 6106, "optionLabel": "Completed TB treatment in the past (previous history of TB)", "optionValue": "COMPLETED_TB_TREATMENT_PAST", "displayOrder": 7, "conditions": [ { "conditionId": 637, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 638, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 6107, "optionLabel": "Already completed a course of TPT", "optionValue": "COMPLETED_TPT_COURSE", "displayOrder": 8, "conditions": [ { "conditionId": 639, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 640, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 6108, "optionLabel": "Active TB not yet ruled out - screening pending", "optionValue": "TB_NOT_RULED_OUT", "displayOrder": 9, "conditions": [ { "conditionId": 641, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 642, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 6109, "optionLabel": "Contraindication to TPT drugs (liver disease, drug allergy)", "optionValue": "CONTRAINDICATION_TPT", "displayOrder": 10, "conditions": [ { "conditionId": 643, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 644, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 6110, "optionLabel": "Active TB ruled out but clinical review needed before initiating (abnormalities detected)", "optionValue": "CLINICAL_REVIEW_NEEDED", "displayOrder": 11, "conditions": [ { "conditionId": 645, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 646, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 6111, "optionLabel": "Insufficient information to determine eligibility", "optionValue": "INSUFFICIENT_INFO", "displayOrder": 12, "conditions": [ { "conditionId": 647, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 648, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] }
                  ],
                  "note": "If option 6-12 selected, do not enable any TPT-related fields; provide option for direct submission.",
                  "validations": []
                },
                {
                  "questionId": 619,
                  "questionUuid": "COM_Q19",
                  "questionText": "TPT start date",
                  "questionTextHindi": null,
                  "questionType": "DATE",
                  "isMandatory": true,
                  "displayOrder": 19,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "COM_Q18", "inValues": ["CHILD_UNDER_5", "CHILD_5_14", "PERSON_15_PLUS", "HIV_POSITIVE", "INSTITUTIONAL_SETTING"] },
                  "options": [],
                  "validations": [
                    { "validationId": 67, "validationType": "DATE_NOT_FUTURE", "validationParam": "TODAY", "errorMessage": "Cannot be a future date" },
                    { "validationId": 68, "validationType": "DATE_NOT_BEFORE", "validationParam": "CONTACT_SCREENING_DATE", "errorMessage": "Cannot be before date of screening (contact screening)" }
                  ]
                },
                {
                  "questionId": 620,
                  "questionUuid": "COM_Q20",
                  "questionText": "TPT Regimen advised",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": true,
                  "displayOrder": 20,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "COM_Q19", "isNotEmpty": true },
                  "options": [
                    { "optionId": 6120, "optionLabel": "6H — Isoniazid daily for 6 months", "optionValue": "6H", "displayOrder": 1, "conditions": [] },
                    { "optionId": 6121, "optionLabel": "3HP — Isoniazid + Rifapentine weekly for 3 months", "optionValue": "3HP", "displayOrder": 2, "conditions": [] },
                    { "optionId": 6122, "optionLabel": "3HR — Isoniazid + Rifampicin daily for 3 months", "optionValue": "3HR", "displayOrder": 3, "conditions": [] },
                    { "optionId": 6123, "optionLabel": "1HP — Isoniazid + Rifapentine daily for 1 month", "optionValue": "1HP", "displayOrder": 4, "conditions": [] },
                    { "optionId": 6124, "optionLabel": "6H (Modified dose) — Isoniazid daily for 6 months with dose adjustment for weight or age", "optionValue": "6H_MODIFIED", "displayOrder": 5, "conditions": [] }
                  ],
                  "validations": []
                }
              ]
            },
            {
              "sectionId": 61,
              "sectionUuid": "COM_SEC_B",
              "sectionName": "Follow-up and Outcome",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": false,
              "displayOrder": 2,
              "hasSubmitButton": false,
              "questions": [
                {
                  "questionId": 621,
                  "questionUuid": "COM_Q21",
                  "questionText": "Follow-up visit number",
                  "questionTextHindi": null,
                  "questionType": "READONLY_NUMBER",
                  "isMandatory": false,
                  "displayOrder": 1,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "autoPopulated": true,
                  "autoPopulateLogic": "Auto-increment 1-6, automatically calculated by system monthly",
                  "options": [],
                  "validations": []
                },
                {
                  "questionId": 622,
                  "questionUuid": "COM_Q22",
                  "questionText": "Follow-up visit date",
                  "questionTextHindi": null,
                  "questionType": "DATE",
                  "isMandatory": true,
                  "displayOrder": 2,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 69, "validationType": "DATE_NOT_FUTURE", "validationParam": "TODAY", "errorMessage": "Cannot be a future date" },
                    { "validationId": 70, "validationType": "DATE_NOT_BEFORE", "validationParam": "COM_Q19", "errorMessage": "Cannot be before TPT start date" }
                  ]
                },
                {
                  "questionId": 623,
                  "questionUuid": "COM_Q23",
                  "questionText": "TPT outcome status",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 3,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 6130, "optionLabel": "Completed", "optionValue": "COMPLETED", "displayOrder": 1, "conditions": [ { "conditionId": 649, "actionType": "ENABLE_QUESTION", "targetQuestionId": 625, "targetQuestionUuid": "COM_Q25" } ] },
                    { "optionId": 6131, "optionLabel": "Lost to follow-up", "optionValue": "LOST_TO_FOLLOWUP", "displayOrder": 2, "conditions": [] },
                    { "optionId": 6132, "optionLabel": "Developed active TB during TPT", "optionValue": "DEVELOPED_ACTIVE_TB", "displayOrder": 3, "conditions": [ { "conditionId": 650, "actionType": "REDIRECT_TO_FORM", "targetFormUuid": "TB_PRESUMPTIVE_CASE", "note": "Redirects volunteer to create new TB Presumptive Case record and closes this TPT case" } ] },
                    { "optionId": 6133, "optionLabel": "Died during TPT", "optionValue": "DIED_DURING_TPT", "displayOrder": 4, "conditions": [ { "conditionId": 651, "actionType": "ENABLE_QUESTION", "targetQuestionId": 625, "targetQuestionUuid": "COM_Q25" }, { "conditionId": 652, "actionType": "ENABLE_QUESTION", "targetQuestionId": 626, "targetQuestionUuid": "COM_Q26" } ] },
                    { "optionId": 6134, "optionLabel": "Other", "optionValue": "OTHER", "displayOrder": 5, "conditions": [ { "conditionId": 653, "actionType": "ENABLE_QUESTION", "targetQuestionId": 624, "targetQuestionUuid": "COM_Q24" } ] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 624,
                  "questionUuid": "COM_Q24",
                  "questionText": "Other",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": false,
                  "displayOrder": 4,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "COM_Q23", "equals": "OTHER" },
                  "options": [],
                  "validations": []
                },
                {
                  "questionId": 625,
                  "questionUuid": "COM_Q25",
                  "questionText": "Date of TPT outcome",
                  "questionTextHindi": null,
                  "questionType": "DATE",
                  "isMandatory": true,
                  "displayOrder": 5,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "COM_Q23", "inValues": ["COMPLETED", "DIED_DURING_TPT"] },
                  "disabledIf": { "questionUuid": "COM_Q18", "inValues": ["ON_ANTI_TB_TREATMENT", "COMPLETED_TB_TREATMENT_PAST", "COMPLETED_TPT_COURSE", "TB_NOT_RULED_OUT", "CONTRAINDICATION_TPT", "CLINICAL_REVIEW_NEEDED", "INSUFFICIENT_INFO"] },
                  "options": [],
                  "validations": [
                    { "validationId": 71, "validationType": "DATE_NOT_FUTURE", "validationParam": "TODAY", "errorMessage": "Cannot be a future date" }
                  ]
                },
                {
                  "questionId": 626,
                  "questionUuid": "COM_Q26",
                  "questionText": "Cause of death",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 6,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "COM_Q23", "equals": "DIED_DURING_TPT" },
                  "options": [],
                  "validations": []
                }
              ]
            },
            {
              "sectionId": 62,
              "sectionUuid": "COM_SEC_C",
              "sectionName": "Location & Metadata",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": true,
              "displayOrder": 3,
              "hasSubmitButton": false,
              "questions": [
                { "questionId": 627, "questionUuid": "COM_Q27", "questionText": "Latitude", "questionType": "READONLY_NUMBER", "isMandatory": false, "displayOrder": 1, "containsPii": true, "visibleByDefault": true, "autoPopulated": true, "options": [], "validations": [ { "validationId": 72, "validationType": "FORMAT", "validationParam": "DECIMAL_DEGREES", "errorMessage": "e.g. 21.1458" } ] },
                { "questionId": 628, "questionUuid": "COM_Q28", "questionText": "Longitude", "questionType": "READONLY_NUMBER", "isMandatory": false, "displayOrder": 2, "containsPii": true, "visibleByDefault": true, "autoPopulated": true, "options": [], "validations": [ { "validationId": 73, "validationType": "FORMAT", "validationParam": "DECIMAL_DEGREES", "errorMessage": "e.g. 21.1458" } ] },
                { "questionId": 629, "questionUuid": "COM_Q29", "questionText": "DigiPin", "questionType": "READONLY_TEXT", "isMandatory": false, "displayOrder": 3, "containsPii": true, "visibleByDefault": true, "autoPopulated": true, "autoPopulateLogic": "Auto-generated from Lat/Long using DIGIPIN encoding algorithm (open-source, offline-capable)", "displayFormat": "XXXX-XXXX-XX", "options": [], "validations": [] },
                { "questionId": 630, "questionUuid": "COM_Q30", "questionText": "Timestamp", "questionType": "READONLY_TEXT", "isMandatory": false, "displayOrder": 4, "containsPii": false, "visibleByDefault": true, "autoPopulated": true, "autoPopulateLogic": "System-captured date and time", "options": [], "validations": [] }
              ]
            },
            {
              "sectionId": 63,
              "sectionUuid": "COM_SEC_D",
              "sectionName": "Additional Information",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": false,
              "displayOrder": 4,
              "hasSubmitButton": true,
              "questions": [
                {
                  "questionId": 631,
                  "questionUuid": "COM_REMARKS",
                  "questionText": "Any other significant Information",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": false,
                  "displayOrder": 1,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 74, "validationType": "MIN_MAX_WORDS", "validationParam": "250-500", "errorMessage": "Please provide between 250 and 500 words" }
                  ]
                }
              ]
            }
          ]
        },

        {
          "formId": 7,
          "formUuid": "OCCUPATIONAL_CONTACT_TRACING",
          "formName": "Occupational Contact Tracing Form",
          "formType": "CONTACT_TRACING_OCCUPATIONAL",
          "isActive": true,
          "followUpDelayDays": 30,
          "versionNumber": 1,
          "definition": "People who share a workplace, worksite, or occupational setting with the index case for significant duration.",
          "enabledIf": { "sourceFormUuid": "CONTACT_TRACING_SELECTOR", "questionUuid": "CTS_Q5", "containsValue": "OCCUPATIONAL" },
          "sections": [
            {
              "sectionId": 70,
              "sectionUuid": "OCC_SEC_A",
              "sectionName": "Contact Details",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": true,
              "displayOrder": 1,
              "hasSubmitButton": false,
              "questions": [
                {
                  "questionId": 701,
                  "questionUuid": "OCC_Q1",
                  "questionText": "Name of Contact",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 1,
                  "maxLength": 100,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 75, "validationType": "MAX_LENGTH", "validationParam": "100", "errorMessage": "Must be 100 characters or fewer" },
                    { "validationId": 76, "validationType": "FORCE_UPPERCASE", "validationParam": null, "errorMessage": null }
                  ]
                },
                {
                  "questionId": 702,
                  "questionUuid": "OCC_Q2",
                  "questionText": "Age of Contact",
                  "questionTextHindi": null,
                  "questionType": "NUMBER",
                  "isMandatory": true,
                  "displayOrder": 2,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 77, "validationType": "RANGE", "validationParam": "0-99", "errorMessage": "Age must be between 0 and 99" }
                  ]
                },
                {
                  "questionId": 703,
                  "questionUuid": "OCC_Q3",
                  "questionText": "Gender",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 3,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 7001, "optionLabel": "Male", "optionValue": "MALE", "displayOrder": 1, "conditions": [] },
                    { "optionId": 7002, "optionLabel": "Female", "optionValue": "FEMALE", "displayOrder": 2, "conditions": [] },
                    { "optionId": 7003, "optionLabel": "Transgender", "optionValue": "TRANSGENDER", "displayOrder": 3, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 704,
                  "questionUuid": "OCC_Q4",
                  "questionText": "Mobile number not available",
                  "questionTextHindi": null,
                  "questionType": "CHECKBOX",
                  "allowMultiple": false,
                  "isMandatory": false,
                  "displayOrder": 4,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    {
                      "optionId": 7010,
                      "optionLabel": "Mobile number not available",
                      "optionValue": "TRUE",
                      "displayOrder": 1,
                      "conditions": [
                        { "conditionId": 701, "actionType": "DISABLE_QUESTION", "targetQuestionId": 705, "targetQuestionUuid": "OCC_Q5" },
                        { "conditionId": 702, "actionType": "SET_DEFAULT_VALUE", "targetQuestionId": 705, "targetQuestionUuid": "OCC_Q5", "value": "9999999999", "note": "Handled by default in the backend" }
                      ]
                    }
                  ],
                  "validations": []
                },
                {
                  "questionId": 705,
                  "questionUuid": "OCC_Q5",
                  "questionText": "Mobile Number",
                  "questionTextHindi": null,
                  "questionType": "NUMBER",
                  "isMandatory": true,
                  "displayOrder": 5,
                  "maxLength": 10,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 78, "validationType": "EXACT_LENGTH", "validationParam": "10", "errorMessage": "Must be exactly 10 digits" }
                  ]
                },
                {
                  "questionId": 706,
                  "questionUuid": "OCC_Q6",
                  "questionText": "Contact's address",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 6,
                  "maxLength": 200,
                  "defaultValue": null,
                  "containsPii": true,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 79, "validationType": "MAX_LENGTH", "validationParam": "200", "errorMessage": "Must be 200 characters or fewer" }
                  ]
                },
                {
                  "questionId": 707,
                  "questionUuid": "OCC_Q7",
                  "questionText": "Name of workplace / worksite / Institute",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 7,
                  "maxLength": 200,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "autoPopulateNote": "Can be auto-populated from 'Name and Address of employment' or 'Name and Address of Institution' captured in the Contact Tracing Type Selector section.",
                  "exampleValues": ["Rampur Brick Kiln Unit-3", "Bastar Tea Estate Block B"],
                  "options": [],
                  "validations": [
                    { "validationId": 80, "validationType": "MAX_LENGTH", "validationParam": "200", "errorMessage": "Must be 200 characters or fewer" },
                    { "validationId": 81, "validationType": "ALLOWED_CHARS", "validationParam": "ALPHANUMERIC_SYMBOLS", "errorMessage": "Alphanumeric characters and symbols only" }
                  ]
                },
                {
                  "questionId": 708,
                  "questionUuid": "OCC_Q8",
                  "questionText": "Type",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 8,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 7020, "optionLabel": "Agricultural field", "optionValue": "AGRICULTURAL_FIELD", "displayOrder": 1, "conditions": [] },
                    { "optionId": 7021, "optionLabel": "Tea garden", "optionValue": "TEA_GARDEN", "displayOrder": 2, "conditions": [] },
                    { "optionId": 7022, "optionLabel": "Brick kiln", "optionValue": "BRICK_KILN", "displayOrder": 3, "conditions": [] },
                    { "optionId": 7023, "optionLabel": "Mines", "optionValue": "MINES", "displayOrder": 4, "conditions": [] },
                    { "optionId": 7024, "optionLabel": "Construction site", "optionValue": "CONSTRUCTION_SITE", "displayOrder": 5, "conditions": [] },
                    { "optionId": 7025, "optionLabel": "Factories", "optionValue": "FACTORIES", "displayOrder": 6, "conditions": [] },
                    { "optionId": 7026, "optionLabel": "Healthcare facility", "optionValue": "HEALTHCARE_FACILITY", "displayOrder": 7, "conditions": [] },
                    { "optionId": 7027, "optionLabel": "Educational Institute", "optionValue": "EDUCATIONAL_INSTITUTE", "displayOrder": 8, "conditions": [] },
                    { "optionId": 7028, "optionLabel": "Market (Shops)", "optionValue": "MARKET_SHOPS", "displayOrder": 9, "conditions": [] },
                    { "optionId": 7029, "optionLabel": "Office", "optionValue": "OFFICE", "displayOrder": 10, "conditions": [] },
                    {
                      "optionId": 7030,
                      "optionLabel": "Other",
                      "optionValue": "OTHER",
                      "displayOrder": 11,
                      "conditions": [
                        { "conditionId": 703, "actionType": "SHOW_QUESTION", "targetQuestionId": 709, "targetQuestionUuid": "OCC_Q9" }
                      ]
                    }
                  ],
                  "validations": []
                },
                {
                  "questionId": 709,
                  "questionUuid": "OCC_Q9",
                  "questionText": "Other",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 9,
                  "maxLength": 100,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "OCC_Q8", "equals": "OTHER" },
                  "options": [],
                  "validations": [
                    { "validationId": 82, "validationType": "MAX_LENGTH", "validationParam": "100", "errorMessage": "Must be 100 characters or fewer" }
                  ]
                },
                {
                  "questionId": 710,
                  "questionUuid": "OCC_Q10",
                  "questionText": "Type of space",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": false,
                  "displayOrder": 10,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 7040, "optionLabel": "Open space", "optionValue": "OPEN_SPACE", "displayOrder": 1, "conditions": [] },
                    { "optionId": 7041, "optionLabel": "Closed space with apt ventilation", "optionValue": "CLOSED_VENTILATED", "displayOrder": 2, "conditions": [] },
                    { "optionId": 7042, "optionLabel": "Closed space with no ventilation", "optionValue": "CLOSED_NOT_VENTILATED", "displayOrder": 3, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 711,
                  "questionUuid": "OCC_Q11",
                  "questionText": "Relationship to Index Case at Workplace",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 11,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 7050, "optionLabel": "Co-worker (same team/unit)", "optionValue": "CO_WORKER", "displayOrder": 1, "conditions": [] },
                    { "optionId": 7051, "optionLabel": "Supervisor", "optionValue": "SUPERVISOR", "displayOrder": 2, "conditions": [] },
                    { "optionId": 7052, "optionLabel": "Subordinate", "optionValue": "SUBORDINATE", "displayOrder": 3, "conditions": [] },
                    { "optionId": 7053, "optionLabel": "Employer", "optionValue": "EMPLOYER", "displayOrder": 4, "conditions": [] },
                    { "optionId": 7054, "optionLabel": "Other workplace associate", "optionValue": "OTHER_ASSOCIATE", "displayOrder": 5, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 712,
                  "questionUuid": "OCC_Q12",
                  "questionText": "Daily hours of shared work environment",
                  "questionTextHindi": null,
                  "questionType": "NUMBER",
                  "isMandatory": true,
                  "displayOrder": 12,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "unit": "HOURS",
                  "options": [],
                  "validations": [
                    { "validationId": 83, "validationType": "NUMERIC_ONLY", "validationParam": null, "errorMessage": "Only numerical values allowed" }
                  ]
                },
                {
                  "questionId": 713,
                  "questionUuid": "OCC_Q13",
                  "questionText": "Does contact have any TB symptoms?",
                  "questionTextHindi": null,
                  "questionType": "CHECKBOX",
                  "allowMultiple": true,
                  "isMandatory": true,
                  "displayOrder": 13,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 7060, "optionLabel": "Coughing > 2 weeks", "optionValue": "COUGH_GT_2W", "displayOrder": 1, "conditions": [ { "conditionId": 704, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 705, "actionType": "DISABLE_QUESTION", "targetQuestionId": 718, "targetQuestionUuid": "OCC_Q18", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 7061, "optionLabel": "Blood in Sputum", "optionValue": "BLOOD_SPUTUM", "displayOrder": 2, "conditions": [ { "conditionId": 706, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 707, "actionType": "DISABLE_QUESTION", "targetQuestionId": 718, "targetQuestionUuid": "OCC_Q18", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 7062, "optionLabel": "Fever > 2 weeks", "optionValue": "FEVER_GT_2W", "displayOrder": 3, "conditions": [ { "conditionId": 708, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 709, "actionType": "DISABLE_QUESTION", "targetQuestionId": 718, "targetQuestionUuid": "OCC_Q18", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 7063, "optionLabel": "Rise of fever in evening", "optionValue": "EVENING_FEVER", "displayOrder": 4, "conditions": [ { "conditionId": 710, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 711, "actionType": "DISABLE_QUESTION", "targetQuestionId": 718, "targetQuestionUuid": "OCC_Q18", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 7064, "optionLabel": "Loss of Appetite", "optionValue": "LOSS_APPETITE", "displayOrder": 5, "conditions": [ { "conditionId": 712, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 713, "actionType": "DISABLE_QUESTION", "targetQuestionId": 718, "targetQuestionUuid": "OCC_Q18", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 7065, "optionLabel": "Loss of Weight", "optionValue": "LOSS_WEIGHT", "displayOrder": 6, "conditions": [ { "conditionId": 714, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 715, "actionType": "DISABLE_QUESTION", "targetQuestionId": 718, "targetQuestionUuid": "OCC_Q18", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 7066, "optionLabel": "Night Sweats", "optionValue": "NIGHT_SWEATS", "displayOrder": 7, "conditions": [ { "conditionId": 716, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 717, "actionType": "DISABLE_QUESTION", "targetQuestionId": 718, "targetQuestionUuid": "OCC_Q18", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 7067, "optionLabel": "History of TB", "optionValue": "HISTORY_TB", "displayOrder": 8, "conditions": [ { "conditionId": 718, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 719, "actionType": "DISABLE_QUESTION", "targetQuestionId": 718, "targetQuestionUuid": "OCC_Q18", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 7068, "optionLabel": "Currently taking Anti-TB drugs", "optionValue": "ON_ANTI_TB_DRUGS", "displayOrder": 9, "conditions": [ { "conditionId": 720, "actionType": "GENERATE_ALERT", "alertMessage": "Do not initiate TPT. Refer for active TB ruling before treatment." }, { "conditionId": 721, "actionType": "DISABLE_QUESTION", "targetQuestionId": 718, "targetQuestionUuid": "OCC_Q18", "reEnableCondition": "Screening result = No TB" } ] },
                    { "optionId": 7069, "optionLabel": "None", "optionValue": "NONE", "displayOrder": 10, "conditions": [], "isExclusive": true }
                  ],
                  "validations": []
                },
                {
                  "questionId": 714,
                  "questionUuid": "OCC_Q14",
                  "questionText": "Referred for TB screening",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": true,
                  "displayOrder": 14,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 7070, "optionLabel": "Yes", "optionValue": "YES", "displayOrder": 1, "conditions": [ { "conditionId": 722, "actionType": "SHOW_QUESTION", "targetQuestionId": 715, "targetQuestionUuid": "OCC_Q15" } ] },
                    { "optionId": 7071, "optionLabel": "No", "optionValue": "NO", "displayOrder": 2, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 715,
                  "questionUuid": "OCC_Q15",
                  "questionText": "Referral facility for screening",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 15,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "OCC_Q14", "equals": "YES" },
                  "note": "Option to go for direct submission as all next fields of screening and TPT initiation may require a few days.",
                  "options": [
                    { "optionId": 7080, "optionLabel": "TB Screening Camp", "optionValue": "TB_SCREENING_CAMP", "displayOrder": 1, "conditions": [] },
                    { "optionId": 7081, "optionLabel": "Medical College", "optionValue": "MEDICAL_COLLEGE", "displayOrder": 2, "conditions": [] },
                    { "optionId": 7082, "optionLabel": "HWC", "optionValue": "HWC", "displayOrder": 3, "conditions": [] },
                    { "optionId": 7083, "optionLabel": "PHC", "optionValue": "PHC", "displayOrder": 4, "conditions": [] },
                    { "optionId": 7084, "optionLabel": "CHC", "optionValue": "CHC", "displayOrder": 5, "conditions": [] },
                    { "optionId": 7085, "optionLabel": "District Hospital", "optionValue": "DISTRICT_HOSPITAL", "displayOrder": 6, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 716,
                  "questionUuid": "OCC_Q16",
                  "questionText": "Screening done at referral facility",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": false,
                  "displayOrder": 16,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "editablePostSubmission": true,
                  "options": [
                    { "optionId": 7090, "optionLabel": "Yes", "optionValue": "YES", "displayOrder": 1, "conditions": [ { "conditionId": 723, "actionType": "SHOW_QUESTION", "targetQuestionId": 717, "targetQuestionUuid": "OCC_Q17" } ] },
                    { "optionId": 7091, "optionLabel": "No", "optionValue": "NO", "displayOrder": 2, "conditions": [] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 717,
                  "questionUuid": "OCC_Q17",
                  "questionText": "Screening result",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": false,
                  "displayOrder": 17,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "OCC_Q16", "equals": "YES" },
                  "options": [
                    { "optionId": 7100, "optionLabel": "TB confirmed", "optionValue": "TB_CONFIRMED", "displayOrder": 1, "conditions": [ { "conditionId": 724, "actionType": "MOVE_CARD_TO_LIST", "targetList": "CONFIRMED_TB_LIST" }, { "conditionId": 725, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 7101, "optionLabel": "No TB", "optionValue": "NO_TB", "displayOrder": 2, "conditions": [ { "conditionId": 726, "actionType": "ENABLE_QUESTION", "targetQuestionId": 718, "targetQuestionUuid": "OCC_Q18" } ] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 718,
                  "questionUuid": "OCC_Q18",
                  "questionText": "Advised to take TPT",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": true,
                  "displayOrder": 18,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "OCC_Q17", "equals": "NO_TB" },
                  "options": [
                    { "optionId": 7110, "optionLabel": "Yes", "optionValue": "YES", "displayOrder": 1, "conditions": [ { "conditionId": 727, "actionType": "SHOW_QUESTION", "targetQuestionId": 719, "targetQuestionUuid": "OCC_Q19" } ] },
                    { "optionId": 7111, "optionLabel": "No", "optionValue": "NO", "displayOrder": 2, "conditions": [ { "conditionId": 728, "actionType": "GO_TO_SUBMIT" } ] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 719,
                  "questionUuid": "OCC_Q19",
                  "questionText": "Contact eligible for TPT?",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 19,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "OCC_Q18", "equals": "YES" },
                  "options": [
                    { "optionId": 7120, "optionLabel": "Child under 5 years - HHC of pulmonary TB case", "optionValue": "CHILD_UNDER_5", "displayOrder": 1, "conditions": [ { "conditionId": 729, "actionType": "ENABLE_QUESTION", "targetQuestionId": 720, "targetQuestionUuid": "OCC_Q20" } ] },
                    { "optionId": 7121, "optionLabel": "Child 5-14 years - HHC of pulmonary TB case", "optionValue": "CHILD_5_14", "displayOrder": 2, "conditions": [ { "conditionId": 730, "actionType": "ENABLE_QUESTION", "targetQuestionId": 720, "targetQuestionUuid": "OCC_Q20" } ] },
                    { "optionId": 7122, "optionLabel": "Person 15 years and above - HHC of pulmonary TB case", "optionValue": "PERSON_15_PLUS", "displayOrder": 3, "conditions": [ { "conditionId": 731, "actionType": "ENABLE_QUESTION", "targetQuestionId": 720, "targetQuestionUuid": "OCC_Q20" } ] },
                    { "optionId": 7123, "optionLabel": "HIV positive", "optionValue": "HIV_POSITIVE", "displayOrder": 4, "conditions": [ { "conditionId": 732, "actionType": "ENABLE_QUESTION", "targetQuestionId": 720, "targetQuestionUuid": "OCC_Q20" } ] },
                    { "optionId": 7124, "optionLabel": "Person living in institutional setting (prison, shelter home, de-addiction centre)", "optionValue": "INSTITUTIONAL_SETTING", "displayOrder": 5, "conditions": [ { "conditionId": 733, "actionType": "ENABLE_QUESTION", "targetQuestionId": 720, "targetQuestionUuid": "OCC_Q20" } ] },
                    { "optionId": 7125, "optionLabel": "Already on anti-TB treatment (active TB confirmed)", "optionValue": "ON_ANTI_TB_TREATMENT", "displayOrder": 6, "conditions": [ { "conditionId": 734, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 735, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 7126, "optionLabel": "Completed TB treatment in the past (previous history of TB)", "optionValue": "COMPLETED_TB_TREATMENT_PAST", "displayOrder": 7, "conditions": [ { "conditionId": 736, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 737, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 7127, "optionLabel": "Already completed a course of TPT", "optionValue": "COMPLETED_TPT_COURSE", "displayOrder": 8, "conditions": [ { "conditionId": 738, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 739, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 7128, "optionLabel": "Active TB not yet ruled out - screening pending", "optionValue": "TB_NOT_RULED_OUT", "displayOrder": 9, "conditions": [ { "conditionId": 740, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 741, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 7129, "optionLabel": "Contraindication to TPT drugs (liver disease, drug allergy)", "optionValue": "CONTRAINDICATION_TPT", "displayOrder": 10, "conditions": [ { "conditionId": 742, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 743, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 7130, "optionLabel": "Active TB ruled out but clinical review needed before initiating (abnormalities detected)", "optionValue": "CLINICAL_REVIEW_NEEDED", "displayOrder": 11, "conditions": [ { "conditionId": 744, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 745, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] },
                    { "optionId": 7131, "optionLabel": "Insufficient information to determine eligibility", "optionValue": "INSUFFICIENT_INFO", "displayOrder": 12, "conditions": [ { "conditionId": 746, "actionType": "DISABLE_ALL_TPT_FIELDS" }, { "conditionId": 747, "actionType": "SHOW_DIRECT_SUBMIT_OPTION" } ] }
                  ],
                  "note": "If option 6-12 selected, do not enable any TPT-related fields; provide option for direct submission.",
                  "validations": []
                },
                {
                  "questionId": 720,
                  "questionUuid": "OCC_Q20",
                  "questionText": "TPT start date",
                  "questionTextHindi": null,
                  "questionType": "DATE",
                  "isMandatory": true,
                  "displayOrder": 20,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "OCC_Q19", "inValues": ["CHILD_UNDER_5", "CHILD_5_14", "PERSON_15_PLUS", "HIV_POSITIVE", "INSTITUTIONAL_SETTING"] },
                  "options": [],
                  "validations": [
                    { "validationId": 84, "validationType": "DATE_NOT_FUTURE", "validationParam": "TODAY", "errorMessage": "Cannot be a future date" },
                    { "validationId": 85, "validationType": "DATE_NOT_BEFORE", "validationParam": "CONTACT_SCREENING_DATE", "errorMessage": "Cannot be before date of screening (contact screening)" }
                  ]
                },
                {
                  "questionId": 721,
                  "questionUuid": "OCC_Q21",
                  "questionText": "TPT Regimen advised",
                  "questionTextHindi": null,
                  "questionType": "RADIO",
                  "isMandatory": true,
                  "displayOrder": 21,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "OCC_Q20", "isNotEmpty": true },
                  "options": [
                    { "optionId": 7140, "optionLabel": "6H — Isoniazid daily for 6 months", "optionValue": "6H", "displayOrder": 1, "conditions": [] },
                    { "optionId": 7141, "optionLabel": "3HP — Isoniazid + Rifapentine weekly for 3 months", "optionValue": "3HP", "displayOrder": 2, "conditions": [] },
                    { "optionId": 7142, "optionLabel": "3HR — Isoniazid + Rifampicin daily for 3 months", "optionValue": "3HR", "displayOrder": 3, "conditions": [] },
                    { "optionId": 7143, "optionLabel": "1HP — Isoniazid + Rifapentine daily for 1 month", "optionValue": "1HP", "displayOrder": 4, "conditions": [] },
                    { "optionId": 7144, "optionLabel": "6H (Modified dose) — Isoniazid daily for 6 months with dose adjustment for weight or age", "optionValue": "6H_MODIFIED", "displayOrder": 5, "conditions": [] }
                  ],
                  "validations": []
                }
              ]
            },
            {
              "sectionId": 71,
              "sectionUuid": "OCC_SEC_B",
              "sectionName": "Follow-up and Outcome",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": false,
              "displayOrder": 2,
              "hasSubmitButton": false,
              "questions": [
                {
                  "questionId": 722,
                  "questionUuid": "OCC_Q22",
                  "questionText": "Follow-up visit number",
                  "questionTextHindi": null,
                  "questionType": "READONLY_NUMBER",
                  "isMandatory": false,
                  "displayOrder": 1,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "autoPopulated": true,
                  "autoPopulateLogic": "Auto-increment 1-6, automatically calculated by system monthly",
                  "options": [],
                  "validations": []
                },
                {
                  "questionId": 723,
                  "questionUuid": "OCC_Q23",
                  "questionText": "Follow-up visit date",
                  "questionTextHindi": null,
                  "questionType": "DATE",
                  "isMandatory": true,
                  "displayOrder": 2,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 86, "validationType": "DATE_NOT_FUTURE", "validationParam": "TODAY", "errorMessage": "Cannot be a future date" },
                    { "validationId": 87, "validationType": "DATE_NOT_BEFORE", "validationParam": "OCC_Q20", "errorMessage": "Cannot be before TPT start date" }
                  ]
                },
                {
                  "questionId": 724,
                  "questionUuid": "OCC_Q24",
                  "questionText": "TPT outcome status",
                  "questionTextHindi": null,
                  "questionType": "DROPDOWN",
                  "allowMultiple": false,
                  "isMandatory": true,
                  "displayOrder": 3,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [
                    { "optionId": 7150, "optionLabel": "Completed", "optionValue": "COMPLETED", "displayOrder": 1, "conditions": [ { "conditionId": 748, "actionType": "ENABLE_QUESTION", "targetQuestionId": 726, "targetQuestionUuid": "OCC_Q26" } ] },
                    { "optionId": 7151, "optionLabel": "Lost to follow-up", "optionValue": "LOST_TO_FOLLOWUP", "displayOrder": 2, "conditions": [] },
                    { "optionId": 7152, "optionLabel": "Developed active TB during TPT", "optionValue": "DEVELOPED_ACTIVE_TB", "displayOrder": 3, "conditions": [ { "conditionId": 749, "actionType": "REDIRECT_TO_FORM", "targetFormUuid": "TB_PRESUMPTIVE_CASE", "note": "Redirects volunteer to create new TB Presumptive Case record and closes this TPT case" } ] },
                    { "optionId": 7153, "optionLabel": "Died during TPT", "optionValue": "DIED_DURING_TPT", "displayOrder": 4, "conditions": [ { "conditionId": 750, "actionType": "ENABLE_QUESTION", "targetQuestionId": 726, "targetQuestionUuid": "OCC_Q26" }, { "conditionId": 751, "actionType": "ENABLE_QUESTION", "targetQuestionId": 727, "targetQuestionUuid": "OCC_Q27" } ] },
                    { "optionId": 7154, "optionLabel": "Other", "optionValue": "OTHER", "displayOrder": 5, "conditions": [ { "conditionId": 752, "actionType": "ENABLE_QUESTION", "targetQuestionId": 725, "targetQuestionUuid": "OCC_Q25" } ] }
                  ],
                  "validations": []
                },
                {
                  "questionId": 725,
                  "questionUuid": "OCC_Q25",
                  "questionText": "Other",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": false,
                  "displayOrder": 4,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "OCC_Q24", "equals": "OTHER" },
                  "options": [],
                  "validations": []
                },
                {
                  "questionId": 726,
                  "questionUuid": "OCC_Q26",
                  "questionText": "Date of TPT outcome",
                  "questionTextHindi": null,
                  "questionType": "DATE",
                  "isMandatory": true,
                  "displayOrder": 5,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "OCC_Q24", "inValues": ["COMPLETED", "DIED_DURING_TPT"] },
                  "disabledIf": { "questionUuid": "OCC_Q19", "inValues": ["ON_ANTI_TB_TREATMENT", "COMPLETED_TB_TREATMENT_PAST", "COMPLETED_TPT_COURSE", "TB_NOT_RULED_OUT", "CONTRAINDICATION_TPT", "CLINICAL_REVIEW_NEEDED", "INSUFFICIENT_INFO"] },
                  "options": [],
                  "validations": [
                    { "validationId": 88, "validationType": "DATE_NOT_FUTURE", "validationParam": "TODAY", "errorMessage": "Cannot be a future date" }
                  ]
                },
                {
                  "questionId": 727,
                  "questionUuid": "OCC_Q27",
                  "questionText": "Cause of death",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": true,
                  "displayOrder": 6,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": false,
                  "enabledIf": { "questionUuid": "OCC_Q24", "equals": "DIED_DURING_TPT" },
                  "options": [],
                  "validations": []
                }
              ]
            },
            {
              "sectionId": 72,
              "sectionUuid": "OCC_SEC_C",
              "sectionName": "Location & Metadata",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": true,
              "displayOrder": 3,
              "hasSubmitButton": false,
              "questions": [
                { "questionId": 728, "questionUuid": "OCC_Q28", "questionText": "Latitude", "questionType": "READONLY_NUMBER", "isMandatory": false, "displayOrder": 1, "containsPii": true, "visibleByDefault": true, "autoPopulated": true, "options": [], "validations": [ { "validationId": 89, "validationType": "FORMAT", "validationParam": "DECIMAL_DEGREES", "errorMessage": "e.g. 21.1458" } ] },
                { "questionId": 729, "questionUuid": "OCC_Q29", "questionText": "Longitude", "questionType": "READONLY_NUMBER", "isMandatory": false, "displayOrder": 2, "containsPii": true, "visibleByDefault": true, "autoPopulated": true, "options": [], "validations": [ { "validationId": 90, "validationType": "FORMAT", "validationParam": "DECIMAL_DEGREES", "errorMessage": "e.g. 21.1458" } ] },
                { "questionId": 730, "questionUuid": "OCC_Q30", "questionText": "DigiPin", "questionType": "READONLY_TEXT", "isMandatory": false, "displayOrder": 3, "containsPii": true, "visibleByDefault": true, "autoPopulated": true, "autoPopulateLogic": "Auto-generated from Lat/Long using DIGIPIN encoding algorithm (open-source, offline-capable)", "displayFormat": "XXXX-XXXX-XX", "options": [], "validations": [] },
                { "questionId": 731, "questionUuid": "OCC_Q31", "questionText": "Timestamp", "questionType": "READONLY_TEXT", "isMandatory": false, "displayOrder": 4, "containsPii": false, "visibleByDefault": true, "autoPopulated": true, "autoPopulateLogic": "System-captured date and time", "options": [], "validations": [] }
              ]
            },
            {
              "sectionId": 73,
              "sectionUuid": "OCC_SEC_D",
              "sectionName": "Additional Information",
              "sectionNameHindi": null,
              "sectionPhase": "PRE_SUBMIT",
              "isRequired": false,
              "displayOrder": 4,
              "hasSubmitButton": true,
              "questions": [
                {
                  "questionId": 732,
                  "questionUuid": "OCC_REMARKS",
                  "questionText": "Any other significant Information",
                  "questionTextHindi": null,
                  "questionType": "TEXT",
                  "isMandatory": false,
                  "displayOrder": 1,
                  "maxLength": null,
                  "defaultValue": null,
                  "containsPii": false,
                  "visibleByDefault": true,
                  "options": [],
                  "validations": [
                    { "validationId": 91, "validationType": "MIN_MAX_WORDS", "validationParam": "250-500", "errorMessage": "Please provide between 250 and 500 words" }
                  ]
                }
              ]
            }
          ]
        }
      ]
    }
""".trimIndent()

package org.piramalswasthya.stoptb.repositories

// Expanded TB Screening & Counselling form definition.
// Structure is identical to the backend response contract:
//   data -> sections[] -> questions[] -> options[] -> conditions[]
//                                     -> validations[]
//
// Dynamic-flow notes:
//  - "SHOW"            reveals a single hidden question (targetQuestionId / targetQuestionCode).
//  - "SHOW_QUESTIONS"  reveals a downstream question (or a section, via targetSectionId).
//  - Hidden questions carry "visibleByDefault": false and become visible only when
//    a triggering option is selected.
//  - Nested chain demo: Q7 -> Q8 -> Q9 (test done -> test type -> CBNAAT result).
//  - Cross-section demo: Q15 (Section 5) -> Q16 (Section 6).




val COUNSELLING_DUMMY_JSON3 = """
{
    "success": true,
    "message": null,
    "data": {
    "formId": 12,
    "formUuid": "COUNSELLING-TB-ADHERENCE-V1",
    "formName": "TB Treatment Adherence Counselling",
    "formType": "COUNSELLING_MODULE",
    "isActive": true,
    "versionNumber": 1,
    "sections": [
    {
        "sectionId": 21,
        "sectionUuid": "SEC-PATIENT-PROFILE",
        "sectionName": "Patient Profile",
        "sectionPhase": "INTAKE",
        "isRequired": true,
        "displayOrder": 1,
        "hasSubmitButton": false,
        "questions": [
        {
            "questionId": 34,
            "questionUuid": "Q-PATIENT-NAME",
            "questionText": "Patient full name",
            "questionType": "TEXT",
            "isMandatory": true,
            "displayOrder": 1,
            "maxLength": 100,
            "defaultValue": null,
            "containsPii": true,
            "visibleByDefault": true,
            "options": [],
            "validations": [
            {
                "validationId": 5,
                "validationType": "MAX_LENGTH",
                "validationParam": "100",
                "errorMessage": "Name cannot exceed 100 characters"
            },
            {
                "validationId": 6,
                "validationType": "REGEX",
                "validationParam": "^[a-zA-Z ]+$",
                "errorMessage": "Name must contain letters only"
            }
            ]
        },
        {
            "questionId": 35,
            "questionUuid": "Q-DOB",
            "questionText": "Date of birth",
            "questionType": "DATE",
            "isMandatory": true,
            "displayOrder": 2,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": true,
            "visibleByDefault": true,
            "options": [],
            "validations": [
            {
                "validationId": 7,
                "validationType": "MAX_DATE",
                "validationParam": "TODAY",
                "errorMessage": "Date of birth cannot be in the future"
            },
            {
                "validationId": 8,
                "validationType": "MIN_DATE",
                "validationParam": "1900-01-01",
                "errorMessage": "Date of birth seems too far in the past"
            }
            ]
        },
        {
            "questionId": 36,
            "questionUuid": "Q-GENDER",
            "questionText": "Gender",
            "questionType": "RADIO",
            "isMandatory": true,
            "displayOrder": 3,
            "maxLength": null,
            "defaultValue": "MALE",
            "containsPii": false,
            "visibleByDefault": true,
            "options": [
            {
                "optionId": 55,
                "optionLabel": "Male",
                "optionValue": "MALE",
                "displayOrder": 1,
                "conditions": []
            },
            {
                "optionId": 56,
                "optionLabel": "Female",
                "optionValue": "FEMALE",
                "displayOrder": 2,
                "conditions": [
                {
                    "conditionId": 5,
                    "actionType": "SHOW_QUESTION",
                    "targetQuestionId": 37,
                    "targetSectionId": null,
                    "targetQuestionUuid": "Q-PREGNANCY-STATUS",
                    "targetSectionUuid": null
                }
                ]
            },
            {
                "optionId": 57,
                "optionLabel": "Other",
                "optionValue": "OTHER",
                "displayOrder": 3,
                "conditions": []
            }
            ],
            "validations": []
        },
        {
            "questionId": 37,
            "questionUuid": "Q-PREGNANCY-STATUS",
            "questionText": "Is the patient currently pregnant?",
            "questionType": "RADIO",
            "isMandatory": false,
            "displayOrder": 4,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": false,
            "options": [
            {
                "optionId": 58,
                "optionLabel": "Yes",
                "optionValue": "YES",
                "displayOrder": 1,
                "conditions": []
            },
            {
                "optionId": 59,
                "optionLabel": "No",
                "optionValue": "NO",
                "displayOrder": 2,
                "conditions": []
            }
            ],
            "validations": [
            {
                "validationId": 9,
                "validationType": "MANDATORY_IF",
                "validationParam": "Q-GENDER=FEMALE",
                "errorMessage": "Pregnancy status is required for female patients"
            }
            ]
        },
        {
            "questionId": 38,
            "questionUuid": "Q-PHONE",
            "questionText": "Contact number",
            "questionType": "TEXT",
            "isMandatory": false,
            "displayOrder": 5,
            "maxLength": 10,
            "defaultValue": null,
            "containsPii": true,
            "visibleByDefault": true,
            "options": [],
            "validations": [
            {
                "validationId": 10,
                "validationType": "REGEX",
                "validationParam": "^[6-9][0-9]{9}$",
                "errorMessage": "Enter a valid 10-digit Indian mobile number"
            }
            ]
        },
        {
            "questionId": 39,
            "questionUuid": "Q-ASHA-ID",
            "questionText": "ASHA worker ID",
            "questionType": "AUTO_FILL",
            "isMandatory": true,
            "displayOrder": 6,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": true,
            "options": [],
            "validations": []
        }
        ]
    },
    {
        "sectionId": 22,
        "sectionUuid": "SEC-TB-HISTORY",
        "sectionName": "TB Diagnosis & Treatment History",
        "sectionPhase": "ASSESSMENT",
        "isRequired": true,
        "displayOrder": 2,
        "hasSubmitButton": false,
        "questions": [
        {
            "questionId": 40,
            "questionUuid": "Q-TB-TYPE",
            "questionText": "Type of TB diagnosed",
            "questionType": "RADIO",
            "isMandatory": true,
            "displayOrder": 1,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": true,
            "options": [
            {
                "optionId": 60,
                "optionLabel": "Pulmonary TB",
                "optionValue": "PULMONARY",
                "displayOrder": 1,
                "conditions": [
                {
                    "conditionId": 6,
                    "actionType": "SHOW_QUESTION",
                    "targetQuestionId": 41,
                    "targetSectionId": null,
                    "targetQuestionUuid": "Q-SPUTUM-RESULT",
                    "targetSectionUuid": null
                }
                ]
            },
            {
                "optionId": 61,
                "optionLabel": "Extra-pulmonary TB",
                "optionValue": "EXTRA_PULMONARY",
                "displayOrder": 2,
                "conditions": [
                {
                    "conditionId": 7,
                    "actionType": "SHOW_QUESTION",
                    "targetQuestionId": 42,
                    "targetSectionId": null,
                    "targetQuestionUuid": "Q-EXTRA-PULM-SITE",
                    "targetSectionUuid": null
                }
                ]
            },
            {
                "optionId": 62,
                "optionLabel": "Drug-Resistant TB (DR-TB)",
                "optionValue": "DR_TB",
                "displayOrder": 3,
                "conditions": [
                {
                    "conditionId": 8,
                    "actionType": "SHOW_QUESTION",
                    "targetQuestionId": 43,
                    "targetSectionId": null,
                    "targetQuestionUuid": "Q-DR-TB-REGIMEN",
                    "targetSectionUuid": null
                },
                {
                    "conditionId": 9,
                    "actionType": "DISABLE_SECTION_VALIDATION",
                    "targetQuestionId": null,
                    "targetSectionId": 23,
                    "targetQuestionUuid": null,
                    "targetSectionUuid": "SEC-STANDARD-REGIMEN"
                }
                ]
            }
            ],
            "validations": []
        },
        {
            "questionId": 41,
            "questionUuid": "Q-SPUTUM-RESULT",
            "questionText": "Sputum smear result",
            "questionType": "RADIO",
            "isMandatory": false,
            "displayOrder": 2,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": false,
            "options": [
            {
                "optionId": 63,
                "optionLabel": "Positive",
                "optionValue": "POSITIVE",
                "displayOrder": 1,
                "conditions": []
            },
            {
                "optionId": 64,
                "optionLabel": "Negative",
                "optionValue": "NEGATIVE",
                "displayOrder": 2,
                "conditions": []
            },
            {
                "optionId": 65,
                "optionLabel": "Not done",
                "optionValue": "NOT_DONE",
                "displayOrder": 3,
                "conditions": []
            }
            ],
            "validations": []
        },
        {
            "questionId": 42,
            "questionUuid": "Q-EXTRA-PULM-SITE",
            "questionText": "Site of extra-pulmonary TB",
            "questionType": "TEXT",
            "isMandatory": false,
            "displayOrder": 3,
            "maxLength": 200,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": false,
            "options": [],
            "validations": [
            {
                "validationId": 11,
                "validationType": "MAX_LENGTH",
                "validationParam": "200",
                "errorMessage": "Site description cannot exceed 200 characters"
            }
            ]
        },
        {
            "questionId": 43,
            "questionUuid": "Q-DR-TB-REGIMEN",
            "questionText": "DR-TB treatment regimen",
            "questionType": "TEXT",
            "isMandatory": false,
            "displayOrder": 4,
            "maxLength": 300,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": false,
            "options": [],
            "validations": []
        },
        {
            "questionId": 44,
            "questionUuid": "Q-COMORBIDITIES",
            "questionText": "Select all comorbidities present",
            "questionType": "MCQ",
            "isMandatory": false,
            "displayOrder": 5,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": true,
            "options": [
            {
                "optionId": 66,
                "optionLabel": "Diabetes",
                "optionValue": "DIABETES",
                "displayOrder": 1,
                "conditions": [
                {
                    "conditionId": 10,
                    "actionType": "SHOW_QUESTION",
                    "targetQuestionId": 45,
                    "targetSectionId": null,
                    "targetQuestionUuid": "Q-HBA1C",
                    "targetSectionUuid": null
                }
                ]
            },
            {
                "optionId": 67,
                "optionLabel": "HIV",
                "optionValue": "HIV",
                "displayOrder": 2,
                "conditions": []
            },
            {
                "optionId": 68,
                "optionLabel": "Malnutrition",
                "optionValue": "MALNUTRITION",
                "displayOrder": 3,
                "conditions": []
            },
            {
                "optionId": 69,
                "optionLabel": "None",
                "optionValue": "NONE",
                "displayOrder": 4,
                "conditions": []
            }
            ],
            "validations": []
        },
        {
            "questionId": 45,
            "questionUuid": "Q-HBA1C",
            "questionText": "Latest HbA1c value (%)",
            "questionType": "TEXT",
            "isMandatory": false,
            "displayOrder": 6,
            "maxLength": 5,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": false,
            "options": [],
            "validations": [
            {
                "validationId": 12,
                "validationType": "REGEX",
                "validationParam": "^\\d{1,2}(\\.\\d)?$",
                "errorMessage": "Enter a valid HbA1c value e.g. 7.2"
            }
            ]
        },
        {
            "questionId": 46,
            "questionUuid": "Q-TREATMENT-START",
            "questionText": "Treatment start date",
            "questionType": "DATE",
            "isMandatory": true,
            "displayOrder": 7,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": true,
            "options": [],
            "validations": [
            {
                "validationId": 13,
                "validationType": "MAX_DATE",
                "validationParam": "TODAY",
                "errorMessage": "Treatment start date cannot be in the future"
            }
            ]
        }
        ]
    },
    {
        "sectionId": 23,
        "sectionUuid": "SEC-STANDARD-REGIMEN",
        "sectionName": "Standard DOTS Regimen",
        "sectionPhase": "ASSESSMENT",
        "isRequired": true,
        "displayOrder": 3,
        "hasSubmitButton": false,
        "questions": [
        {
            "questionId": 47,
            "questionUuid": "Q-DOTS-PHASE",
            "questionText": "Current DOTS phase",
            "questionType": "RADIO",
            "isMandatory": true,
            "displayOrder": 1,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": true,
            "options": [
            {
                "optionId": 70,
                "optionLabel": "Intensive (2 months)",
                "optionValue": "INTENSIVE",
                "displayOrder": 1,
                "conditions": []
            },
            {
                "optionId": 71,
                "optionLabel": "Continuation (4 months)",
                "optionValue": "CONTINUATION",
                "displayOrder": 2,
                "conditions": []
            }
            ],
            "validations": []
        },
        {
            "questionId": 48,
            "questionUuid": "Q-MISSED-DOSES",
            "questionText": "Number of doses missed in last 30 days",
            "questionType": "TEXT",
            "isMandatory": true,
            "displayOrder": 2,
            "maxLength": 3,
            "defaultValue": "0",
            "containsPii": false,
            "visibleByDefault": true,
            "options": [],
            "validations": [
            {
                "validationId": 14,
                "validationType": "REGEX",
                "validationParam": "^([0-9]|[1-2][0-9]|30)$",
                "errorMessage": "Enter a number between 0 and 30"
            }
            ]
        },
        {
            "questionId": 49,
            "questionUuid": "Q-SIDE-EFFECTS",
            "questionText": "Reported side effects",
            "questionType": "MCQ",
            "isMandatory": false,
            "displayOrder": 3,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": true,
            "options": [
            {
                "optionId": 72,
                "optionLabel": "Nausea/Vomiting",
                "optionValue": "NAUSEA",
                "displayOrder": 1,
                "conditions": []
            },
            {
                "optionId": 73,
                "optionLabel": "Jaundice",
                "optionValue": "JAUNDICE",
                "displayOrder": 2,
                "conditions": []
            },
            {
                "optionId": 74,
                "optionLabel": "Skin rash",
                "optionValue": "SKIN_RASH",
                "displayOrder": 3,
                "conditions": []
            },
            {
                "optionId": 75,
                "optionLabel": "None",
                "optionValue": "NONE",
                "displayOrder": 4,
                "conditions": []
            }
            ],
            "validations": []
        },
        {
            "questionId": 50,
            "questionUuid": "Q-NOTICE-DISPLAY",
            "questionText": "Remind patient: take all tablets together with water after food",
            "questionType": "DISPLAY",
            "isMandatory": false,
            "displayOrder": 4,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": true,
            "options": [],
            "validations": []
        }
        ]
    },
    {
        "sectionId": 24,
        "sectionUuid": "SEC-COUNSELLING-OUTCOME",
        "sectionName": "Counselling Outcome",
        "sectionPhase": "OUTCOME",
        "isRequired": true,
        "displayOrder": 4,
        "hasSubmitButton": true,
        "questions": [
        {
            "questionId": 51,
            "questionUuid": "Q-PATIENT-UNDERSTANDING",
            "questionText": "Patient's understanding of treatment",
            "questionType": "RADIO",
            "isMandatory": true,
            "displayOrder": 1,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": true,
            "options": [
            {
                "optionId": 76,
                "optionLabel": "Good",
                "optionValue": "GOOD",
                "displayOrder": 1,
                "conditions": []
            },
            {
                "optionId": 77,
                "optionLabel": "Moderate",
                "optionValue": "MODERATE",
                "displayOrder": 2,
                "conditions": []
            },
            {
                "optionId": 78,
                "optionLabel": "Poor",
                "optionValue": "POOR",
                "displayOrder": 3,
                "conditions": [
                {
                    "conditionId": 11,
                    "actionType": "SHOW_QUESTION",
                    "targetQuestionId": 52,
                    "targetSectionId": null,
                    "targetQuestionUuid": "Q-FOLLOWUP-DATE",
                    "targetSectionUuid": null
                }
                ]
            }
            ],
            "validations": []
        },
        {
            "questionId": 52,
            "questionUuid": "Q-FOLLOWUP-DATE",
            "questionText": "Next follow-up date",
            "questionType": "DATE",
            "isMandatory": false,
            "displayOrder": 2,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": false,
            "options": [],
            "validations": [
            {
                "validationId": 15,
                "validationType": "MIN_DATE",
                "validationParam": "TODAY",
                "errorMessage": "Follow-up date must be today or in the future"
            }
            ]
        },
        {
            "questionId": 53,
            "questionUuid": "Q-COUNSELLOR-NOTES",
            "questionText": "Counsellor remarks",
            "questionType": "TEXT",
            "isMandatory": false,
            "displayOrder": 3,
            "maxLength": 500,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": true,
            "options": [],
            "validations": [
            {
                "validationId": 16,
                "validationType": "MAX_LENGTH",
                "validationParam": "500",
                "errorMessage": "Remarks cannot exceed 500 characters"
            }
            ]
        }
        ]
    }
    ]
}
}

"""




//fresh new Counselling_dummy_json
val COUNSELLING_DUMMY_JSON = """
{
    "success": true,
    "message": null,
    "data": {
    "formId": 1,
    "formUuid": "TB_SCREENING_V1",
    "formName": "TB Screening Form",
    "formType": "SCREENING",
    "isActive": true,
    "versionNumber": 1,
    "sections": [
    {
        "sectionId": 1,
        "sectionUuid": "SYMPTOMS",
        "sectionName": "Symptom Assessment",
        "sectionPhase": "PRE_SUBMIT",
        "isRequired": true,
        "displayOrder": 1,
        "hasSubmitButton": false,
        "questions": [
        {
            "questionId": 1,
            "questionUuid": "COUGH_DURATION",
            "questionText": "How long have you had a cough?",
            "questionType": "RADIO",
            "isMandatory": true,
            "displayOrder": 1,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": true,
            "options": [
            {
                "optionId": 1,
                "optionLabel": "Less than 2 weeks",
                "optionValue": "LT_2W",
                "displayOrder": 1,
                "conditions": []
            },
            {
                "optionId": 2,
                "optionLabel": "2 weeks or more",
                "optionValue": "GTE_2W",
                "displayOrder": 2,
                "conditions": [
                {
                    "conditionId": 1,
                    "actionType": "SHOW",
                    "targetQuestionId": 2,
                    "targetSectionId": null,
                    "targetQuestionUuid": "SPUTUM_COLLECTION",
                    "targetSectionUuid": null
                }
                ]
            }
            ],
            "validations": []
        },
        {
            "questionId": 2,
            "questionUuid": "SPUTUM_COLLECTION",
            "questionText": "Can sputum sample be collected?",
            "questionType": "RADIO",
            "isMandatory": false,
            "displayOrder": 2,
            "maxLength": null,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": false,
            "options": [
            {
                "optionId": 3,
                "optionLabel": "Yes",
                "optionValue": "YES",
                "displayOrder": 1,
                "conditions": []
            },
            {
                "optionId": 4,
                "optionLabel": "No",
                "optionValue": "NO",
                "displayOrder": 2,
                "conditions": []
            }
            ],
            "validations": []
        },
        {
            "questionId": 3,
            "questionUuid": "PATIENT_AGE",
            "questionText": "Patient age",
            "questionType": "TEXT",
            "isMandatory": true,
            "displayOrder": 3,
            "maxLength": 3,
            "defaultValue": null,
            "containsPii": false,
            "visibleByDefault": true,
            "options": [],
            "validations": [
            {
                "validationId": 1,
                "validationType": "MAX_LENGTH",
                "validationParam": "3",
                "errorMessage": "Age must be at most 3 digits"
            }
            ]
        }
        ]
    }
    ]
}
}"""

//2nd old data
/*
val COUNSELLING_DUMMY_JSON = """
{
    "success": true,
    "message": null,
    "data": {
        "formId": 1,
        "formCode": "TB_SCREENING_COUNSELLING_V1",
        "formName": "TB Screening & Counselling Form",
        "formType": "SCREENING",
        "isActive": true,
        "versionNumber": 1,
        "sections": [
            {
                "sectionId": 1,
                "sectionCode": "SYMPTOMS",
                "sectionName": "Symptom Assessment",
                "sectionPhase": "PRE_SUBMIT",
                "isRequired": true,
                "displayOrder": 1,
                "hasSubmitButton": false,
                "questions": [
                    {
                        "questionId": 1,
                        "questionCode": "COUGH_DURATION",
                        "questionText": "How long have you had a cough?",
                        "questionType": "RADIO",
                        "isMandatory": true,
                        "displayOrder": 1,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": true,
                        "options": [
                            {
                                "optionId": 1,
                                "optionLabel": "Less than 2 weeks",
                                "optionValue": "LT_2W",
                                "displayOrder": 1,
                                "conditions": []
                            },
                            {
                                "optionId": 2,
                                "optionLabel": "2 weeks or more",
                                "optionValue": "GTE_2W",
                                "displayOrder": 2,
                                "conditions": [
                                    {
                                        "conditionId": 1,
                                        "actionType": "SHOW",
                                        "targetQuestionId": 2,
                                        "targetSectionId": null,
                                        "targetQuestionCode": "SPUTUM_COLLECTION",
                                        "targetSectionCode": null
                                    }
                                ]
                            }
                        ],
                        "validations": []
                    },
                    {
                        "questionId": 2,
                        "questionCode": "SPUTUM_COLLECTION",
                        "questionText": "Can a sputum sample be collected now?",
                        "questionType": "RADIO",
                        "isMandatory": false,
                        "displayOrder": 2,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": false,
                        "options": [
                            {
                                "optionId": 3,
                                "optionLabel": "Yes",
                                "optionValue": "YES",
                                "displayOrder": 1,
                                "conditions": []
                            },
                            {
                                "optionId": 4,
                                "optionLabel": "No",
                                "optionValue": "NO",
                                "displayOrder": 2,
                                "conditions": []
                            }
                        ],
                        "validations": []
                    },
                    {
                        "questionId": 3,
                        "questionCode": "OTHER_SYMPTOMS",
                        "questionText": "Which of the following symptoms are present?",
                        "questionType": "CHECKBOX",
                        "isMandatory": false,
                        "displayOrder": 3,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": true,
                        "options": [
                            {
                                "optionId": 5,
                                "optionLabel": "Fever for more than 2 weeks",
                                "optionValue": "FEVER",
                                "displayOrder": 1,
                                "conditions": []
                            },
                            {
                                "optionId": 6,
                                "optionLabel": "Unexplained weight loss",
                                "optionValue": "WEIGHT_LOSS",
                                "displayOrder": 2,
                                "conditions": []
                            },
                            {
                                "optionId": 7,
                                "optionLabel": "Night sweats",
                                "optionValue": "NIGHT_SWEATS",
                                "displayOrder": 3,
                                "conditions": []
                            },
                            {
                                "optionId": 8,
                                "optionLabel": "Blood in sputum",
                                "optionValue": "HEMOPTYSIS",
                                "displayOrder": 4,
                                "conditions": []
                            }
                        ],
                        "validations": []
                    }
                ]
            },
            {
                "sectionId": 2,
                "sectionCode": "RISK_HISTORY",
                "sectionName": "Risk & Contact History",
                "sectionPhase": "PRE_SUBMIT",
                "isRequired": true,
                "displayOrder": 2,
                "hasSubmitButton": false,
                "questions": [
                    {
                        "questionId": 4,
                        "questionCode": "TB_CONTACT",
                        "questionText": "Have you been in close contact with a known TB patient?",
                        "questionType": "RADIO",
                        "isMandatory": true,
                        "displayOrder": 1,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": true,
                        "options": [
                            {
                                "optionId": 9,
                                "optionLabel": "Yes",
                                "optionValue": "YES",
                                "displayOrder": 1,
                                "conditions": [
                                    {
                                        "conditionId": 2,
                                        "actionType": "SHOW",
                                        "targetQuestionId": 5,
                                        "targetSectionId": null,
                                        "targetQuestionCode": "CONTACT_RELATION",
                                        "targetSectionCode": null
                                    }
                                ]
                            },
                            {
                                "optionId": 10,
                                "optionLabel": "No",
                                "optionValue": "NO",
                                "displayOrder": 2,
                                "conditions": []
                            },
                            {
                                "optionId": 11,
                                "optionLabel": "Not sure",
                                "optionValue": "UNKNOWN",
                                "displayOrder": 3,
                                "conditions": []
                            }
                        ],
                        "validations": []
                    },
                    {
                        "questionId": 5,
                        "questionCode": "CONTACT_RELATION",
                        "questionText": "What is your relationship to the TB patient?",
                        "questionType": "DROPDOWN",
                        "isMandatory": false,
                        "displayOrder": 2,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": false,
                        "options": [
                            {
                                "optionId": 12,
                                "optionLabel": "Household member",
                                "optionValue": "HOUSEHOLD",
                                "displayOrder": 1,
                                "conditions": []
                            },
                            {
                                "optionId": 13,
                                "optionLabel": "Workplace contact",
                                "optionValue": "WORKPLACE",
                                "displayOrder": 2,
                                "conditions": []
                            },
                            {
                                "optionId": 14,
                                "optionLabel": "Other",
                                "optionValue": "OTHER",
                                "displayOrder": 3,
                                "conditions": []
                            }
                        ],
                        "validations": []
                    },
                    {
                        "questionId": 6,
                        "questionCode": "COMORBIDITIES",
                        "questionText": "Does the patient have any of the following conditions?",
                        "questionType": "CHECKBOX",
                        "isMandatory": false,
                        "displayOrder": 3,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": true,
                        "options": [
                            {
                                "optionId": 15,
                                "optionLabel": "Diabetes",
                                "optionValue": "DIABETES",
                                "displayOrder": 1,
                                "conditions": []
                            },
                            {
                                "optionId": 16,
                                "optionLabel": "HIV positive",
                                "optionValue": "HIV",
                                "displayOrder": 2,
                                "conditions": []
                            },
                            {
                                "optionId": 17,
                                "optionLabel": "None",
                                "optionValue": "NONE",
                                "displayOrder": 3,
                                "conditions": []
                            }
                        ],
                        "validations": []
                    }
                ]
            },
            {
                "sectionId": 3,
                "sectionCode": "DIAGNOSTICS",
                "sectionName": "Diagnostic Tests",
                "sectionPhase": "PRE_SUBMIT",
                "isRequired": false,
                "displayOrder": 3,
                "hasSubmitButton": false,
                "questions": [
                    {
                        "questionId": 7,
                        "questionCode": "TEST_DONE",
                        "questionText": "Has any diagnostic test been performed?",
                        "questionType": "RADIO",
                        "isMandatory": true,
                        "displayOrder": 1,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": true,
                        "options": [
                            {
                                "optionId": 18,
                                "optionLabel": "Yes",
                                "optionValue": "YES",
                                "displayOrder": 1,
                                "conditions": [
                                    {
                                        "conditionId": 3,
                                        "actionType": "SHOW",
                                        "targetQuestionId": 8,
                                        "targetSectionId": null,
                                        "targetQuestionCode": "TEST_TYPE",
                                        "targetSectionCode": null
                                    }
                                ]
                            },
                            {
                                "optionId": 19,
                                "optionLabel": "No",
                                "optionValue": "NO",
                                "displayOrder": 2,
                                "conditions": []
                            }
                        ],
                        "validations": []
                    },
                    {
                        "questionId": 8,
                        "questionCode": "TEST_TYPE",
                        "questionText": "Which test was performed?",
                        "questionType": "MCQ",
                        "isMandatory": false,
                        "displayOrder": 2,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": false,
                        "options": [
                            {
                                "optionId": 20,
                                "optionLabel": "Sputum microscopy",
                                "optionValue": "MICROSCOPY",
                                "displayOrder": 1,
                                "conditions": []
                            },
                            {
                                "optionId": 21,
                                "optionLabel": "CBNAAT / NAAT",
                                "optionValue": "CBNAAT",
                                "displayOrder": 2,
                                "conditions": [
                                    {
                                        "conditionId": 4,
                                        "actionType": "SHOW",
                                        "targetQuestionId": 9,
                                        "targetSectionId": null,
                                        "targetQuestionCode": "CBNAAT_RESULT",
                                        "targetSectionCode": null
                                    }
                                ]
                            },
                            {
                                "optionId": 22,
                                "optionLabel": "Chest X-ray",
                                "optionValue": "CXR",
                                "displayOrder": 3,
                                "conditions": []
                            }
                        ],
                        "validations": []
                    },
                    {
                        "questionId": 9,
                        "questionCode": "CBNAAT_RESULT",
                        "questionText": "What was the CBNAAT result?",
                        "questionType": "RADIO",
                        "isMandatory": false,
                        "displayOrder": 3,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": false,
                        "options": [
                            {
                                "optionId": 23,
                                "optionLabel": "MTB Detected",
                                "optionValue": "MTB_DETECTED",
                                "displayOrder": 1,
                                "conditions": []
                            },
                            {
                                "optionId": 24,
                                "optionLabel": "MTB Not Detected",
                                "optionValue": "MTB_NOT_DETECTED",
                                "displayOrder": 2,
                                "conditions": []
                            }
                        ],
                        "validations": []
                    }
                ]
            },
            {
                "sectionId": 4,
                "sectionCode": "DEMOGRAPHICS",
                "sectionName": "Patient Demographics",
                "sectionPhase": "PRE_SUBMIT",
                "isRequired": true,
                "displayOrder": 4,
                "hasSubmitButton": false,
                "questions": [
                    {
                        "questionId": 10,
                        "questionCode": "PATIENT_NAME",
                        "questionText": "Patient full name",
                        "questionType": "TEXT",
                        "isMandatory": true,
                        "displayOrder": 1,
                        "maxLength": 100,
                        "defaultValue": null,
                        "containsPii": true,
                        "visibleByDefault": true,
                        "options": [],
                        "validations": [
                            {
                                "validationId": 1,
                                "validationType": "MAX_LENGTH",
                                "validationParam": "100",
                                "errorMessage": "Name must be at most 100 characters"
                            }
                        ]
                    },
                    {
                        "questionId": 11,
                        "questionCode": "PATIENT_AGE",
                        "questionText": "Patient age (years)",
                        "questionType": "NUMBER",
                        "isMandatory": true,
                        "displayOrder": 2,
                        "maxLength": 3,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": true,
                        "options": [],
                        "validations": [
                            {
                                "validationId": 2,
                                "validationType": "MIN_VALUE",
                                "validationParam": "0",
                                "errorMessage": "Age cannot be negative"
                            },
                            {
                                "validationId": 3,
                                "validationType": "MAX_VALUE",
                                "validationParam": "120",
                                "errorMessage": "Age must be 120 or less"
                            }
                        ]
                    },
                    {
                        "questionId": 12,
                        "questionCode": "MOBILE_NUMBER",
                        "questionText": "Patient mobile number",
                        "questionType": "TEXT",
                        "isMandatory": false,
                        "displayOrder": 3,
                        "maxLength": 10,
                        "defaultValue": null,
                        "containsPii": true,
                        "visibleByDefault": true,
                        "options": [],
                        "validations": [
                            {
                                "validationId": 4,
                                "validationType": "REGEX",
                                "validationParam": "^[6-9]\\d{9}$",
                                "errorMessage": "Enter a valid 10-digit mobile number"
                            },
                            {
                                "validationId": 5,
                                "validationType": "MAX_LENGTH",
                                "validationParam": "10",
                                "errorMessage": "Mobile number must be 10 digits"
                            }
                        ]
                    }
                ]
            },
            {
                "sectionId": 8,
                "sectionCode": "DEMOGRAPHICS",
                "sectionName": "Patient Demographics",
                "sectionPhase": "PRE_SUBMIT",
                "isRequired": true,
                "displayOrder": 5,
                "hasSubmitButton": true,
                "questions": [
                    {
                        "questionId": 10,
                        "questionCode": "PATIENT_NAME",
                        "questionText": "Patient full name",
                        "questionType": "TEXT",
                        "isMandatory": true,
                        "displayOrder": 1,
                        "maxLength": 100,
                        "defaultValue": null,
                        "containsPii": true,
                        "visibleByDefault": true,
                        "options": [],
                        "validations": [
                            {
                                "validationId": 1,
                                "validationType": "MAX_LENGTH",
                                "validationParam": "100",
                                "errorMessage": "Name must be at most 100 characters"
                            }
                        ]
                    },
                    {
                        "questionId": 11,
                        "questionCode": "PATIENT_AGE",
                        "questionText": "Patient age (years)",
                        "questionType": "NUMBER",
                        "isMandatory": true,
                        "displayOrder": 2,
                        "maxLength": 3,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": true,
                        "options": [],
                        "validations": [
                            {
                                "validationId": 2,
                                "validationType": "MIN_VALUE",
                                "validationParam": "0",
                                "errorMessage": "Age cannot be negative"
                            },
                            {
                                "validationId": 3,
                                "validationType": "MAX_VALUE",
                                "validationParam": "120",
                                "errorMessage": "Age must be 120 or less"
                            }
                        ]
                    },
                    {
                        "questionId": 12,
                        "questionCode": "MOBILE_NUMBER",
                        "questionText": "Patient mobile number",
                        "questionType": "TEXT",
                        "isMandatory": false,
                        "displayOrder": 3,
                        "maxLength": 10,
                        "defaultValue": null,
                        "containsPii": true,
                        "visibleByDefault": true,
                        "options": [],
                        "validations": [
                            {
                                "validationId": 4,
                                "validationType": "REGEX",
                                "validationParam": "^[6-9]\\d{9}$",
                                "errorMessage": "Enter a valid 10-digit mobile number"
                            },
                            {
                                "validationId": 5,
                                "validationType": "MAX_LENGTH",
                                "validationParam": "10",
                                "errorMessage": "Mobile number must be 10 digits"
                            }
                        ]
                    }
                ]
            },
            {
                "sectionId": 5,
                "sectionCode": "COUNSELLING",
                "sectionName": "Counselling & Awareness",
                "sectionPhase": "POST_SUBMIT",
                "isRequired": false,
                "displayOrder": 6,
                "hasSubmitButton": false,
                "questions": [
                    {
                        "questionId": 13,
                        "questionCode": "TB_AWARENESS",
                        "questionText": "How would you rate the patient's awareness about TB?",
                        "questionType": "RADIO",
                        "isMandatory": false,
                        "displayOrder": 1,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": true,
                        "options": [
                            {
                                "optionId": 25,
                                "optionLabel": "Good",
                                "optionValue": "GOOD",
                                "displayOrder": 1,
                                "conditions": []
                            },
                            {
                                "optionId": 26,
                                "optionLabel": "Average",
                                "optionValue": "AVERAGE",
                                "displayOrder": 2,
                                "conditions": []
                            },
                            {
                                "optionId": 27,
                                "optionLabel": "Poor / No awareness",
                                "optionValue": "POOR",
                                "displayOrder": 3,
                                "conditions": [
                                    {
                                        "conditionId": 5,
                                        "actionType": "SHOW",
                                        "targetQuestionId": 14,
                                        "targetSectionId": null,
                                        "targetQuestionCode": "COUNSELLING_TOPICS",
                                        "targetSectionCode": null
                                    }
                                ]
                            }
                        ],
                        "validations": []
                    },
                    {
                        "questionId": 14,
                        "questionCode": "COUNSELLING_TOPICS",
                        "questionText": "Which topics were covered during counselling?",
                        "questionType": "CHECKBOX",
                        "isMandatory": false,
                        "displayOrder": 2,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": false,
                        "options": [
                            {
                                "optionId": 28,
                                "optionLabel": "Disease transmission",
                                "optionValue": "TRANSMISSION",
                                "displayOrder": 1,
                                "conditions": []
                            },
                            {
                                "optionId": 29,
                                "optionLabel": "Treatment duration & adherence",
                                "optionValue": "ADHERENCE",
                                "displayOrder": 2,
                                "conditions": []
                            },
                            {
                                "optionId": 30,
                                "optionLabel": "Nutrition support",
                                "optionValue": "NUTRITION",
                                "displayOrder": 3,
                                "conditions": []
                            },
                            {
                                "optionId": 31,
                                "optionLabel": "Stigma & mental health",
                                "optionValue": "STIGMA",
                                "displayOrder": 4,
                                "conditions": []
                            }
                        ],
                        "validations": []
                    },
                    {
                        "questionId": 15,
                        "questionCode": "WILLING_TREATMENT",
                        "questionText": "Is the patient willing to start treatment?",
                        "questionType": "RADIO",
                        "isMandatory": true,
                        "displayOrder": 3,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": true,
                        "options": [
                            {
                                "optionId": 32,
                                "optionLabel": "Yes",
                                "optionValue": "YES",
                                "displayOrder": 1,
                                "conditions": []
                            },
                            {
                                "optionId": 33,
                                "optionLabel": "No",
                                "optionValue": "NO",
                                "displayOrder": 2,
                                "conditions": [
                                    {
                                        "conditionId": 6,
                                        "actionType": "SHOW",
                                        "targetQuestionId": 16,
                                        "targetSectionId": 6,
                                        "targetQuestionCode": "ADHERENCE_BARRIER",
                                        "targetSectionCode": "ADHERENCE"
                                    }
                                ]
                            },
                            {
                                "optionId": 34,
                                "optionLabel": "Undecided",
                                "optionValue": "UNDECIDED",
                                "displayOrder": 3,
                                "conditions": [
                                    {
                                        "conditionId": 7,
                                        "actionType": "SHOW",
                                        "targetQuestionId": 16,
                                        "targetSectionId": 6,
                                        "targetQuestionCode": "ADHERENCE_BARRIER",
                                        "targetSectionCode": "ADHERENCE"
                                    }
                                ]
                            }
                        ],
                        "validations": []
                    }
                ]
            },
            {
                "sectionId": 6,
                "sectionCode": "ADHERENCE",
                "sectionName": "Treatment Adherence & Follow-up",
                "sectionPhase": "POST_SUBMIT",
                "isRequired": false,
                "displayOrder": 7,
                "hasSubmitButton": true,
                "questions": [
                    {
                        "questionId": 16,
                        "questionCode": "ADHERENCE_BARRIER",
                        "questionText": "Describe the main barrier to starting/continuing treatment",
                        "questionType": "TEXTAREA",
                        "isMandatory": false,
                        "displayOrder": 1,
                        "maxLength": 500,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": false,
                        "options": [],
                        "validations": [
                            {
                                "validationId": 6,
                                "validationType": "MAX_LENGTH",
                                "validationParam": "500",
                                "errorMessage": "Description must be at most 500 characters"
                            }
                        ]
                    },
                    {
                        "questionId": 17,
                        "questionCode": "DOTS_PROVIDER",
                        "questionText": "Who will act as the DOTS / treatment supporter?",
                        "questionType": "DROPDOWN",
                        "isMandatory": false,
                        "displayOrder": 2,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": true,
                        "options": [
                            {
                                "optionId": 35,
                                "optionLabel": "ASHA worker",
                                "optionValue": "ASHA",
                                "displayOrder": 1,
                                "conditions": []
                            },
                            {
                                "optionId": 36,
                                "optionLabel": "ANM",
                                "optionValue": "ANM",
                                "displayOrder": 2,
                                "conditions": []
                            },
                            {
                                "optionId": 37,
                                "optionLabel": "Family member",
                                "optionValue": "FAMILY",
                                "displayOrder": 3,
                                "conditions": []
                            },
                            {
                                "optionId": 38,
                                "optionLabel": "Self-administered",
                                "optionValue": "SELF",
                                "displayOrder": 4,
                                "conditions": []
                            }
                        ],
                        "validations": []
                    },
                    {
                        "questionId": 18,
                        "questionCode": "FOLLOWUP_DATE",
                        "questionText": "Next follow-up date",
                        "questionType": "DATE",
                        "isMandatory": true,
                        "displayOrder": 3,
                        "maxLength": null,
                        "defaultValue": null,
                        "containsPii": false,
                        "visibleByDefault": true,
                        "options": [],
                        "validations": [
                            {
                                "validationId": 7,
                                "validationType": "FUTURE_DATE",
                                "validationParam": "true",
                                "errorMessage": "Follow-up date must be in the future"
                            }
                        ]
                    }
                ]
            }
        ]
    }
}
""".trimIndent()

*/


// old data
/*val COUNSELLING_DUMMY_JSON = """
{
  "formCode": "TB_COUNSELLING_V1",
  "formName": "TB Counselling Form",
  "formType": "counselling",
  "isActive": true,
  "sections": [
    {
      "sectionCode": "SECTION_A",
      "sectionName": "Basic Details",
      "sectionPhase": "PRE_SUBMIT",
      "isRequired": true,
      "displayOrder": 1,
      "hasSubmitButton": false,
      "questions": [
        {
          "questionCode": "PATIENT_NAME",
          "questionText": "Enter Patient Name",
          "questionType": "TEXT",
          "isMandatory": true,
          "displayOrder": 1,
          "maxLength": 100,
          "defaultValue": null,
          "containsPii": true,
          "validations": [
            {
              "validationType": "MAX_LENGTH",
              "validationParam": "100",
              "errorMessage": "Maximum 100 characters allowed"
            }
          ]
        },
        {
          "questionCode": "HAS_COUGH",
          "questionText": "Does patient have cough?",
          "questionType": "RADIO",
          "isMandatory": true,
          "displayOrder": 2,
          "containsPii": false,
          "options": [
            {
              "optionLabel": "Yes",
              "optionValue": "YES",
              "displayOrder": 1,
              "conditions": [
                {
                  "actionType": "SHOW_QUESTION",
                  "question": {
                    "questionCode": "COUGH_DURATION",
                    "questionText": "Duration of cough in days",
                    "questionType": "TEXT",
                    "isMandatory": false,
                    "displayOrder": 3,
                    "maxLength": 3,
                    "containsPii": false,
                    "validations": [
                      {
                        "validationType": "REGEX",
                        "validationParam": "^[0-9]+${'$'}",
                        "errorMessage": "Only numeric value allowed"
                      }
                    ]
                  }
                },
                {
                  "actionType": "SHOW_QUESTION",
                  "question": {
                    "questionCode": "COUGH_TYPE",
                    "questionText": "Type of cough",
                    "questionType": "MCQ",
                    "isMandatory": true,
                    "displayOrder": 4,
                    "containsPii": false,
                    "options": [
                      {
                        "optionLabel": "Dry",
                        "optionValue": "DRY",
                        "displayOrder": 1
                      },
                      {
                        "optionLabel": "Wet",
                        "optionValue": "WET",
                        "displayOrder": 2
                      }
                    ]
                  }
                }
              ]
            },
            {
              "optionLabel": "No",
              "optionValue": "NO",
              "displayOrder": 2
            }
          ]
        }
      ]
    },
    {
      "sectionCode": "FOLLOW_UP_TU",
      "sectionName": "Follow Up",
      "sectionPhase": "POST_SUBMIT",
      "isRequired": false,
      "displayOrder": 2,
      "hasSubmitButton": true,
      "questions": [
        {
          "questionCode": "FOLLOW_UP_DATE",
          "questionText": "Follow Up Date",
          "questionType": "DATE",
          "isMandatory": true,
          "displayOrder": 1,
          "defaultValue": "TODAY",
          "containsPii": false,
          "validations": [
            {
              "validationType": "MIN_DATE",
              "validationParam": "TODAY",
              "errorMessage": "Date cannot be in past"
            }
          ]
        },
        {
          "questionCode": "STATUS",
          "questionText": "Treatment Status",
          "questionType": "MCQ",
          "isMandatory": true,
          "displayOrder": 2,
          "options": [
            {
              "optionLabel": "Complete",
              "optionValue": "COMPLETE",
              "displayOrder": 1,
              "conditions": [
                {
                  "actionType": "LOCK_FORM"
                }
              ]
            },
            {
              "optionLabel": "Refused",
              "optionValue": "REFUSED",
              "displayOrder": 2,
              "conditions": [
                {
                  "actionType": "DISABLE_SECTION_VALIDATION",
                  "targetSection": {
                    "sectionCode": "FOLLOW_UP_TU",
                    "sectionName": "Follow Up"
                  }
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
"""
 */



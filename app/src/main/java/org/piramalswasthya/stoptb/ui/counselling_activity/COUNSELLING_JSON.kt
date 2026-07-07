package org.piramalswasthya.stoptb.ui.counselling_activity

val COUNSELLING_JSON =
    """
    {
        "success": true,
        "message": null,
        "data": [
            {
                "formId": 1,
                "formUuid": "TB_COUNSELLING",
                "formName": "TB Counselling",
                "formType": "TB_COUNSELLING",
                "isActive": true,
                "followUpDelayDays": 15,
                "versionNumber": 1,
                "sections": [
                    {
                        "sectionId": 1,
                        "sectionUuid": "TB_SEC_A",
                        "sectionName": "Disease Awareness",
                        "sectionNameHindi": "बीमारी के बारे में जागरूकता",
                        "sectionPhase": "PRE_SUBMIT",
                        "isRequired": true,
                        "displayOrder": 1,
                        "hasSubmitButton": false,
                        "isEditable": false,
                        "questions": [
                            {
                                "questionId": 1,
                                "questionUuid": "TB_A_Q1",
                                "questionText": "TB disease explained to patient",
                                "questionTextHindi": "मरीज़ को टीबी बीमारी के बारे में समझाया गया।",
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
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 2,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 2,
                                "questionUuid": "TB_A_Q2",
                                "questionText": "Transmission route explained",
                                "questionTextHindi": "ट्रांसमिशन के तरीके के बारे में जानकारी",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 2,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 3,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 4,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 3,
                                "questionUuid": "TB_A_Q3",
                                "questionText": "Symptoms explained",
                                "questionTextHindi": "लक्षणों की जानकारी",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 3,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 5,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 6,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 4,
                                "questionUuid": "TB_A_Q4",
                                "questionText": "Treatment duration explained",
                                "questionTextHindi": "इलाज की अवधि के बारे में जानकारी",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 4,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 7,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 8,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 5,
                                "questionUuid": "TB_A_REMARKS",
                                "questionText": "Disease awareness notes",
                                "questionTextHindi": "बीमारी के बारे में जानकारी देने वाले नोट्स",
                                "questionType": "TEXT",
                                "isMandatory": false,
                                "displayOrder": 5,
                                "maxLength": 500,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [],
                                "validations": [
                                    {
                                        "validationId": 1,
                                        "validationType": "MAX_LENGTH",
                                        "validationParam": "500",
                                        "errorMessage": "Must be 500 characters or fewer"
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "sectionId": 2,
                        "sectionUuid": "TB_SEC_B",
                        "sectionName": "Do's and Don'ts",
                        "sectionNameHindi": "करो और ना करो",
                        "sectionPhase": "PRE_SUBMIT",
                        "isRequired": true,
                        "displayOrder": 2,
                        "hasSubmitButton": false,
                        "isEditable": false,
                        "questions": [
                            {
                                "questionId": 6,
                                "questionUuid": "TB_B_Q1",
                                "questionText": "Cover mouth while coughing — advised",
                                "questionTextHindi": "खांसते समय मुंह ढकने की सलाह दी जाती है।",
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
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 10,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 7,
                                "questionUuid": "TB_B_Q2",
                                "questionText": "Complete full treatment course — advised",
                                "questionTextHindi": "इलाज का पूरा कोर्स पूरा करने की सलाह दी जाती है।",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 2,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 11,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 12,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 8,
                                "questionUuid": "TB_B_Q3",
                                "questionText": "Regular follow-up attendance — advised",
                                "questionTextHindi": "इलाज का पूरा कोर्स पूरा करने की सलाह दी जाती है।",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 3,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 13,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 14,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 9,
                                "questionUuid": "TB_B_Q4",
                                "questionText": "Nutritional guidance provided",
                                "questionTextHindi": "पोषण संबंधी मार्गदर्शन प्रदान किया गया",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 4,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 15,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 16,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 10,
                                "questionUuid": "TB_B_Q5",
                                "questionText": "No smoking / alcohol — advised",
                                "questionTextHindi": "धूम्रपान / शराब न लेने की सलाह दी जाती है।",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 5,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 17,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 18,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 11,
                                "questionUuid": "TB_B_Q6",
                                "questionText": "Isolation precautions explained",
                                "questionTextHindi": "अलगाव सावधानियों के बारे में बताया गया",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 6,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 19,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 20,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 12,
                                "questionUuid": "TB_B_REMARKS",
                                "questionText": "Do's & Don'ts notes",
                                "questionTextHindi": "क्या करें और क्या न करें - नोट्स",
                                "questionType": "TEXT",
                                "isMandatory": false,
                                "displayOrder": 7,
                                "maxLength": 500,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [],
                                "validations": [
                                    {
                                        "validationId": 2,
                                        "validationType": "MAX_LENGTH",
                                        "validationParam": "500",
                                        "errorMessage": "Must be 500 characters or fewer"
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "sectionId": 3,
                        "sectionUuid": "TB_SEC_C",
                        "sectionName": "Government Schemes",
                        "sectionNameHindi": "सरकारी योजनाएं",
                        "sectionPhase": "PRE_SUBMIT",
                        "isRequired": false,
                        "displayOrder": 3,
                        "hasSubmitButton": false,
                        "isEditable": false,
                        "questions": [
                            {
                                "questionId": 13,
                                "questionUuid": "TB_C_Q1",
                                "questionText": "Nikshay Poshan Yojana (NPY) eligibility explained",
                                "questionTextHindi": "निक्षय पोषण योजना (एनपीवाई) पात्रता के बारे में बताया गया",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 1,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 21,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 22,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 14,
                                "questionUuid": "TB_C_Q2",
                                "questionText": "DOTS free treatment explained",
                                "questionTextHindi": "DOTS मुफ़्त इलाज के बारे में जानकारी",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 2,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 23,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 24,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 15,
                                "questionUuid": "TB_C_REMARKS",
                                "questionText": "Schemes notes",
                                "questionTextHindi": "योजनाओं के नोट्स",
                                "questionType": "TEXT",
                                "isMandatory": false,
                                "displayOrder": 3,
                                "maxLength": 300,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [],
                                "validations": [
                                    {
                                        "validationId": 3,
                                        "validationType": "MAX_LENGTH",
                                        "validationParam": "300",
                                        "errorMessage": "Must be 300 characters or fewer"
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "sectionId": 4,
                        "sectionUuid": "TB_SEC_D",
                        "sectionName": "Treatment Regimen",
                        "sectionNameHindi": "इलाज का तरीका",
                        "sectionPhase": "PRE_SUBMIT",
                        "isRequired": true,
                        "displayOrder": 4,
                        "hasSubmitButton": false,
                        "isEditable": false,
                        "questions": [
                            {
                                "questionId": 16,
                                "questionUuid": "TB_D_Q1",
                                "questionText": "Regimen explained to patient",
                                "questionTextHindi": "मरीज़ को इलाज का तरीका समझाया गया।",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 1,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 25,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 26,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 17,
                                "questionUuid": "TB_D_Q2",
                                "questionText": "Medication names explained",
                                "questionTextHindi": "दवाओं के नामों की जानकारी",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 2,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 27,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 28,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 18,
                                "questionUuid": "TB_D_Q3",
                                "questionText": "Side effects explained",
                                "questionTextHindi": "साइड इफ़ेक्ट्स के बारे में जानकारी",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 3,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 29,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 30,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 19,
                                "questionUuid": "TB_D_Q4",
                                "questionText": "Importance of adherence explained",
                                "questionTextHindi": "निर्देशों का पालन करने का महत्व समझाया गया",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 4,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 31,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 32,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 20,
                                "questionUuid": "TB_D_REMARKS",
                                "questionText": "Treatment regimen notes",
                                "questionTextHindi": "इलाज के तरीके से जुड़े नोट्स",
                                "questionType": "TEXT",
                                "isMandatory": false,
                                "displayOrder": 5,
                                "maxLength": 300,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [],
                                "validations": [
                                    {
                                        "validationId": 4,
                                        "validationType": "MAX_LENGTH",
                                        "validationParam": "300",
                                        "errorMessage": "Must be 300 characters or fewer"
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "sectionId": 5,
                        "sectionUuid": "TB_SEC_E",
                        "sectionName": "Counselling Completion",
                        "sectionNameHindi": "काउंसलिंग पूरी होना",
                        "sectionPhase": "PRE_SUBMIT",
                        "isRequired": true,
                        "displayOrder": 5,
                        "hasSubmitButton": true,
                        "isEditable": false,
                        "questions": [
                            {
                                "questionId": 21,
                                "questionUuid": "TB_E_Q1",
                                "questionText": "Counselling completion status",
                                "questionTextHindi": "काउंसलिंग पूरी होने की स्थिति",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 1,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 33,
                                        "optionLabel": "Complete",
                                        "optionLabelHindi": "सम्पूर्ण",
                                        "optionValue": "COMPLETE",
                                        "optionValueHindi": "सम्पूर्ण",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 34,
                                        "optionLabel": "Refused",
                                        "optionLabelHindi": "अस्वीकार करना",
                                        "optionValue": "REFUSED",
                                        "optionValueHindi": "अस्वीकार करना",
                                        "displayOrder": 2,
                                        "conditions": [
                                            {
                                                "conditionId": 1,
                                                "actionType": "DISABLE_SECTION_VALIDATION",
                                                "targetQuestionId": null,
                                                "targetSectionId": 1,
                                                "targetQuestionUuid": null,
                                                "targetSectionUuid": "TB_SEC_A"
                                            },
                                            {
                                                "conditionId": 2,
                                                "actionType": "DISABLE_SECTION_VALIDATION",
                                                "targetQuestionId": null,
                                                "targetSectionId": 2,
                                                "targetQuestionUuid": null,
                                                "targetSectionUuid": "TB_SEC_B"
                                            },
                                            {
                                                "conditionId": 3,
                                                "actionType": "DISABLE_SECTION_VALIDATION",
                                                "targetQuestionId": null,
                                                "targetSectionId": 3,
                                                "targetQuestionUuid": null,
                                                "targetSectionUuid": "TB_SEC_C"
                                            },
                                            {
                                                "conditionId": 4,
                                                "actionType": "DISABLE_SECTION_VALIDATION",
                                                "targetQuestionId": null,
                                                "targetSectionId": 4,
                                                "targetQuestionUuid": null,
                                                "targetSectionUuid": "TB_SEC_D"
                                            },
                                            {
                                                "conditionId": 5,
                                                "actionType": "SHOW_QUESTION",
                                                "targetQuestionId": 22,
                                                "targetSectionId": null,
                                                "targetQuestionUuid": "TB_E_REFUSAL",
                                                "targetSectionUuid": null
                                            }
                                        ]
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 22,
                                "questionUuid": "TB_E_REFUSAL",
                                "questionText": "Reason for refusal",
                                "questionTextHindi": "इनकार का कारण",
                                "questionType": "TEXT",
                                "isMandatory": true,
                                "displayOrder": 2,
                                "maxLength": 300,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": false,
                                "options": [],
                                "validations": [
                                    {
                                        "validationId": 5,
                                        "validationType": "MAX_LENGTH",
                                        "validationParam": "300",
                                        "errorMessage": "Must be 300 characters or fewer"
                                    }
                                ]
                            },
                            {
                                "questionId": 23,
                                "questionUuid": "TB_E_REMARKS",
                                "questionText": "Counsellor remarks",
                                "questionTextHindi": "काउंसलर की टिप्पणी",
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
                                        "validationId": 6,
                                        "validationType": "MAX_LENGTH",
                                        "validationParam": "500",
                                        "errorMessage": "Must be 500 characters or fewer"
                                    }
                                ]
                            }
                        ]
                    }
                ]
            },
            {
                "formId": 2,
                "formUuid": "TB_COUNSELLING_V2",
                "formName": "TB Counselling",
                "formType": "TB_COUNSELLING_V2",
                "isActive": true,
                "followUpDelayDays": 15,
                "versionNumber": 1,
                "sections": [
                    {
                        "sectionId": 6,
                        "sectionUuid": "TB2_SEC_GENERAL_INFO",
                        "sectionName": "General Information",
                        "sectionNameHindi": "सामान्य जानकारी",
                        "sectionPhase": "GENERAL_INFO",
                        "isRequired": true,
                        "displayOrder": 1,
                        "hasSubmitButton": false,
                        "isEditable": false,
                        "questions": [
                            {
                                "questionId": 24,
                                "questionUuid": "TB2_GI_Q1",
                                "questionText": "Has the beneficiary agreed for counselling?",
                                "questionTextHindi": "क्या लाभार्थी काउंसलिंग के लिए सहमत हुआ है?",
                                "questionType": "RADIO",
                                "isMandatory": true,
                                "displayOrder": 1,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 35,
                                        "optionLabel": "Yes",
                                        "optionLabelHindi": "हाँ",
                                        "optionValue": "YES",
                                        "optionValueHindi": "हाँ",
                                        "displayOrder": 1,
                                        "conditions": []
                                    },
                                    {
                                        "optionId": 36,
                                        "optionLabel": "No",
                                        "optionLabelHindi": "नहीं",
                                        "optionValue": "NO",
                                        "optionValueHindi": "नहीं",
                                        "displayOrder": 2,
                                        "conditions": [
                                            {
                                                "conditionId": 6,
                                                "actionType": "DISABLE_SECTION_VALIDATION",
                                                "targetQuestionId": null,
                                                "targetSectionId": 7,
                                                "targetQuestionUuid": null,
                                                "targetSectionUuid": "TB2_SEC_A"
                                            },
                                            {
                                                "conditionId": 7,
                                                "actionType": "DISABLE_SECTION_VALIDATION",
                                                "targetQuestionId": null,
                                                "targetSectionId": 8,
                                                "targetQuestionUuid": null,
                                                "targetSectionUuid": "TB2_SEC_B"
                                            },
                                            {
                                                "conditionId": 8,
                                                "actionType": "DISABLE_SECTION_VALIDATION",
                                                "targetQuestionId": null,
                                                "targetSectionId": 9,
                                                "targetQuestionUuid": null,
                                                "targetSectionUuid": "TB2_SEC_C"
                                            },
                                            {
                                                "conditionId": 9,
                                                "actionType": "DISABLE_SECTION_VALIDATION",
                                                "targetQuestionId": null,
                                                "targetSectionId": 10,
                                                "targetQuestionUuid": null,
                                                "targetSectionUuid": "TB2_SEC_D"
                                            },
                                            {
                                                "conditionId": 10,
                                                "actionType": "DISABLE_SECTION_VALIDATION",
                                                "targetQuestionId": null,
                                                "targetSectionId": 11,
                                                "targetQuestionUuid": null,
                                                "targetSectionUuid": "TB2_SEC_E"
                                            },
                                            {
                                                "conditionId": 11,
                                                "actionType": "SHOW_QUESTION",
                                                "targetQuestionId": 25,
                                                "targetSectionId": null,
                                                "targetQuestionUuid": "TB2_GI_REFUSAL",
                                                "targetSectionUuid": null
                                            }
                                        ]
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 25,
                                "questionUuid": "TB2_GI_REFUSAL",
                                "questionText": "Reason for refusal",
                                "questionTextHindi": "इनकार का कारण",
                                "questionType": "TEXT",
                                "isMandatory": true,
                                "displayOrder": 2,
                                "maxLength": 300,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": false,
                                "options": [],
                                "validations": [
                                    {
                                        "validationId": 7,
                                        "validationType": "MAX_LENGTH",
                                        "validationParam": "300",
                                        "errorMessage": "Must be 300 characters or fewer"
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "sectionId": 7,
                        "sectionUuid": "TB2_SEC_A",
                        "sectionName": "Disease Awareness",
                        "sectionNameHindi": "बीमारी के बारे में जागरूकता",
                        "sectionPhase": "PRE_SUBMIT",
                        "isRequired": true,
                        "displayOrder": 2,
                        "hasSubmitButton": false,
                        "isEditable": false,
                        "questions": [
                            {
                                "questionId": 26,
                                "questionUuid": "TB2_A_Q1",
                                "questionText": "TB disease explained to patient",
                                "questionTextHindi": "मरीज़ को टीबी बीमारी के बारे में समझाया गया।",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 1,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 37,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 27,
                                "questionUuid": "TB2_A_Q2",
                                "questionText": "Transmission route explained",
                                "questionTextHindi": "ट्रांसमिशन के तरीके के बारे में जानकारी",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 2,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 38,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 28,
                                "questionUuid": "TB2_A_Q3",
                                "questionText": "Symptoms explained",
                                "questionTextHindi": "लक्षणों की जानकारी",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 3,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 39,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 29,
                                "questionUuid": "TB2_A_Q4",
                                "questionText": "Treatment duration explained",
                                "questionTextHindi": "इलाज की अवधि के बारे में जानकारी",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 4,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 40,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 30,
                                "questionUuid": "TB2_A_REMARKS",
                                "questionText": "Disease awareness notes",
                                "questionTextHindi": "बीमारी के बारे में जानकारी देने वाले नोट्स",
                                "questionType": "TEXT",
                                "isMandatory": false,
                                "displayOrder": 5,
                                "maxLength": 500,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [],
                                "validations": [
                                    {
                                        "validationId": 8,
                                        "validationType": "MAX_LENGTH",
                                        "validationParam": "500",
                                        "errorMessage": "Must be 500 characters or fewer"
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "sectionId": 8,
                        "sectionUuid": "TB2_SEC_B",
                        "sectionName": "Do's and Don'ts",
                        "sectionNameHindi": "करो और ना करो",
                        "sectionPhase": "PRE_SUBMIT",
                        "isRequired": true,
                        "displayOrder": 3,
                        "hasSubmitButton": false,
                        "isEditable": false,
                        "questions": [
                            {
                                "questionId": 31,
                                "questionUuid": "TB2_B_Q1",
                                "questionText": "Cover mouth while coughing — advised",
                                "questionTextHindi": "खांसते समय मुंह ढकने की सलाह दी जाती है।",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 1,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 41,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 32,
                                "questionUuid": "TB2_B_Q2",
                                "questionText": "Complete full treatment course — advised",
                                "questionTextHindi": "इलाज का पूरा कोर्स पूरा करने की सलाह दी जाती है।",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 2,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 42,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 33,
                                "questionUuid": "TB2_B_Q3",
                                "questionText": "Regular follow-up attendance — advised",
                                "questionTextHindi": "इलाज का पूरा कोर्स पूरा करने की सलाह दी जाती है।",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 3,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 43,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 34,
                                "questionUuid": "TB2_B_Q4",
                                "questionText": "Nutritional guidance provided",
                                "questionTextHindi": "पोषण संबंधी मार्गदर्शन प्रदान किया गया",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 4,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 44,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 35,
                                "questionUuid": "TB2_B_Q5",
                                "questionText": "No smoking / alcohol — advised",
                                "questionTextHindi": "धूम्रपान / शराब न लेने की सलाह दी जाती है।",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 5,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 45,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 36,
                                "questionUuid": "TB2_B_Q6",
                                "questionText": "Isolation precautions explained",
                                "questionTextHindi": "अलगाव सावधानियों के बारे में बताया गया",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 6,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 46,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 37,
                                "questionUuid": "TB2_B_REMARKS",
                                "questionText": "Do's & Don'ts notes",
                                "questionTextHindi": "क्या करें और क्या न करें - नोट्स",
                                "questionType": "TEXT",
                                "isMandatory": false,
                                "displayOrder": 7,
                                "maxLength": 500,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [],
                                "validations": [
                                    {
                                        "validationId": 9,
                                        "validationType": "MAX_LENGTH",
                                        "validationParam": "500",
                                        "errorMessage": "Must be 500 characters or fewer"
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "sectionId": 9,
                        "sectionUuid": "TB2_SEC_C",
                        "sectionName": "Government Schemes",
                        "sectionNameHindi": "सरकारी योजनाएं",
                        "sectionPhase": "PRE_SUBMIT",
                        "isRequired": true,
                        "displayOrder": 4,
                        "hasSubmitButton": false,
                        "isEditable": false,
                        "questions": [
                            {
                                "questionId": 38,
                                "questionUuid": "TB2_C_Q1",
                                "questionText": "Nikshay Poshan Yojana (NPY) eligibility explained",
                                "questionTextHindi": "निक्षय पोषण योजना (एनपीवाई) पात्रता के बारे में बताया गया",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 1,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 47,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 39,
                                "questionUuid": "TB2_C_Q2",
                                "questionText": "DOTS free treatment explained",
                                "questionTextHindi": "DOTS मुफ़्त इलाज के बारे में जानकारी",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 2,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 48,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 40,
                                "questionUuid": "TB2_C_REMARKS",
                                "questionText": "Schemes notes",
                                "questionTextHindi": "योजनाओं के नोट्स",
                                "questionType": "TEXT",
                                "isMandatory": false,
                                "displayOrder": 3,
                                "maxLength": 300,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [],
                                "validations": [
                                    {
                                        "validationId": 10,
                                        "validationType": "MAX_LENGTH",
                                        "validationParam": "300",
                                        "errorMessage": "Must be 300 characters or fewer"
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "sectionId": 10,
                        "sectionUuid": "TB2_SEC_D",
                        "sectionName": "Treatment Regimen",
                        "sectionNameHindi": "इलाज का तरीका",
                        "sectionPhase": "PRE_SUBMIT",
                        "isRequired": true,
                        "displayOrder": 5,
                        "hasSubmitButton": false,
                        "isEditable": false,
                        "questions": [
                            {
                                "questionId": 41,
                                "questionUuid": "TB2_D_Q1",
                                "questionText": "Regimen explained to patient",
                                "questionTextHindi": "मरीज़ को इलाज का तरीका समझाया गया।",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 1,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 49,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 42,
                                "questionUuid": "TB2_D_Q2",
                                "questionText": "Medication names explained",
                                "questionTextHindi": "दवाओं के नामों की जानकारी",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 2,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 50,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 43,
                                "questionUuid": "TB2_D_Q3",
                                "questionText": "Side effects explained",
                                "questionTextHindi": "साइड इफ़ेक्ट्स के बारे में जानकारी",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 3,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 51,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 44,
                                "questionUuid": "TB2_D_Q4",
                                "questionText": "Importance of adherence explained",
                                "questionTextHindi": "निर्देशों का पालन करने का महत्व समझाया गया",
                                "questionType": "CHECKBOX",
                                "isMandatory": true,
                                "displayOrder": 4,
                                "maxLength": null,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [
                                    {
                                        "optionId": 52,
                                        "optionLabel": "Checked",
                                        "optionLabelHindi": "चेक किया गया",
                                        "optionValue": "CHECKED",
                                        "optionValueHindi": "चेक किया गया",
                                        "displayOrder": 1,
                                        "conditions": []
                                    }
                                ],
                                "validations": []
                            },
                            {
                                "questionId": 45,
                                "questionUuid": "TB2_D_REMARKS",
                                "questionText": "Treatment regimen notes",
                                "questionTextHindi": "इलाज के तरीके से जुड़े नोट्स",
                                "questionType": "TEXT",
                                "isMandatory": false,
                                "displayOrder": 5,
                                "maxLength": 300,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [],
                                "validations": [
                                    {
                                        "validationId": 11,
                                        "validationType": "MAX_LENGTH",
                                        "validationParam": "300",
                                        "errorMessage": "Must be 300 characters or fewer"
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "sectionId": 11,
                        "sectionUuid": "TB2_SEC_E",
                        "sectionName": "Impact Assessment",
                        "sectionNameHindi": "प्रभाव का आकलन",
                        "sectionPhase": "PRE_SUBMIT",
                        "isRequired": true,
                        "displayOrder": 6,
                        "hasSubmitButton": true,
                        "isEditable": true,
                        "questions": [
                            {
                                "questionId": 46,
                                "questionUuid": "TB2_E_Q1",
                                "questionText": "How has TB disease impacted the life of this beneficiary",
                                "questionTextHindi": "टीबी बीमारी ने इस लाभार्थी के जीवन को कैसे प्रभावित किया है",
                                "questionType": "TEXT",
                                "isMandatory": false,
                                "displayOrder": 1,
                                "maxLength": 400,
                                "defaultValue": null,
                                "containsPii": false,
                                "visibleByDefault": true,
                                "options": [],
                                "validations": [
                                    {
                                        "validationId": 12,
                                        "validationType": "MAX_LENGTH",
                                        "validationParam": "400",
                                        "errorMessage": "Must be 400 characters or fewer"
                                    }
                                ]
                            }
                        ]
                    }
                ]
            }
        ]
    }
    
""".trimIndent()
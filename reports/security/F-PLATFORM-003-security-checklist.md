<!--
Function: F-PLATFORM-003 security checklist — dictionary and i18n security verification
Time: 2026-07-05
Author: AxeXie
-->

# F-PLATFORM-003 Security Checklist — Dictionary & i18n

- [x] `@PreAuthorize` on all dict and i18n controller methods (platform.dictionary.read/manage, platform.i18n.read/manage)
- [x] Dictionary types versioned (version + published_version); only PUBLISHED types serve lookups
- [x] Dictionary items use stable item_code as code contract, not editable display labels
- [x] Bilingual labels (label_zh_cn, label_en_us) stored per item, not in separate mutable config
- [x] i18n resource keys are stable identifiers; locale + resource_key unique constraint enforced
- [x] No cross-domain table access; governance module owns platform_governance schema only
- [x] Audit records written for dict/i18n mutations (platform.dictionary.changed, platform.i18n.changed)
- [x] R1_NON_MOTION risk level; no edge safety required
- [x] Status machine: DRAFT -> PUBLISHED transition enforced (SM-VERSIONED-CONFIG)
- [x] No secrets or credentials in dictionary/i18n data

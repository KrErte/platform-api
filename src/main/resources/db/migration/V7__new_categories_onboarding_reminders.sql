-- V7: New Estonia-specific categories, onboarding flag, reminder dates

-- 1. New categories: digital_identity, pets, pensions
INSERT INTO vault_categories (slug, name_et, name_en, icon, sort_order, field_template) VALUES
('digital_identity', 'Digitaalne identiteet', 'Digital Identity', '🪪', 11,
 '[{"key":"service_name","label_et":"Teenuse nimi","label_en":"Service name","type":"select","required":true,
   "options":["Smart-ID","Mobile-ID","ID-kaart","DigiDoc","e-Residency","muu"]},
  {"key":"username","label_et":"Kasutajanimi / isikukood","label_en":"Username / ID code","type":"text"},
  {"key":"pin1","label_et":"PIN1 (autentimine)","label_en":"PIN1 (authentication)","type":"text"},
  {"key":"pin2","label_et":"PIN2 (allkirjastamine)","label_en":"PIN2 (signing)","type":"text"},
  {"key":"puk","label_et":"PUK kood","label_en":"PUK code","type":"text"},
  {"key":"expiry_date","label_et":"Kehtivuse lõpp","label_en":"Expiry date","type":"date"},
  {"key":"notes","label_et":"Märkused","label_en":"Notes","type":"textarea"}]'),

('pets', 'Lemmikloomad', 'Pets', '🐾', 12,
 '[{"key":"name","label_et":"Nimi","label_en":"Name","type":"text","required":true},
  {"key":"species","label_et":"Liik","label_en":"Species","type":"select","options":["koer","kass","lind","kala","hamster","küülik","muu"]},
  {"key":"breed","label_et":"Tõug","label_en":"Breed","type":"text"},
  {"key":"chip_number","label_et":"Kiibinumber","label_en":"Chip number","type":"text"},
  {"key":"vet_clinic","label_et":"Veterinaarkliink","label_en":"Vet clinic","type":"text"},
  {"key":"vet_phone","label_et":"Veterinaari telefon","label_en":"Vet phone","type":"text"},
  {"key":"insurance","label_et":"Kindlustus","label_en":"Insurance","type":"text"},
  {"key":"caretaker","label_et":"Hooldaja (pärast surma)","label_en":"Caretaker (after death)","type":"text"},
  {"key":"special_needs","label_et":"Erivajadused","label_en":"Special needs","type":"textarea"}]'),

('pensions', 'Pensionid ja investeeringud', 'Pensions & Investments', '📈', 13,
 '[{"key":"type","label_et":"Tüüp","label_en":"Type","type":"select","required":true,
   "options":["II sammas","III sammas","vabatahtlik pension","investeerimiskonto","krüpto","muu"]},
  {"key":"provider","label_et":"Pakkuja","label_en":"Provider","type":"text","required":true},
  {"key":"fund_name","label_et":"Fondi nimi","label_en":"Fund name","type":"text"},
  {"key":"account_number","label_et":"Konto number","label_en":"Account number","type":"text"},
  {"key":"beneficiary","label_et":"Soodustatud isik","label_en":"Beneficiary","type":"text"},
  {"key":"estimated_value","label_et":"Hinnanguline väärtus","label_en":"Estimated value","type":"text"},
  {"key":"notes","label_et":"Märkused","label_en":"Notes","type":"textarea"}]');

-- 2. Onboarding flag on users
ALTER TABLE users ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT false;

-- 3. Reminder date on vault entries
ALTER TABLE vault_entries ADD COLUMN reminder_date DATE;

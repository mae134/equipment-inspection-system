INSERT INTO equipment (
  equipment_code,
  name,
  manufacturer,
  model,
  location,
  installed_date,
  inspection_cycle_days,
  description,
  active,
  created_at,
  updated_at
) VALUES (
  'EQ-001',
  'モーター設備A',
  'テスト製作所',
  'MTR-100',
  '第1工場',
  '2025-01-15',
  30,
  '画面動作確認用',
  TRUE,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
)
ON CONFLICT (equipment_code) DO NOTHING;

INSERT INTO equipment_inspection_item (
  equipment_id,
  name,
  type,
  unit,
  min_value,
  max_value,
  normal_boolean_value,
  description,
  display_order,
  active,
  created_at,
  updated_at
)
SELECT
  e.id,
  'モーター温度',
  'NUMERIC',
  '℃',
  0,
  80,
  NULL,
  'モーター表面温度',
  1,
  TRUE,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM equipment e
WHERE e.equipment_code = 'EQ-001'
  AND NOT EXISTS (
    SELECT 1
    FROM equipment_inspection_item i
    WHERE i.equipment_id = e.id
      AND i.name = 'モーター温度'
  );

  INSERT INTO equipment_inspection_item (
  equipment_id,
  name,
  type,
  unit,
  min_value,
  max_value,
  normal_boolean_value,
  description,
  display_order,
  active,
  created_at,
  updated_at
)
SELECT
  e.id,
  '油漏れ',
  'BOOLEAN',
  NULL,
  NULL,
  NULL,
  FALSE,
  '油漏れの有無を確認する',
  2,
  TRUE,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM equipment e
WHERE e.equipment_code = 'EQ-001'
  AND NOT EXISTS (
    SELECT 1
    FROM equipment_inspection_item i
    WHERE i.equipment_id = e.id
      AND i.name = '油漏れ'
  );

INSERT INTO users (
  name,
  email,
  password_hash,
  role,
  active,
  created_at,
  updated_at
) VALUES
(
  'テスト点検者',
  'inspector@example.com',
  '$2a$10$xOtqNjmCryrB6.VOOePjjuluyKA94M4PFTcVIUFYx1lWV81gpz2OC',
  'INSPECTOR',
  TRUE,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
),
(
  'テスト管理者',
  'admin@example.com',
  '$2a$10$xOtqNjmCryrB6.VOOePjjuluyKA94M4PFTcVIUFYx1lWV81gpz2OC',
  'ADMIN',
  TRUE,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
)
ON CONFLICT (email) DO NOTHING;

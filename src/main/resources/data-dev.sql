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

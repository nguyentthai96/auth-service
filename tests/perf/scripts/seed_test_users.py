#!/usr/bin/env python3
"""
Seed script: Create test users for performance benchmarking.
Creates 50 users (perfuser1..perfuser50) with bcrypt hashed passwords.
Uses snowflake-like IDs starting from 100000000000000.
"""
import subprocess
import sys

try:
    import bcrypt
except ImportError:
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', '--user', 'bcrypt', '-q'])
    import bcrypt

NUM_USERS = 50
DOMAIN_ID = 842319544233984
BASE_USER_ID = 100000000000000
BASE_UD_ID = 200000000000000

# Generate SQL
sql_lines = []
sql_lines.append("BEGIN;")
sql_lines.append("")
sql_lines.append("-- Ensure default domain exists")
sql_lines.append(f"INSERT INTO domains (id, code, name, description, active, config, status, version) "
                 f"VALUES ({DOMAIN_ID}, 'default', 'Default Domain', 'Default domain for testing', "
                 f"true, '{{}}', 'ACTIVE', 0) ON CONFLICT (code) DO NOTHING;")
sql_lines.append("")
sql_lines.append("-- Create test users")

for i in range(1, NUM_USERS + 1):
    password = f"PerfTest{i}!"
    pw_hash = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=12)).decode()
    user_id = BASE_USER_ID + i
    ud_id = BASE_UD_ID + i
    username = f"perfuser{i}"
    email = f"perfuser{i}@test.local"
    full_name = f"Perf Test User {i}"

    sql_lines.append(
        f"INSERT INTO users (id, username, email, password_hash, full_name, status, "
        f"failed_login_count, active, mfa_enabled, version) "
        f"VALUES ({user_id}, '{username}', '{email}', "
        f"'{pw_hash}', '{full_name}', 'ACTIVE', 0, true, false, 0) "
        f"ON CONFLICT (username) DO NOTHING;"
    )
    sql_lines.append(
        f"INSERT INTO user_domains (id, user_id, domain_id, is_primary, active) "
        f"VALUES ({ud_id}, {user_id}, {DOMAIN_ID}, true, true) "
        f"ON CONFLICT (user_id, domain_id) DO NOTHING;"
    )

sql_lines.append("")
sql_lines.append("COMMIT;")
sql_lines.append("")
sql_lines.append("-- Verify")
sql_lines.append("SELECT count(*) as user_count FROM users WHERE username LIKE 'perfuser%';")
sql_lines.append("SELECT count(*) as domain_count FROM user_domains WHERE domain_id = " + str(DOMAIN_ID) + ";")

sql = "\n".join(sql_lines)

# Write to file
output_path = "/home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/tests/perf/scripts/seed_test_users.sql"
import os
os.makedirs(os.path.dirname(output_path), exist_ok=True)
with open(output_path, 'w') as f:
    f.write(sql)

print(f"Generated SQL for {NUM_USERS} users → {output_path}")

# Execute via podman
result = subprocess.run(
    ['podman', 'exec', '-i', 'auth-postgres', 'psql', '-U', 'auth_user', '-d', 'auth_db'],
    input=sql, capture_output=True, text=True
)
print(result.stdout)
if result.stderr:
    print("STDERR:", result.stderr)

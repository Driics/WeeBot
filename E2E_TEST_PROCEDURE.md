# End-to-End Test Procedure: Reaction Roles System

## Test ID: subtask-5-1
**Date:** 2026-03-08
**Feature:** Reaction Roles System
**Objective:** Verify end-to-end flow from dashboard creation to Discord role assignment

## Prerequisites

### Required Services
- [x] PostgreSQL (database)
- [x] Apache Kafka (messaging)
- [x] sb-worker (Discord bot)
- [x] sb-api (REST API)
- [x] sb-dashboard (Web UI)

### Required Credentials
- Discord bot token (DISCORD_TOKEN)
- Discord OAuth2 credentials (CLIENT_ID, CLIENT_SECRET)
- Test Discord server with bot installed
- Test Discord channel for posting role menus
- Test roles created in Discord server

### Test Data Setup
1. **Guild ID:** [To be filled during test]
2. **Test Channel ID:** [To be filled during test]
3. **Test Role IDs:** [At least 2-3 roles for testing]
4. **Test User:** [User to perform role interaction]

---

## Test Execution Steps

### Phase 1: Service Startup ✓

```bash
# Start all infrastructure and services
docker-compose up -d postgres kafka

# Wait for services to be healthy (check docker-compose ps)
# Verify PostgreSQL is accessible on localhost:5432
# Verify Kafka is accessible on localhost:9092

# Start the backend services
docker-compose up -d sablebot-worker sablebot-api

# Start the dashboard
docker-compose up -d sablebot-dashboard

# Verify all services are running
docker-compose ps
```

**Expected Result:**
- All containers show "healthy" or "running" status
- No error logs in `docker-compose logs`

---

### Phase 2: Database Migration Verification ✓

```bash
# Check that reaction role tables exist
docker-compose exec postgres psql -U sablebot -d sablebot -c "\dt reaction_role*"
```

**Expected Result:**
```
                    List of relations
 Schema |           Name            | Type  |  Owner
--------+---------------------------+-------+----------
 public | reaction_role_group       | table | sablebot
 public | reaction_role_menu        | table | sablebot
 public | reaction_role_menu_item   | table | sablebot
```

---

### Phase 3: Dashboard Access ✓

1. **Open Dashboard:** Navigate to `http://localhost:3000`
2. **Authenticate:** Use Discord OAuth2 to log in
3. **Select Server:** Choose test guild from server list
4. **Navigate:** Go to "Reaction Roles" page

**Expected Result:**
- Dashboard loads without console errors (check browser DevTools)
- Reaction Roles page renders
- Empty state or existing menus displayed

---

### Phase 4: Create Reaction Role Menu ✓

1. **Click "Create New Menu" button**
2. **Fill in menu details:**
   - Title: "Color Roles"
   - Description: "Choose your favorite color!"
   - Type: "BUTTONS" (or REACTIONS or BOTH)
   - Channel ID: [Test channel ID]

3. **Save Menu**

**Expected Result:**
- Menu saved successfully
- Toast notification: "Menu saved"
- Menu appears in list with ID assigned

---

### Phase 5: Add Role Items ✓

1. **Click "Edit" on the created menu**
2. **Add Role Item #1:**
   - Role: [Select test role 1, e.g., "Red"]
   - Emoji/Label: "🔴 Red"
   - Description: "Red color role"
   - Toggleable: Yes
   - Display Order: 1

3. **Add Role Item #2:**
   - Role: [Select test role 2, e.g., "Blue"]
   - Emoji/Label: "🔵 Blue"
   - Description: "Blue color role"
   - Toggleable: Yes
   - Display Order: 2

4. **Add Role Item #3:**
   - Role: [Select test role 3, e.g., "Green"]
   - Emoji/Label: "🟢 Green"
   - Description: "Green color role"
   - Toggleable: Yes
   - Display Order: 3

5. **Save all items**

**Expected Result:**
- All 3 role items saved
- Items displayed in correct order
- No validation errors

---

### Phase 6: Post Menu to Discord Channel ✓

1. **Click "Post to Channel" button**
2. **Wait for confirmation**

**Expected Result:**
- API request succeeds (check Network tab)
- Toast notification: "Menu posted successfully"
- Message ID returned and stored

---

### Phase 7: Verify Discord Message ✓

1. **Open Discord client/web**
2. **Navigate to test channel**
3. **Find posted role menu message**

**Expected Result:**
- Message appears in channel from bot
- Message contains:
  - Title: "Color Roles"
  - Description: "Choose your favorite color!"
  - 3 buttons (or reactions): 🔴 Red, 🔵 Blue, 🟢 Green
  - Buttons are clickable

**Screenshot Location:** `./test-evidence/discord-menu-posted.png`

---

### Phase 8: Test Role Assignment (Button Click) ✓

1. **As test user, click "🔴 Red" button**
2. **Check for ephemeral response**
3. **Check Discord role assignment**

**Expected Result:**
- Ephemeral message: "Role assigned: Red" (or similar)
- User receives "Red" role in Discord
- Role appears in member list with red color
- No errors in worker logs

**Verification Command:**
```bash
# Check worker logs for role assignment
docker-compose logs sablebot-worker | grep -i "role"
```

---

### Phase 9: Test Role Toggle ✓

1. **Click "🔴 Red" button again**
2. **Check role removal**

**Expected Result:**
- Ephemeral message: "Role removed: Red"
- "Red" role removed from user
- User no longer has red color in member list

---

### Phase 10: Test Multiple Roles ✓

1. **Click "🔵 Blue" button**
2. **Click "🟢 Green" button**
3. **Verify both roles assigned**

**Expected Result:**
- User has both "Blue" and "Green" roles
- No conflicts (since items are in no exclusive group)

---

### Phase 11: Test Reaction-based Assignment ✓

**If menu type is REACTIONS or BOTH:**

1. **Remove all roles from test user**
2. **Add emoji reaction to message (🔴)**
3. **Check role assignment**

**Expected Result:**
- User receives "Red" role
- Role assigned automatically

4. **Remove emoji reaction**
5. **Check role removal (if toggleable)**

**Expected Result:**
- User loses "Red" role (if item is toggleable)

---

### Phase 12: Database Verification ✓

```bash
# Check database for menu and items
docker-compose exec postgres psql -U sablebot -d sablebot -c "
SELECT m.id, m.title, m.menu_type, m.channel_id, m.message_id,
       COUNT(i.id) as item_count
FROM reaction_role_menu m
LEFT JOIN reaction_role_menu_item i ON m.id = i.menu_id
GROUP BY m.id;
"

# Check menu items
docker-compose exec postgres psql -U sablebot -d sablebot -c "
SELECT id, menu_id, role_id, label, emoji, toggleable, display_order
FROM reaction_role_menu_item
ORDER BY menu_id, display_order;
"
```

**Expected Result:**
- Menu record exists with correct channel_id and message_id
- 3 menu item records exist with correct role mappings

---

### Phase 13: Audit Log Verification ✓

```bash
# Check audit logs for role assignment events
docker-compose exec postgres psql -U sablebot -d sablebot -c "
SELECT action_type, guild_id, user_id, details, created_at
FROM audit_action
WHERE action_type IN ('MEMBER_ROLE_ASSIGN', 'MEMBER_ROLE_REMOVE')
ORDER BY created_at DESC
LIMIT 10;
"
```

**Expected Result:**
- Audit log entries exist for each role assignment/removal
- Entries contain:
  - Correct action_type
  - Correct user_id
  - Role details in JSON
  - Timestamp of action

---

## Test Results Summary

### ✅ PASS Criteria
- [ ] All services start successfully
- [ ] Database tables created via migration
- [ ] Dashboard loads and renders reaction-roles page
- [ ] Menu created successfully via dashboard
- [ ] Role items added successfully
- [ ] Menu posted to Discord channel
- [ ] Message appears in Discord with buttons/reactions
- [ ] Button click assigns role to user
- [ ] Button click again removes role (toggle)
- [ ] Reaction add assigns role (if applicable)
- [ ] Reaction remove removes role (if applicable)
- [ ] Database contains correct menu/item data
- [ ] Audit logs capture role assignment events

### ❌ FAIL Criteria
- [ ] Any service fails to start
- [ ] Database migration errors
- [ ] Dashboard shows console errors
- [ ] Menu creation fails
- [ ] Items fail to save
- [ ] Post to channel fails
- [ ] No message appears in Discord
- [ ] Button clicks don't assign roles
- [ ] Toggle behavior doesn't work
- [ ] Audit logs missing events

---

## Known Limitations / Notes

1. **Discord Bot Permissions Required:**
   - MANAGE_ROLES permission
   - Bot role must be higher than roles being assigned
   - Bot must have access to target channel

2. **Manual Steps Required:**
   - This test requires actual Discord OAuth2 authentication
   - Requires access to a test Discord server
   - Requires manual button clicks in Discord client

3. **Environment Variables:**
   - DISCORD_TOKEN must be set
   - DISCORD_CLIENT_ID and DISCORD_CLIENT_SECRET must be set
   - All environment variables must be configured in docker-compose or .env

---

## Troubleshooting

### Issue: Services won't start
**Solution:** Check docker-compose logs for errors, verify environment variables

### Issue: Database migration fails
**Solution:** Run `./gradlew :sb-worker:bootRun` locally to see detailed errors

### Issue: Dashboard can't connect to API
**Solution:** Verify NEXT_PUBLIC_API_URL is set correctly, check CORS settings

### Issue: Bot doesn't respond to button clicks
**Solution:**
- Check worker logs for errors
- Verify bot has MANAGE_ROLES permission
- Verify bot role is higher than target roles
- Verify Kafka is running and accessible

### Issue: Roles not assigned
**Solution:**
- Check ReactionRoleListener logs
- Verify role IDs are correct
- Verify user permissions
- Check validation logic in ReactionRoleServiceImpl

---

## Test Evidence

Store screenshots and logs in `./test-evidence/` directory:
- `dashboard-menu-list.png` - Menu list page
- `dashboard-menu-builder.png` - Menu builder with items
- `discord-menu-posted.png` - Posted message in Discord
- `discord-role-assigned.png` - User with assigned role
- `worker-logs.txt` - Worker logs during test
- `api-logs.txt` - API logs during test
- `database-state.sql` - Database dump after test

---

## Automation Potential

Future improvements could include:
- Discord.js test bot to simulate user interactions
- API integration tests using TestRestTemplate
- Database state assertions using Testcontainers
- Playwright tests for dashboard UI

---

## Sign-off

**Tester:** _______________________
**Date:** _______________________
**Result:** PASS / FAIL
**Notes:** _______________________


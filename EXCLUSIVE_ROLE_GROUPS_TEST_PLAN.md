# Exclusive Role Groups Test Plan

## Test ID: subtask-5-2
**Date:** 2026-03-08
**Feature:** Reaction Roles System - Exclusive Role Groups
**Objective:** Verify that exclusive role groups enforce single-role selection (e.g., color roles where users can only have one color at a time)

---

## Overview

Exclusive role groups ensure that when a user selects a role from a group, any other roles they have from that same group are automatically removed. This is commonly used for:
- **Color roles** (Red, Blue, Green - user can only have one color)
- **Tier roles** (Bronze, Silver, Gold - user can only have one tier)
- **Platform roles** (PC, Console, Mobile - user can only select one platform)

---

## Implementation Details

### Code Reference

The exclusive group logic is implemented in `ReactionRoleServiceImpl.kt`:

**Location:** Lines 342-347
```kotlin
else -> {
    // Add the role
    // If the item is in an exclusive group, remove other roles from the group first
    if (item.groupId != null) {
        removeGroupRoles(member, item.groupId!!)
    }

    val assigned = assignRole(member, role)
    // ...
}
```

**`removeGroupRoles` method:** Lines 392-410
```kotlin
@Transactional
override fun removeGroupRoles(member: Member, groupId: Long): Int {
    val groupItems = getMenuItemsByGroup(groupId)
    var removedCount = 0

    groupItems.forEach { item ->
        val role = getRole(member.guild, item.roleId)
        if (role != null && memberHasRole(member, item.roleId)) {
            if (removeRole(member, role)) {
                removedCount++
            }
        }
    }

    if (removedCount > 0) {
        log.info { "Removed $removedCount roles from group $groupId for member ${member.id}" }
    }

    return removedCount
}
```

**Key Behavior:**
1. When a user clicks a role button/reaction, the service checks if the item has a `groupId`
2. If yes, it calls `removeGroupRoles()` to remove all other roles from that group
3. Then it assigns the new role
4. This ensures only one role from the group is active at any time

---

## Prerequisites

### Required Services
- [x] PostgreSQL (database)
- [x] Apache Kafka (messaging)
- [x] sb-worker (Discord bot with MANAGE_ROLES permission)
- [x] sb-api (REST API)
- [x] sb-dashboard (Web UI)

### Test Data Setup

#### 1. Create Test Discord Roles
In your test Discord server, create **3 color roles**:
- 🔴 **Red** (with red color)
- 🔵 **Blue** (with blue color)
- 🟢 **Green** (with green color)

Note the role IDs for each (right-click role → Copy ID with Developer Mode enabled).

#### 2. Test User
- At least one test user account (can be your own alternate account)
- User must be a member of the test server
- User should initially have **no color roles**

#### 3. Test Channel
- Create or identify a test channel where the bot can post messages
- Note the channel ID

---

## Test Execution Steps

### Phase 1: Create Exclusive Role Group 🎯

1. **Navigate to Dashboard:**
   - Go to `http://localhost:3000/dashboard/[guildId]/reaction-roles`

2. **Open Group Manager:**
   - Click "Manage Groups" button (in RoleMenuBuilder component)

3. **Create New Group:**
   - Click "Create New Group"
   - **Group Name:** `Color Roles`
   - **Description:** `Exclusive color selection - users can only have one color at a time`
   - Click "Save"

4. **Verify Group Creation:**
   - Group appears in the groups list
   - Group has an ID assigned
   - Toast notification: "Group created successfully"

**Expected Result:**
- ✅ Group "Color Roles" created
- ✅ Group ID returned (note this for next steps)
- ✅ No errors in browser console
- ✅ No errors in API logs

**Database Verification:**
```bash
docker-compose exec postgres psql -U sablebot -d sablebot -c "
SELECT id, guild_id, name, description, active, created_at
FROM reaction_role_group
WHERE name = 'Color Roles';
"
```

Expected output:
```
 id | guild_id |    name     |           description                          | active | created_at
----+----------+-------------+------------------------------------------------+--------+------------
  1 | [guild]  | Color Roles | Exclusive color selection - users can only...  | t      | 2026-03-08...
```

---

### Phase 2: Create Reaction Role Menu with Grouped Items 🎨

1. **Create Menu:**
   - Click "Create New Menu"
   - **Title:** `Choose Your Color`
   - **Description:** `Pick one color role. Selecting a new color will remove your old one.`
   - **Type:** `BUTTONS` (recommended for easier testing)
   - **Channel ID:** [Your test channel ID]
   - Click "Save Menu"

2. **Add Role Item #1 (Red):**
   - Click "Add Item"
   - **Role:** Select "🔴 Red" role
   - **Emoji/Label:** `🔴 Red`
   - **Description:** `Red color role`
   - **Display Order:** `1`
   - **Toggleable:** `No` (exclusive groups typically aren't toggleable)
   - **Role Group:** Select `Color Roles` (the group created in Phase 1)
   - **Required Roles:** Leave empty
   - Click "Save"

3. **Add Role Item #2 (Blue):**
   - Click "Add Item"
   - **Role:** Select "🔵 Blue" role
   - **Emoji/Label:** `🔵 Blue`
   - **Description:** `Blue color role`
   - **Display Order:** `2`
   - **Toggleable:** `No`
   - **Role Group:** Select `Color Roles` (same group!)
   - Click "Save"

4. **Add Role Item #3 (Green):**
   - Click "Add Item"
   - **Role:** Select "🟢 Green" role
   - **Emoji/Label:** `🟢 Green`
   - **Description:** `Green color role`
   - **Display Order:** `3`
   - **Toggleable:** `No`
   - **Role Group:** Select `Color Roles` (same group!)
   - Click "Save"

**Expected Result:**
- ✅ All 3 items saved successfully
- ✅ All items show group badge: "Group: Color Roles"
- ✅ Items appear in correct order (1, 2, 3)

**Database Verification:**
```bash
docker-compose exec postgres psql -U sablebot -d sablebot -c "
SELECT i.id, i.role_id, i.label, i.toggleable, i.display_order,
       g.name as group_name
FROM reaction_role_menu_item i
LEFT JOIN reaction_role_group g ON i.group_id = g.id
WHERE i.menu_id = [menu_id]
ORDER BY i.display_order;
"
```

Expected output:
```
 id | role_id | label   | toggleable | display_order | group_name
----+---------+---------+------------+---------------+-------------
  1 | [red]   | 🔴 Red  | f          | 1             | Color Roles
  2 | [blue]  | 🔵 Blue | f          | 2             | Color Roles
  3 | [green] | 🟢 Green| f          | 3             | Color Roles
```

**Critical Check:** All items must have the **same `group_id`** for exclusivity to work!

---

### Phase 3: Post Menu to Discord Channel 📤

1. **Post Menu:**
   - Click "Post to Channel" button
   - Wait for success notification

2. **Verify in Discord:**
   - Open Discord client
   - Navigate to test channel
   - Verify message posted with 3 buttons: 🔴 Red, 🔵 Blue, 🟢 Green

**Expected Result:**
- ✅ Message appears in Discord
- ✅ Message has title "Choose Your Color"
- ✅ Message has description about exclusive selection
- ✅ 3 buttons visible and clickable

---

### Phase 4: Test Exclusive Group Behavior - Initial Assignment ✅

**Test User:** User currently has **NO color roles**

1. **Assign First Role (Red):**
   - As test user, click "🔴 Red" button

2. **Verify Role Assignment:**
   - Check ephemeral response: "✅ Role **Red** has been added to you!"
   - Check Discord member list: User should have Red role (appears with red color)
   - User should have **1 role** (Red)

**Expected Result:**
- ✅ User receives Red role
- ✅ User color changes to red in member list
- ✅ No errors in bot logs

**Worker Logs Verification:**
```bash
docker-compose logs sablebot-worker | grep -i "role" | tail -20
```

Look for:
```
Assigned role [red_role_id] to member [user_id] via button in guild [guild_id]
```

---

### Phase 5: Test Exclusive Group Behavior - Role Switching 🔄

**Test User:** User currently has **Red role**

1. **Switch to Blue Role:**
   - As test user, click "🔵 Blue" button

2. **Verify Role Removal & Assignment:**
   - Check ephemeral response: "✅ Role **Blue** has been added to you!"
   - Check Discord member list:
     - User should **NO LONGER** have Red role ❌
     - User should **HAVE** Blue role ✅
     - User color should change from red to blue
   - User should have **1 role** (Blue only)

**Expected Result:**
- ✅ Red role removed automatically
- ✅ Blue role added
- ✅ User has only 1 color role (Blue)
- ✅ User color changes to blue

**Worker Logs Verification:**
```bash
docker-compose logs sablebot-worker | grep -i "group" | tail -20
```

Look for:
```
Removed 1 roles from group [group_id] for member [user_id]
Assigned role [blue_role_id] to member [user_id] via button in guild [guild_id]
```

**This is the CRITICAL test** - it proves exclusive group logic works!

---

### Phase 6: Test Exclusive Group Behavior - Second Switch 🔄

**Test User:** User currently has **Blue role**

1. **Switch to Green Role:**
   - As test user, click "🟢 Green" button

2. **Verify Role Removal & Assignment:**
   - Blue role removed ❌
   - Green role added ✅
   - User color changes to green

**Expected Result:**
- ✅ Blue role removed automatically
- ✅ Green role added
- ✅ User has only 1 color role (Green only)

---

### Phase 7: Test Exclusive Group Behavior - Back to First Role ↩️

**Test User:** User currently has **Green role**

1. **Switch Back to Red Role:**
   - As test user, click "🔴 Red" button

2. **Verify Role Removal & Assignment:**
   - Green role removed ❌
   - Red role added ✅
   - User color changes back to red

**Expected Result:**
- ✅ Green role removed automatically
- ✅ Red role added
- ✅ User has only 1 color role (Red only)
- ✅ Full cycle completed successfully

---

### Phase 8: Database Audit Log Verification 📊

Check that all role removals and assignments were logged:

```bash
docker-compose exec postgres psql -U sablebot -d sablebot -c "
SELECT
    action_type,
    user_id,
    target_user_id,
    details->>'role_name' as role_name,
    details->>'reaction_role' as is_reaction_role,
    created_at
FROM audit_action
WHERE action_type IN ('MEMBER_ROLE_ASSIGN', 'MEMBER_ROLE_REMOVE')
  AND details->>'reaction_role' = 'true'
ORDER BY created_at DESC
LIMIT 20;
"
```

**Expected Result:**

Should show a sequence like:
```
 action_type          | user_id | target_user_id | role_name | is_reaction_role | created_at
----------------------+---------+----------------+-----------+------------------+------------
 MEMBER_ROLE_ASSIGN   | null    | [user]         | Red       | true             | [time6]
 MEMBER_ROLE_REMOVE   | null    | [user]         | Green     | true             | [time5]
 MEMBER_ROLE_ASSIGN   | null    | [user]         | Green     | true             | [time4]
 MEMBER_ROLE_REMOVE   | null    | [user]         | Blue      | true             | [time3]
 MEMBER_ROLE_ASSIGN   | null    | [user]         | Blue      | true             | [time2]
 MEMBER_ROLE_REMOVE   | null    | [user]         | Red       | true             | [time1]
 MEMBER_ROLE_ASSIGN   | null    | [user]         | Red       | true             | [time0]
```

Each role switch should create **2 audit events**:
1. `MEMBER_ROLE_REMOVE` for the old role
2. `MEMBER_ROLE_ASSIGN` for the new role

---

### Phase 9: Edge Case Testing - Direct Role Assignment 🧪

This tests that the exclusive group logic **only applies** when using reaction roles, not when roles are manually assigned by admins.

1. **Manual Role Assignment (Outside Reaction Roles):**
   - As a server admin, manually assign Blue role to the test user (via Discord UI)
   - User should now have **both Red and Blue** roles

2. **Use Reaction Role System:**
   - As test user, click "🟢 Green" button

3. **Verify Cleanup:**
   - Both Red and Blue should be removed ❌
   - Green should be added ✅
   - User should only have Green role

**Expected Result:**
- ✅ System removes ALL roles from the exclusive group (both Red and Blue)
- ✅ Only Green role remains
- ✅ System cleans up inconsistent state

---

### Phase 10: Multiple Users Testing 👥

Test that exclusive groups work independently for different users:

1. **User A Actions:**
   - User A clicks "🔴 Red" button
   - User A receives Red role

2. **User B Actions (simultaneously):**
   - User B clicks "🔵 Blue" button
   - User B receives Blue role

3. **Verify Independence:**
   - User A still has Red role (unchanged)
   - User B has Blue role
   - Each user's role selection is independent

**Expected Result:**
- ✅ User A has Red role
- ✅ User B has Blue role
- ✅ No interference between users
- ✅ Exclusive groups are per-user, not global

---

## Test Results Summary

### ✅ PASS Criteria

- [ ] **Group Creation:** Exclusive role group created successfully in dashboard
- [ ] **Menu Creation:** Role menu created with 3 items all assigned to same group
- [ ] **Discord Posting:** Menu posted to Discord with 3 buttons
- [ ] **Initial Assignment:** First role (Red) assigned successfully
- [ ] **First Switch:** Red removed, Blue added (1→2 transition)
- [ ] **Second Switch:** Blue removed, Green added (2→3 transition)
- [ ] **Third Switch:** Green removed, Red added (3→1 transition)
- [ ] **Single Role Enforcement:** User NEVER has more than 1 role from the group
- [ ] **Audit Logging:** All assignments and removals logged in database
- [ ] **Multi-User:** Different users can have different roles from the same group
- [ ] **Edge Case:** Manual role assignment cleaned up by system

### ❌ FAIL Criteria

- [ ] User accumulates multiple roles from the same exclusive group
- [ ] Role not removed when switching to different group role
- [ ] User ends up with no roles after clicking button
- [ ] Wrong role removed (role from different group)
- [ ] System error when switching roles
- [ ] Audit logs missing removal events
- [ ] One user's selection affects another user's roles

---

## Key Differences from Basic Reaction Roles

| Feature | Basic Reaction Role | Exclusive Group Role |
|---------|---------------------|---------------------|
| **Multiple Roles** | ✅ User can have many | ❌ User can have only ONE from group |
| **Toggleable** | ✅ Commonly used | ❌ Typically disabled |
| **Role Removal** | Only if toggleable | ✅ Automatic on role switch |
| **Use Cases** | Notification roles, interest roles | Color roles, tier roles, platform selection |
| **Group ID** | `null` | Set to group ID |

---

## Technical Notes

### Database Schema

**reaction_role_group table:**
```sql
CREATE TABLE reaction_role_group (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(200),
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

**reaction_role_menu_item.group_id:**
- Foreign key to `reaction_role_group.id`
- When `NULL` → item is NOT in an exclusive group
- When set → item IS in an exclusive group

### Service Layer Logic

The exclusive group enforcement happens in `ReactionRoleServiceImpl.handleRoleInteraction()`:

1. **Check if item has `groupId`:**
   ```kotlin
   if (item.groupId != null) {
       removeGroupRoles(member, item.groupId!!)
   }
   ```

2. **Remove all group roles:**
   - Query all menu items with the same `group_id`
   - For each item, check if member has that role
   - Remove each role found

3. **Assign new role:**
   - After cleanup, assign the clicked role
   - This ensures only the new role exists

### Listener Flow

**Button Click Flow:**
```
User clicks button
  → ButtonInteractionEvent fired
  → ReactionRoleListener.onButtonInteraction()
  → Extract menuItemId from componentId
  → Get menu item from database
  → Call reactionRoleService.handleRoleInteraction()
    → [Exclusive group logic executes here]
    → removeGroupRoles() if groupId != null
    → assignRole()
  → Reply with ephemeral message
```

---

## Troubleshooting

### Issue: User has multiple roles from exclusive group

**Possible Causes:**
1. Menu items don't all have the same `group_id`
2. Roles were manually assigned outside reaction role system
3. Database inconsistency

**Solution:**
1. Verify all items have same `group_id`:
   ```sql
   SELECT id, label, group_id FROM reaction_role_menu_item WHERE menu_id = [menu_id];
   ```
2. Manually remove extra roles
3. Re-test with clean state

---

### Issue: No roles removed when clicking different role

**Possible Causes:**
1. `group_id` is `NULL` (not set)
2. Service layer not calling `removeGroupRoles()`
3. Bot lacks MANAGE_ROLES permission

**Solution:**
1. Check item configuration in database
2. Check worker logs for `removeGroupRoles` call
3. Verify bot permissions

---

### Issue: Wrong role removed

**Possible Causes:**
1. Items from different menus share the same `group_id`
2. Database corruption

**Solution:**
1. Verify group membership:
   ```sql
   SELECT i.id, i.label, i.group_id, g.name
   FROM reaction_role_menu_item i
   LEFT JOIN reaction_role_group g ON i.group_id = g.id;
   ```
2. Ensure each menu uses its own groups

---

## Test Evidence

Store screenshots and logs in `./test-evidence/exclusive-groups/`:

- `01-group-creation.png` - Dashboard showing created group
- `02-menu-with-items.png` - Menu builder showing all 3 items with group badges
- `03-discord-menu.png` - Posted menu in Discord
- `04-red-role-assigned.png` - User with Red role
- `05-switch-to-blue.png` - User with Blue role (Red removed)
- `06-switch-to-green.png` - User with Green role (Blue removed)
- `07-audit-logs.png` - Database audit log query results
- `worker-logs-exclusive-groups.txt` - Worker logs showing removeGroupRoles calls

---

## Automation Potential

This test could be automated with:

1. **API Integration Tests:**
   ```kotlin
   @Test
   fun `exclusive group removes other roles when assigning new role`() {
       // Create group, menu, items
       // Simulate button click with user who has roleA from group
       // Assert roleA removed and roleB assigned
   }
   ```

2. **Discord.js Bot Simulation:**
   - Create test bot that simulates user button clicks
   - Verify role changes programmatically

3. **Database State Assertions:**
   - Use Testcontainers for PostgreSQL
   - Assert audit log entries created

---

## Acceptance Criteria ✅

This test is considered **PASSED** when:

1. ✅ Exclusive role group can be created via dashboard
2. ✅ Multiple menu items can be assigned to the same group
3. ✅ User clicking a group role button receives that role
4. ✅ User clicking a different group role button:
   - Loses previous role from the group
   - Receives new role
   - Has only ONE role from the group
5. ✅ Exclusive group behavior works for multiple role switches (Red→Blue→Green→Red)
6. ✅ Audit logs capture both removals and assignments
7. ✅ Multiple users can independently select different roles from the same group
8. ✅ No errors in worker or API logs during normal operation

---

## Sign-off

**Tester:** _______________________
**Date:** _______________________
**Result:** PASS / FAIL
**Notes:** _______________________

---

## Related Documentation

- **Main E2E Test:** `E2E_TEST_PROCEDURE.md`
- **Integration Summary:** `INTEGRATION_TEST_SUMMARY.md`
- **Implementation:** `sb-common-worker/src/main/kotlin/ru/sablebot/common/worker/modules/reactionroles/service/ReactionRoleServiceImpl.kt`
- **Spec:** `.auto-claude/specs/005-reaction-roles-system/spec.md`

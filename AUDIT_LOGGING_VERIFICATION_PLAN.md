# Audit Logging Verification Test Plan

**Feature:** Reaction Roles System - Audit Logging
**Test Type:** Integration Testing - Manual Verification
**Subtask:** subtask-5-5
**Status:** Ready for Execution

---

## Overview

This test plan verifies that all reaction role events (role assignments and removals) are properly logged to the audit system. The audit logging implementation captures comprehensive metadata about each role interaction, enabling server administrators to track and review all reaction role activity.

### Audit Events for Reaction Roles

The system logs two types of audit events:

1. **MEMBER_ROLE_ASSIGN** - When a user receives a role via reaction/button
2. **MEMBER_ROLE_REMOVE** - When a toggleable role is removed via reaction/button

### Logged Information

Each audit log entry includes:
- **Action Type**: MEMBER_ROLE_ASSIGN or MEMBER_ROLE_REMOVE
- **Action Date**: Timestamp of when the event occurred
- **Source User**: The member who triggered the interaction (may be null for reactions)
- **Target User**: The member who received/lost the role
- **Channel**: The channel where the reaction role menu is located
- **Attributes** (stored as JSONB):
  - `role_id`: Discord role ID (Snowflake)
  - `role_name`: Human-readable role name
  - `reaction_role`: `"true"` to identify reaction role events

---

## Implementation Details

### Code References

#### AuditActionType Enum
**File:** `sb-common/src/main/kotlin/ru/sablebot/common/model/AuditActionType.kt`
```kotlin
MEMBER_ROLE_ASSIGN("#7DE8B8"),  // Green color for assignments
MEMBER_ROLE_REMOVE("#E8B87D"),  // Orange color for removals
```

#### Audit Logging Method
**File:** `sb-common-worker/src/main/kotlin/ru/sablebot/common/worker/modules/reactionroles/service/ReactionRoleServiceImpl.kt`

**Method:** `logRoleAction()` (lines 435-458)
```kotlin
private fun logRoleAction(
    guild: Guild,
    member: Member,
    role: Role,
    channel: TextChannel?,
    author: Member?,
    added: Boolean
) {
    val actionType = if (added) {
        AuditActionType.MEMBER_ROLE_ASSIGN
    } else {
        AuditActionType.MEMBER_ROLE_REMOVE
    }

    auditService
        .log(guild.idLong, actionType)
        .withUser(author)           // Who triggered it (null for reactions)
        .withTargetUser(member)     // Who received/lost the role
        .withChannel(channel)       // Where it happened
        .withAttribute("role_id", role.id)
        .withAttribute("role_name", role.name)
        .withAttribute("reaction_role", "true")
        .save()
}
```

#### Integration Points

**Called from `handleRoleInteraction()` at:**
- **Line 319**: When toggleable role is removed (added = false)
- **Line 351**: When role is assigned (added = true)

**Important:** Validation failures do NOT create audit log entries. Only successful role assignments/removals are logged.

---

## Prerequisites

### System Requirements
1. All services running:
   - PostgreSQL database
   - Kafka (if required for message handling)
   - sb-worker (Discord bot)
   - sb-api (REST API)
   - sb-dashboard (optional for menu creation)

2. Discord Setup:
   - Bot has MANAGE_ROLES permission
   - Bot role is higher than roles being assigned
   - Test user has MANAGE_SERVER permission (for /audit commands)
   - At least one reaction role menu posted in a channel

3. Audit System Configured:
   - Run `/audit enable` in Discord to enable audit logging
   - Optionally set audit channel with `/audit setchannel`
   - Configure monitored actions with `/audit setactions` (ensure MEMBER_ROLE_ASSIGN and MEMBER_ROLE_REMOVE are enabled)

### Database Access
You need direct database access to verify audit_action table entries:
```bash
psql -h localhost -U sablebot -d sablebot
# or via Docker:
docker exec -it sablebot-postgres psql -U sablebot -d sablebot
```

### Test Data Setup
Create a simple reaction role menu with:
- 1 toggleable role item (e.g., "Notifications")
- 1 non-toggleable role item (e.g., "Member")
- Button or reaction-based interaction

---

## Test Procedure

### Phase 1: Verify Audit System Configuration

**Objective:** Ensure audit system is enabled and configured

**Steps:**
1. In Discord test channel, run: `/audit status`
2. Verify response shows:
   - ✅ Audit system enabled: YES
   - Configured actions include: MEMBER_ROLE_ASSIGN, MEMBER_ROLE_REMOVE

**Expected Result:**
- Audit system is enabled
- Reaction role action types are monitored

**Database Verification:**
```sql
-- Check audit config for your guild
SELECT
    guild_id,
    enabled,
    channel_id,
    monitored_actions
FROM audit_config
WHERE guild_id = YOUR_GUILD_ID;
```

**Pass Criteria:**
- [ ] `/audit status` shows enabled
- [ ] MEMBER_ROLE_ASSIGN in monitored_actions
- [ ] MEMBER_ROLE_REMOVE in monitored_actions

---

### Phase 2: Test Role Assignment Audit Logging

**Objective:** Verify audit log entry is created when role is assigned

**Steps:**
1. Navigate to channel with reaction role menu
2. Click button or add reaction for a role you don't have
3. Verify you receive the role in Discord
4. Check database for audit log entry

**Expected Result:**
- Role assigned successfully
- Audit log entry created with action_type = MEMBER_ROLE_ASSIGN

**Database Verification:**
```sql
-- Find recent role assignment audit logs for your guild
SELECT
    id,
    action_date,
    action_type,
    target_user_id,
    target_user_name,
    source_user_id,
    source_user_name,
    channel_id,
    channel_name,
    attributes
FROM audit_action
WHERE guild_id = YOUR_GUILD_ID
  AND action_type = 'MEMBER_ROLE_ASSIGN'
ORDER BY action_date DESC
LIMIT 10;
```

**Expected Database Record:**
```
action_type: MEMBER_ROLE_ASSIGN
target_user_id: <your Discord user ID>
target_user_name: <your Discord username>
channel_id: <channel where menu is posted>
attributes: {
  "role_id": "<role snowflake ID>",
  "role_name": "<role name>",
  "reaction_role": "true"
}
```

**Pass Criteria:**
- [ ] Audit log entry exists
- [ ] action_type is MEMBER_ROLE_ASSIGN
- [ ] target_user_id matches your Discord ID
- [ ] attributes.role_id matches the assigned role
- [ ] attributes.reaction_role is "true"
- [ ] channel_id matches the menu channel

---

### Phase 3: Test Role Removal Audit Logging (Toggleable Roles)

**Objective:** Verify audit log entry is created when toggleable role is removed

**Steps:**
1. Click the same button/reaction again (must be a toggleable role item)
2. Verify role is removed from you in Discord
3. Check database for audit log entry

**Expected Result:**
- Role removed successfully
- Audit log entry created with action_type = MEMBER_ROLE_REMOVE

**Database Verification:**
```sql
-- Find recent role removal audit logs
SELECT
    id,
    action_date,
    action_type,
    target_user_id,
    target_user_name,
    attributes
FROM audit_action
WHERE guild_id = YOUR_GUILD_ID
  AND action_type = 'MEMBER_ROLE_REMOVE'
ORDER BY action_date DESC
LIMIT 10;
```

**Expected Database Record:**
```
action_type: MEMBER_ROLE_REMOVE
target_user_id: <your Discord user ID>
attributes: {
  "role_id": "<role snowflake ID>",
  "role_name": "<role name>",
  "reaction_role": "true"
}
```

**Pass Criteria:**
- [ ] Audit log entry exists
- [ ] action_type is MEMBER_ROLE_REMOVE
- [ ] target_user_id matches your Discord ID
- [ ] attributes.role_id matches the removed role
- [ ] attributes.reaction_role is "true"

---

### Phase 4: Test Button vs Reaction Interaction Metadata

**Objective:** Verify audit logs correctly differentiate button and reaction interactions

**Steps:**
1. Assign a role via **button click**
2. Check audit log entry for source_user_id
3. Remove the role (if toggleable)
4. Assign the same role via **reaction** (add emoji)
5. Check audit log entry for source_user_id

**Expected Result:**
- Button interactions: source_user_id populated (author is the button clicker)
- Reaction interactions: source_user_id may be null (reactions don't always provide author context)

**Database Verification:**
```sql
-- Compare button vs reaction audit logs
SELECT
    action_date,
    action_type,
    source_user_id,
    source_user_name,
    target_user_id,
    target_user_name,
    attributes->>'role_name' as role_name
FROM audit_action
WHERE guild_id = YOUR_GUILD_ID
  AND action_type IN ('MEMBER_ROLE_ASSIGN', 'MEMBER_ROLE_REMOVE')
ORDER BY action_date DESC
LIMIT 5;
```

**Pass Criteria:**
- [ ] Button click logs have source_user_id populated
- [ ] Both button and reaction logs have correct target_user_id
- [ ] All logs have reaction_role: "true" in attributes

---

### Phase 5: Verify Audit Log Timestamps and Ordering

**Objective:** Ensure audit logs are created in correct chronological order

**Steps:**
1. Perform sequence: Assign Role A → Remove Role A → Assign Role B
2. Query audit logs with ORDER BY action_date DESC
3. Verify logs appear in correct reverse chronological order

**Database Verification:**
```sql
-- Check chronological ordering
SELECT
    action_date,
    action_type,
    attributes->>'role_name' as role_name,
    target_user_name
FROM audit_action
WHERE guild_id = YOUR_GUILD_ID
  AND action_type IN ('MEMBER_ROLE_ASSIGN', 'MEMBER_ROLE_REMOVE')
ORDER BY action_date DESC
LIMIT 10;
```

**Expected Result:**
- Most recent action appears first
- Timestamps are accurate to within a few seconds of actual interaction time
- No duplicate entries for same interaction

**Pass Criteria:**
- [ ] Logs ordered correctly by action_date
- [ ] Timestamps match interaction time
- [ ] No duplicate logs

---

### Phase 6: Test Exclusive Role Groups Audit Logging

**Objective:** Verify audit logs capture both removal (from group) and assignment (new role)

**Steps:**
1. Have a menu with exclusive role group (e.g., Color Roles: Red, Blue, Green)
2. Click button for "Red" role
3. Check audit log - should see MEMBER_ROLE_ASSIGN for Red
4. Click button for "Blue" role
5. Check audit log - should see:
   - MEMBER_ROLE_REMOVE for Red
   - MEMBER_ROLE_ASSIGN for Blue

**Database Verification:**
```sql
-- Find exclusive group role switches
SELECT
    action_date,
    action_type,
    attributes->>'role_name' as role_name,
    target_user_name
FROM audit_action
WHERE guild_id = YOUR_GUILD_ID
  AND target_user_id = 'YOUR_USER_ID'
  AND action_type IN ('MEMBER_ROLE_ASSIGN', 'MEMBER_ROLE_REMOVE')
ORDER BY action_date DESC
LIMIT 5;
```

**Expected Result:**
```
action_date             | action_type            | role_name
2026-03-08 14:45:02    | MEMBER_ROLE_ASSIGN     | Blue
2026-03-08 14:45:02    | MEMBER_ROLE_REMOVE     | Red
2026-03-08 14:44:30    | MEMBER_ROLE_ASSIGN     | Red
```

**Pass Criteria:**
- [ ] REMOVE log exists for old role (Red)
- [ ] ASSIGN log exists for new role (Blue)
- [ ] Both logs have same action_date (within 1 second)
- [ ] Both logs have reaction_role: "true"

---

### Phase 7: Test Failed Validation (No Audit Log)

**Objective:** Verify failed validations do NOT create audit log entries

**Steps:**
1. Create role item with prerequisite role requirement
2. As user WITHOUT prerequisite, click button
3. Verify you get error message (validation failed)
4. Check database - should NOT see new audit log entry

**Database Verification:**
```sql
-- Check for audit logs in last 1 minute for your user
SELECT
    action_date,
    action_type,
    attributes->>'role_name' as role_name
FROM audit_action
WHERE guild_id = YOUR_GUILD_ID
  AND target_user_id = 'YOUR_USER_ID'
  AND action_date > NOW() - INTERVAL '1 minute'
ORDER BY action_date DESC;
```

**Expected Result:**
- Query returns NO new rows
- No audit log entry created for failed validation

**Pass Criteria:**
- [ ] No audit log entry created when validation fails
- [ ] Audit logs ONLY created for successful role assignments/removals

---

### Phase 8: Test Multiple Users (Audit Log Isolation)

**Objective:** Verify audit logs correctly track different users

**Steps:**
1. Have User A assign Role X
2. Have User B assign Role Y
3. Query audit logs
4. Verify each user's action is logged separately with correct target_user_id

**Database Verification:**
```sql
-- View audit logs for multiple users
SELECT
    action_date,
    action_type,
    target_user_name,
    attributes->>'role_name' as role_name
FROM audit_action
WHERE guild_id = YOUR_GUILD_ID
  AND action_type IN ('MEMBER_ROLE_ASSIGN', 'MEMBER_ROLE_REMOVE')
ORDER BY action_date DESC
LIMIT 20;
```

**Expected Result:**
- User A has separate log entry with their Discord ID
- User B has separate log entry with their Discord ID
- No cross-contamination of user IDs

**Pass Criteria:**
- [ ] Each user has separate audit log entries
- [ ] target_user_id matches correct Discord user
- [ ] No logs attributed to wrong user

---

### Phase 9: Test Audit Log Attributes (JSONB Structure)

**Objective:** Verify attributes field contains correct JSON structure

**Steps:**
1. Assign any role via reaction/button
2. Query audit log attributes field
3. Verify JSON structure and data types

**Database Verification:**
```sql
-- Inspect attributes JSONB field
SELECT
    id,
    action_type,
    attributes,
    attributes->>'role_id' as role_id_text,
    attributes->>'role_name' as role_name_text,
    attributes->>'reaction_role' as reaction_role_flag
FROM audit_action
WHERE guild_id = YOUR_GUILD_ID
  AND action_type IN ('MEMBER_ROLE_ASSIGN', 'MEMBER_ROLE_REMOVE')
ORDER BY action_date DESC
LIMIT 5;
```

**Expected JSONB Structure:**
```json
{
  "role_id": "1234567890123456789",
  "role_name": "Notifications",
  "reaction_role": "true"
}
```

**Pass Criteria:**
- [ ] attributes is valid JSONB
- [ ] role_id is string (Discord Snowflake)
- [ ] role_name is string
- [ ] reaction_role is string "true"
- [ ] No unexpected fields in attributes

---

### Phase 10: Test Audit Log Persistence

**Objective:** Verify audit logs persist across service restarts

**Steps:**
1. Create some reaction role audit log entries
2. Restart sb-worker service
3. Query database - verify logs still exist
4. Create new audit log entry after restart
5. Verify new log appears alongside old logs

**Database Verification:**
```sql
-- Count audit logs before and after restart
SELECT
    COUNT(*) as total_logs,
    COUNT(CASE WHEN action_type = 'MEMBER_ROLE_ASSIGN' THEN 1 END) as assign_logs,
    COUNT(CASE WHEN action_type = 'MEMBER_ROLE_REMOVE' THEN 1 END) as remove_logs
FROM audit_action
WHERE guild_id = YOUR_GUILD_ID
  AND attributes->>'reaction_role' = 'true';
```

**Expected Result:**
- Old logs persist after restart
- New logs created successfully after restart
- No data loss

**Pass Criteria:**
- [ ] All logs persist across restarts
- [ ] No corruption of audit_action table
- [ ] New logs created successfully post-restart

---

## Database Schema Validation

### Audit Action Table Structure

**Table:** `audit_action`

**Columns:**
- `id` (BIGINT, PRIMARY KEY) - Auto-incrementing ID
- `guild_id` (BIGINT, NOT NULL) - Discord guild ID
- `action_date` (TIMESTAMP, NOT NULL) - When event occurred
- `action_type` (VARCHAR, NOT NULL) - Enum value (MEMBER_ROLE_ASSIGN, etc.)
- `source_user_id` (VARCHAR) - User who triggered action (nullable)
- `source_user_name` (VARCHAR) - Username of source
- `target_user_id` (VARCHAR) - User who received/lost role
- `target_user_name` (VARCHAR) - Username of target
- `channel_id` (VARCHAR) - Channel where event occurred
- `channel_name` (VARCHAR) - Channel name
- `attributes` (JSONB) - Additional metadata

**Indexes:**
- `idx_audit_action_guild_date` (guild_id, action_date)
- `idx_audit_action_type` (action_type)

**Verify Schema:**
```sql
-- Check table structure
\d audit_action

-- Verify indexes exist
SELECT
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE tablename = 'audit_action';
```

---

## Discord Command Verification

### View Audit Logs via Discord Bot

**Command:** `/audit status`
- Shows whether audit system is enabled
- Displays monitored action types
- Shows audit channel if configured

**Note:** As of this implementation, there is NO Discord command to query audit logs directly. Audit logs must be viewed via:
1. Database queries (as shown in this test plan)
2. Dashboard (if audit log viewer is implemented in future)
3. Audit channel messages (if configured to post to channel)

---

## Troubleshooting Guide

### Issue 1: No Audit Logs Created

**Symptoms:**
- Roles are assigned/removed successfully
- No entries in audit_action table

**Diagnosis:**
```sql
-- Check if audit system is enabled
SELECT enabled FROM audit_config WHERE guild_id = YOUR_GUILD_ID;

-- Check if AuditService is being called (check application logs)
grep "logRoleAction" /path/to/sb-worker/logs/application.log
```

**Solutions:**
1. Enable audit system: `/audit enable`
2. Check monitored_actions includes MEMBER_ROLE_ASSIGN and MEMBER_ROLE_REMOVE
3. Verify AuditService bean is properly injected
4. Check application logs for exceptions during audit logging

---

### Issue 2: Audit Logs Missing Attributes

**Symptoms:**
- Audit log entry exists
- attributes field is empty or missing keys

**Diagnosis:**
```sql
-- Check attributes structure
SELECT
    id,
    attributes,
    jsonb_object_keys(attributes) as attribute_keys
FROM audit_action
WHERE guild_id = YOUR_GUILD_ID
ORDER BY action_date DESC
LIMIT 5;
```

**Solutions:**
1. Verify ReactionRoleServiceImpl.logRoleAction() is being called
2. Check role object is not null when logging
3. Ensure withAttribute() calls execute before save()

---

### Issue 3: Wrong target_user_id in Audit Log

**Symptoms:**
- Audit log shows wrong user received/lost role

**Diagnosis:**
```sql
-- Compare logged user vs actual Discord member
SELECT
    target_user_id,
    target_user_name,
    attributes->>'role_name' as role_name
FROM audit_action
WHERE guild_id = YOUR_GUILD_ID
ORDER BY action_date DESC
LIMIT 5;
```

**Solutions:**
1. Verify ReactionRoleListener passes correct member to handleRoleInteraction()
2. Check ButtonInteractionEvent.getMember() returns correct member
3. Ensure MessageReactionAddEvent.getUserId() matches member receiving role

---

### Issue 4: Duplicate Audit Log Entries

**Symptoms:**
- Single role assignment creates multiple audit logs

**Diagnosis:**
```sql
-- Check for duplicates within 1 second
SELECT
    action_date,
    action_type,
    target_user_id,
    attributes->>'role_id' as role_id,
    COUNT(*) as duplicate_count
FROM audit_action
WHERE guild_id = YOUR_GUILD_ID
GROUP BY action_date, action_type, target_user_id, attributes->>'role_id'
HAVING COUNT(*) > 1
ORDER BY action_date DESC;
```

**Solutions:**
1. Check ReactionRoleListener is not registered multiple times
2. Verify handleRoleInteraction() is only called once per event
3. Ensure no duplicate event listeners in Spring context

---

### Issue 5: Audit Logs Not Showing in Audit Channel

**Symptoms:**
- Audit logs exist in database
- No messages posted to audit channel

**Diagnosis:**
```sql
-- Check audit channel configuration
SELECT
    guild_id,
    channel_id as audit_channel_id,
    monitored_actions
FROM audit_config
WHERE guild_id = YOUR_GUILD_ID;
```

**Solutions:**
1. Verify audit channel is set: `/audit setchannel #audit-log`
2. Check bot has SEND_MESSAGES permission in audit channel
3. Ensure monitored_actions includes MEMBER_ROLE_ASSIGN and MEMBER_ROLE_REMOVE
4. Check Discord audit message formatter handles reaction role events

---

## Acceptance Criteria

### Functional Requirements
- [ ] Role assignment creates MEMBER_ROLE_ASSIGN audit log
- [ ] Role removal creates MEMBER_ROLE_REMOVE audit log
- [ ] Audit logs include correct target_user_id
- [ ] Audit logs include correct channel_id
- [ ] Audit logs include role_id in attributes
- [ ] Audit logs include role_name in attributes
- [ ] Audit logs include reaction_role: "true" in attributes
- [ ] Validation failures do NOT create audit logs
- [ ] Audit logs persist across service restarts

### Button Interaction Audit Logging
- [ ] Button click creates audit log with source_user_id
- [ ] Toggleable button (remove role) creates MEMBER_ROLE_REMOVE log
- [ ] Non-toggleable button (assign role) creates MEMBER_ROLE_ASSIGN log

### Reaction Interaction Audit Logging
- [ ] Adding reaction creates MEMBER_ROLE_ASSIGN audit log
- [ ] Removing reaction (toggleable) creates MEMBER_ROLE_REMOVE audit log
- [ ] Reaction logs include correct target_user_id

### Exclusive Role Groups Audit Logging
- [ ] Switching roles logs BOTH remove old + assign new
- [ ] Exclusive group removal logged before new assignment
- [ ] Both logs have same/adjacent action_date timestamps

### Database Integrity
- [ ] audit_action table exists with correct schema
- [ ] Indexes exist on guild_id, action_date, action_type
- [ ] attributes column is JSONB type
- [ ] All audit logs queryable via SQL

### Code Quality
- [ ] logRoleAction() called from handleRoleInteraction()
- [ ] AuditService properly injected via constructor
- [ ] MEMBER_ROLE_ASSIGN and MEMBER_ROLE_REMOVE in AuditActionType enum
- [ ] No duplicate audit log entries
- [ ] No exceptions during audit logging (check logs)

---

## Test Evidence Template

Document your test execution with the following evidence:

### Evidence 1: Audit System Enabled
**Command:** `/audit status`
**Screenshot:** [Screenshot of /audit status command response]
**Result:** ✅ PASS / ❌ FAIL

---

### Evidence 2: Role Assignment Audit Log
**Database Query Result:**
```
action_date: 2026-03-08 14:30:00
action_type: MEMBER_ROLE_ASSIGN
target_user_id: 123456789012345678
role_id: 987654321098765432
role_name: Notifications
```
**Result:** ✅ PASS / ❌ FAIL

---

### Evidence 3: Role Removal Audit Log
**Database Query Result:**
```
action_date: 2026-03-08 14:30:15
action_type: MEMBER_ROLE_REMOVE
target_user_id: 123456789012345678
role_id: 987654321098765432
role_name: Notifications
```
**Result:** ✅ PASS / ❌ FAIL

---

### Evidence 4: Attributes JSONB Structure
**Database Query Result:**
```json
{
  "role_id": "987654321098765432",
  "role_name": "Notifications",
  "reaction_role": "true"
}
```
**Result:** ✅ PASS / ❌ FAIL

---

### Evidence 5: Exclusive Group Audit Logs
**Database Query Result:**
```
2026-03-08 14:35:02 | MEMBER_ROLE_ASSIGN | Blue
2026-03-08 14:35:02 | MEMBER_ROLE_REMOVE | Red
```
**Result:** ✅ PASS / ❌ FAIL

---

## Summary

This comprehensive test plan verifies that:
1. ✅ Audit logging is implemented in ReactionRoleServiceImpl
2. ✅ Two audit action types defined: MEMBER_ROLE_ASSIGN, MEMBER_ROLE_REMOVE
3. ✅ Audit logs include comprehensive metadata (role_id, role_name, reaction_role flag)
4. ✅ Audit logs correctly track both button and reaction interactions
5. ✅ Exclusive role groups log both removal and assignment
6. ✅ Failed validations do NOT create audit logs
7. ✅ Audit logs persist in database and are queryable via SQL

**Implementation Status:** ✅ COMPLETE

The audit logging system is fully implemented and ready for manual testing when Discord environment is available.

---

## Related Documentation

- **Implementation Plan:** `.auto-claude/specs/005-reaction-roles-system/implementation_plan.json`
- **E2E Test Procedure:** `E2E_TEST_PROCEDURE.md`
- **Exclusive Groups Test:** `EXCLUSIVE_ROLE_GROUPS_TEST_PLAN.md`
- **Toggleable Roles Test:** `TOGGLEABLE_ROLES_TEST_PLAN.md`
- **Role Requirements Test:** `ROLE_REQUIREMENTS_TEST_PLAN.md`
- **Integration Summary:** `INTEGRATION_TEST_SUMMARY.md`

---

**Test Plan Created:** 2026-03-08
**Last Updated:** 2026-03-08
**Version:** 1.0

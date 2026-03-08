# Role Requirements Test Plan

## Test ID: subtask-5-4
**Date:** 2026-03-08
**Feature:** Reaction Roles System - Role Requirements (Prerequisites)
**Objective:** Verify that role requirements enforce prerequisite checks, preventing users without required roles from obtaining protected roles

---

## Overview

Role requirements (prerequisites) ensure that users must have specific roles before they can obtain certain roles through reaction role menus. This feature enables:
- **Tiered access** (must have "Member" role to get "Verified Member" role)
- **VIP roles** (must have "Supporter" role to get "VIP Events" role)
- **Progression systems** (must have "Level 5" to get "Level 10" role)
- **Protected roles** (must have "Staff" role to get "Moderator Trainee" role)

Users who attempt to get a role without meeting the prerequisites will receive an error message and the role will not be assigned.

---

## Implementation Details

### Code Reference

The role requirements logic is implemented in `ReactionRoleServiceImpl.kt`:

#### Storage Format
**Location:** `ReactionRoleMenuItem.kt` lines 41-42
```kotlin
@Column(name = "required_role_ids", columnDefinition = "TEXT")
var requiredRoleIds: String? = null
```
- Prerequisite role IDs are stored as comma-separated string (e.g., "123456789,987654321")
- `null` or empty string means no prerequisites

#### Validation Method
**Location:** Lines 237-247
```kotlin
@Transactional(readOnly = true)
override fun validateRoleRequirements(member: Member, item: ReactionRoleMenuItem): Boolean {
    val requiredRoleIds = item.requiredRoleIds
    if (requiredRoleIds.isNullOrBlank()) {
        return true  // No requirements = always valid
    }

    val requiredIds = requiredRoleIds.split(",").map { it.trim() }
    return requiredIds.all { roleId ->
        memberHasRole(member, roleId)
    }
}
```

#### Assignment Validation
**Location:** Lines 250-274
```kotlin
@Transactional(readOnly = true)
override fun validateRoleAssignment(
    member: Member,
    item: ReactionRoleMenuItem,
    role: Role
): ReactionRoleService.ValidationResult {
    // Check if bot can manage the role
    if (!member.guild.selfMember.canInteract(role)) {
        return ReactionRoleService.ValidationResult(
            success = false,
            errorMessage = "Bot cannot manage this role (role is higher than bot's highest role)"
        )
    }

    // Check if member meets role requirements
    if (!validateRoleRequirements(member, item)) {
        return ReactionRoleService.ValidationResult(
            success = false,
            errorMessage = "You do not meet the requirements for this role"
        )
    }

    return ReactionRoleService.ValidationResult(success = true)
}
```

#### Integration in Role Assignment
**Location:** Lines 303-310
```kotlin
// Validate the assignment
val validation = validateRoleAssignment(member, item, role)
if (!validation.success) {
    return ReactionRoleService.RoleInteractionResult(
        success = false,
        action = ReactionRoleService.RoleInteractionResult.Action.VALIDATION_FAILED,
        errorMessage = validation.errorMessage
    )
}
```

**Key Behavior:**
1. When a user clicks a role button/reaction, `handleRoleInteraction()` is called
2. Before assigning the role, `validateRoleAssignment()` is called
3. This calls `validateRoleRequirements()` which checks if member has ALL required roles
4. If validation fails, an error is returned with message "You do not meet the requirements for this role"
5. No role assignment occurs and no audit log entry is created
6. For button interactions, user receives ephemeral error message
7. For reaction interactions, the reaction is automatically removed

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
In your test Discord server, create the following roles:

**Basic Roles:**
- 📝 **Member** (basic membership role)
- ✅ **Verified** (verification role)

**Protected Roles:**
- 🌟 **VIP** (requires Member + Verified)
- 👑 **Premium** (requires Member)
- 🎮 **Events Access** (requires VIP)

Note the role IDs for each (right-click role → Copy ID with Developer Mode enabled).

#### 2. Test Users
You'll need **2 test user accounts**:
- **User A:** Test user with NO roles initially
- **User B:** Test user who will be given prerequisite roles

Both users must be members of the test server.

#### 3. Test Channel
- Create or identify a test channel where the bot can post messages
- Note the channel ID

---

## Test Execution Steps

### Phase 1: Create Reaction Role Menu with Prerequisites 🔒

1. **Navigate to Dashboard:**
   - Go to `http://localhost:3000/dashboard/[guildId]/reaction-roles`

2. **Create Menu:**
   - Click "Create New Menu"
   - **Title:** `Role Progression`
   - **Description:** `Roles with prerequisites - earn them by meeting requirements`
   - **Menu Type:** `BUTTONS`
   - **Channel ID:** [your test channel ID]
   - Click "Save Menu"

3. **Verify Menu Creation:**
   - Menu appears in the list
   - Menu ID is assigned
   - Toast notification: "Menu created successfully"

**Expected Result:**
- ✅ Menu "Role Progression" created
- ✅ Menu ID returned (note this for next steps)
- ✅ No errors in browser console

---

### Phase 2: Add Role Items with Requirements 📋

#### Item 1: Basic Role (No Prerequisites)

1. **Click "Add Item"** in the menu builder
2. **Configure:**
   - **Role:** Select "Member" role
   - **Label:** `Member`
   - **Emoji:** `📝`
   - **Description:** `Basic membership - no requirements`
   - **Toggleable:** ✅ Yes
   - **Required Roles:** Leave EMPTY (no prerequisites)
3. **Save Item**

**Expected Result:**
- ✅ Item created with no required roles
- ✅ `required_role_ids` field is NULL in database

#### Item 2: Single Prerequisite

1. **Click "Add Item"**
2. **Configure:**
   - **Role:** Select "Premium" role
   - **Label:** `Premium`
   - **Emoji:** `👑`
   - **Description:** `Requires: Member role`
   - **Toggleable:** ✅ Yes
   - **Required Roles:** Select "Member" role from multi-select
3. **Save Item**

**Expected Result:**
- ✅ Item created with Member as prerequisite
- ✅ `required_role_ids` contains Member role ID

#### Item 3: Multiple Prerequisites

1. **Click "Add Item"**
2. **Configure:**
   - **Role:** Select "VIP" role
   - **Label:** `VIP`
   - **Emoji:** `🌟`
   - **Description:** `Requires: Member AND Verified roles`
   - **Toggleable:** ✅ Yes
   - **Required Roles:** Select BOTH "Member" and "Verified" roles
3. **Save Item**

**Expected Result:**
- ✅ Item created with multiple prerequisites
- ✅ `required_role_ids` contains comma-separated role IDs

#### Item 4: Chained Prerequisites

1. **Click "Add Item"**
2. **Configure:**
   - **Role:** Select "Events Access" role
   - **Label:** `Events Access`
   - **Emoji:** `🎮`
   - **Description:** `Requires: VIP role`
   - **Toggleable:** ✅ Yes
   - **Required Roles:** Select "VIP" role
3. **Save Item**

**Expected Result:**
- ✅ Item created with VIP as prerequisite (which itself requires Member + Verified)

**Database Verification:**
```bash
docker-compose exec postgres psql -U sablebot -d sablebot -c "
SELECT id, role_id, label, required_role_ids, toggleable, active
FROM reaction_role_menu_item
WHERE menu_id = [menu_id]
ORDER BY display_order;
"
```

Expected output:
```
 id | role_id  |     label      | required_role_ids       | toggleable | active
----+----------+----------------+-------------------------+------------+--------
  1 | [member] | Member         | NULL                    | t          | t
  2 | [prem]   | Premium        | [member_id]             | t          | t
  3 | [vip]    | VIP            | [member_id],[verified]  | t          | t
  4 | [events] | Events Access  | [vip_id]                | t          | t
```

---

### Phase 3: Post Menu to Discord 📤

1. **In Dashboard:**
   - Click "Post to Channel" button
   - Wait for confirmation

2. **Verify in Discord:**
   - Check test channel for the menu message
   - Verify all 4 buttons appear:
     - 📝 Member
     - 👑 Premium
     - 🌟 VIP
     - 🎮 Events Access

**Expected Result:**
- ✅ Message posted to Discord channel
- ✅ All 4 buttons visible with correct emojis and labels
- ✅ No errors in bot logs

---

### Phase 4: Test User Without Prerequisites ❌

**Test User:** User A (has NO roles)

1. **Click "Member" button** (no prerequisites)
   - **Expected:** ✅ Role assigned successfully
   - **Feedback:** "Role Member added!"
   - **Verify:** User A now has Member role in Discord

2. **Click "Premium" button** (requires Member - which user now has)
   - **Expected:** ✅ Role assigned successfully
   - **Feedback:** "Role Premium added!"
   - **Verify:** User A now has both Member AND Premium roles

3. **Click "VIP" button** (requires Member + Verified)
   - **Expected:** ❌ Role assignment FAILS (user lacks Verified role)
   - **Feedback:** "You do not meet the requirements for this role"
   - **Verify:** User A does NOT have VIP role
   - **Verify:** No audit log entry for VIP assignment

4. **Click "Events Access" button** (requires VIP)
   - **Expected:** ❌ Role assignment FAILS (user lacks VIP role)
   - **Feedback:** "You do not meet the requirements for this role"
   - **Verify:** User A does NOT have Events Access role

**Expected Results:**
- ✅ User can get Member (no prerequisites)
- ✅ User can get Premium (has Member prerequisite)
- ❌ User CANNOT get VIP (missing Verified prerequisite)
- ❌ User CANNOT get Events Access (missing VIP prerequisite)

**Database Verification (Audit Log):**
```bash
docker-compose exec postgres psql -U sablebot -d sablebot -c "
SELECT action_type, target_id, metadata
FROM audit_action
WHERE guild_id = [guild_id]
  AND target_id = '[user_a_id]'
  AND action_type IN ('MEMBER_ROLE_ASSIGN', 'MEMBER_ROLE_REMOVE')
ORDER BY created_at DESC
LIMIT 10;
"
```

Expected output (should show ONLY Member and Premium assignments):
```
  action_type       | target_id | metadata
--------------------+-----------+----------------------------------
 MEMBER_ROLE_ASSIGN | [user_a]  | {"roleId": "[premium_id]", ...}
 MEMBER_ROLE_ASSIGN | [user_a]  | {"roleId": "[member_id]", ...}
```

**Key Point:** NO audit entries for VIP or Events Access (validation failed before assignment)

---

### Phase 5: Grant Prerequisite and Retry ✅

**Test User:** User A (currently has Member + Premium)

1. **Manually assign "Verified" role to User A** (via Discord Server Settings → Members)

2. **Click "VIP" button again** (now has Member + Verified)
   - **Expected:** ✅ Role assigned successfully
   - **Feedback:** "Role VIP added!"
   - **Verify:** User A now has Member, Premium, Verified, AND VIP roles

3. **Click "Events Access" button** (now has VIP prerequisite)
   - **Expected:** ✅ Role assigned successfully
   - **Feedback:** "Role Events Access added!"
   - **Verify:** User A now has all roles including Events Access

**Expected Results:**
- ✅ After gaining Verified role, user can now get VIP
- ✅ After gaining VIP role, user can now get Events Access
- ✅ Prerequisites are re-validated on each interaction

**Database Verification (Audit Log):**
```bash
docker-compose exec postgres psql -U sablebot -d sablebot -c "
SELECT action_type, target_id, metadata
FROM audit_action
WHERE guild_id = [guild_id]
  AND target_id = '[user_a_id]'
  AND action_type = 'MEMBER_ROLE_ASSIGN'
ORDER BY created_at DESC
LIMIT 4;
"
```

Expected output (should now include VIP and Events Access):
```
  action_type       | target_id | metadata
--------------------+-----------+--------------------------------------
 MEMBER_ROLE_ASSIGN | [user_a]  | {"roleId": "[events_access_id]", ...}
 MEMBER_ROLE_ASSIGN | [user_a]  | {"roleId": "[vip_id]", ...}
 MEMBER_ROLE_ASSIGN | [user_a]  | {"roleId": "[premium_id]", ...}
 MEMBER_ROLE_ASSIGN | [user_a]  | {"roleId": "[member_id]", ...}
```

---

### Phase 6: Test Multiple Prerequisites (ALL Required) 🔐

**Test User:** User B (has NO roles initially)

1. **Give User B only the "Member" role** (manually via Discord)

2. **Click "VIP" button** (requires Member + Verified)
   - **Expected:** ❌ Role assignment FAILS
   - **Feedback:** "You do not meet the requirements for this role"
   - **Reason:** User has Member but NOT Verified (ALL required)

3. **Give User B only the "Verified" role** (manually remove Member, add Verified)

4. **Click "VIP" button** again
   - **Expected:** ❌ Role assignment STILL FAILS
   - **Feedback:** "You do not meet the requirements for this role"
   - **Reason:** User has Verified but NOT Member (ALL required)

5. **Give User B BOTH Member AND Verified roles** (manually)

6. **Click "VIP" button** again
   - **Expected:** ✅ Role assigned successfully
   - **Feedback:** "Role VIP added!"
   - **Verify:** User B now has Member, Verified, AND VIP

**Expected Results:**
- ❌ Having ONLY "Member" is not sufficient for VIP
- ❌ Having ONLY "Verified" is not sufficient for VIP
- ✅ Having BOTH "Member" AND "Verified" allows VIP assignment
- ✅ System enforces ALL prerequisites using `.all { }` logic

**Code Validation:**
This test verifies line 244 in `ReactionRoleServiceImpl.kt`:
```kotlin
return requiredIds.all { roleId ->
    memberHasRole(member, roleId)
}
```

---

### Phase 7: Test Prerequisites with Toggleable Roles 🔄

**Test User:** User A (currently has all roles)

1. **Toggle OFF the "Member" role** (click Member button to remove it)
   - **Expected:** ✅ Member role removed (toggleable)
   - **Verify:** User A no longer has Member role

2. **Observe other roles:**
   - Premium role STILL exists (was already assigned, not auto-removed)
   - VIP role STILL exists (was already assigned, not auto-removed)
   - Events Access STILL exists (was already assigned)

3. **Try to toggle OFF "VIP" role** (click VIP button)
   - **Expected:** ✅ VIP role removed (toggleable)
   - **Verify:** User A no longer has VIP role

4. **Try to toggle ON "VIP" role again** (click VIP button)
   - **Expected:** ❌ Role assignment FAILS
   - **Feedback:** "You do not meet the requirements for this role"
   - **Reason:** User no longer has Member role (prerequisite)

**Expected Results:**
- ✅ Removing a prerequisite role does NOT auto-remove dependent roles
- ✅ Re-acquiring a role WITH prerequisites re-validates requirements
- ✅ Toggleable + prerequisites work together correctly

**Important Note:**
The system does NOT cascade-remove roles when prerequisites are lost. This is intentional:
- Users keep roles they already have
- Requirements are only checked when ASSIGNING new roles
- Admins can manually remove roles if needed

---

### Phase 8: Test Reaction-based Prerequisites 🎭

**Test User:** User A (reset to NO roles)

1. **Add menu with REACTIONS menu type** (create a second test menu)
   - Menu Type: `REACTIONS` or `BOTH`
   - Add same items (Member, Premium, VIP, Events Access)

2. **Post menu to Discord**

3. **Test User A adds reaction for "VIP" role** (without prerequisites)
   - **Expected:** ❌ Reaction is automatically REMOVED
   - **Expected:** Role is NOT assigned
   - **Bot Log:** Warning about failed validation

4. **Give User A the "Member" and "Verified" roles** (manually)

5. **Test User A adds reaction for "VIP" role** (with prerequisites)
   - **Expected:** ✅ Reaction stays (not removed)
   - **Expected:** ✅ Role is assigned
   - **Verify:** User A now has VIP role

**Expected Results:**
- ❌ Reactions for roles without prerequisites are auto-removed
- ✅ Reactions for roles WITH prerequisites are accepted
- ✅ Prerequisite validation works for BOTH buttons and reactions

**Code Reference:**
This test verifies `ReactionRoleListener.kt` lines 84-92 (onMessageReactionAdd):
```kotlin
if (!result.success) {
    // Remove the reaction if validation failed
    event.reaction.removeReaction(event.user).queue()
}
```

---

### Phase 9: Edge Case - Prerequisites + Exclusive Groups 🎯

**Test Scenario:** Create a role group with prerequisites

1. **Create Exclusive Group:**
   - Name: "VIP Tiers"
   - Group for: Bronze VIP, Silver VIP, Gold VIP

2. **Create Menu Items:**
   - **Bronze VIP:** No prerequisites, Group: VIP Tiers
   - **Silver VIP:** Requires "Member", Group: VIP Tiers
   - **Gold VIP:** Requires "Member + Verified", Group: VIP Tiers

3. **Test User (no roles) clicks "Bronze VIP":**
   - **Expected:** ✅ Bronze VIP assigned (no prerequisites)

4. **Test User clicks "Silver VIP":**
   - **Expected:** ❌ FAILS (missing Member prerequisite)
   - **Expected:** User KEEPS Bronze VIP (validation failed before group removal)

5. **Give user "Member" role, click "Silver VIP":**
   - **Expected:** ✅ Silver VIP assigned
   - **Expected:** ✅ Bronze VIP REMOVED (exclusive group behavior)
   - **Verify:** User has Silver VIP only (from VIP Tiers group)

6. **User clicks "Gold VIP":**
   - **Expected:** ❌ FAILS (missing Verified prerequisite)
   - **Expected:** User KEEPS Silver VIP (validation failed before group removal)

7. **Give user "Verified" role, click "Gold VIP":**
   - **Expected:** ✅ Gold VIP assigned
   - **Expected:** ✅ Silver VIP REMOVED (exclusive group behavior)
   - **Verify:** User has Gold VIP only (from VIP Tiers group)

**Expected Results:**
- ✅ Prerequisites are checked BEFORE exclusive group removal
- ✅ If validation fails, user keeps existing group role
- ✅ If validation succeeds, old group role is removed and new role is assigned
- ✅ Order of operations: validate → remove group roles → assign new role

**Code Validation:**
This test verifies the order of operations in `handleRoleInteraction()` (lines 303-350):
1. Line 303: Validate assignment (includes prerequisites check)
2. Line 304-310: Return error if validation fails (no role changes)
3. Line 342-347: If validation succeeds, remove group roles THEN assign

---

### Phase 10: Database Schema Validation 🗄️

Run the following queries to verify the database structure supports prerequisites:

```bash
# Check required_role_ids column exists and has correct type
docker-compose exec postgres psql -U sablebot -d sablebot -c "
SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_name = 'reaction_role_menu_item'
  AND column_name = 'required_role_ids';
"
```

Expected output:
```
   column_name    | data_type | character_maximum_length | is_nullable
------------------+-----------+--------------------------+-------------
 required_role_ids| text      |                          | YES
```

```bash
# Check sample data
docker-compose exec postgres psql -U sablebot -d sablebot -c "
SELECT id, role_id, label, required_role_ids
FROM reaction_role_menu_item
WHERE required_role_ids IS NOT NULL
LIMIT 5;
"
```

Expected output (comma-separated role IDs):
```
 id | role_id       | label         | required_role_ids
----+---------------+---------------+---------------------------
  2 | 123456789012  | Premium       | 987654321098
  3 | 111222333444  | VIP           | 987654321098,555666777888
  4 | 999888777666  | Events Access | 111222333444
```

---

## Troubleshooting Guide

### Issue 1: Prerequisites not enforced (role assigned without requirements)

**Symptoms:**
- User gets role even without prerequisite roles
- No validation error shown

**Possible Causes:**
1. `required_role_ids` field is NULL or empty (check database)
2. Service method not calling `validateRoleAssignment()`
3. Validation logic has a bug

**Debug Steps:**
```bash
# Check if required_role_ids is set
docker-compose exec postgres psql -U sablebot -d sablebot -c "
SELECT id, role_id, label, required_role_ids
FROM reaction_role_menu_item
WHERE id = [item_id];
"
```

**Expected Fix:**
- Ensure required_role_ids is populated from dashboard
- Check RoleItemEditor component saves required roles correctly
- Verify API DTOs include requiredRoleIds field

---

### Issue 2: User has prerequisite but still can't get role

**Symptoms:**
- User has all required roles but validation fails
- Error: "You do not meet the requirements for this role"

**Possible Causes:**
1. Role IDs don't match (typo, wrong ID saved)
2. Required role IDs not trimmed (whitespace issues)
3. Case sensitivity mismatch

**Debug Steps:**
```bash
# Check user's current roles (in Discord bot logs or JDA)
# Compare with required_role_ids from database

# Enable debug logging in ReactionRoleServiceImpl
# Check logs for:
logger.warn { "Member ${member.id} does not meet requirements for role ${role.id}" }
```

**Expected Fix:**
- Verify role IDs are exactly correct (use Discord Developer Mode → Copy ID)
- Check `.split(",").map { it.trim() }` handles whitespace correctly
- Ensure role IDs are stored as strings, not longs (Discord IDs are >53 bits)

---

### Issue 3: Multiple prerequisites not working (ALL logic)

**Symptoms:**
- User has SOME prerequisites but validation passes
- User gets role with only 1 of 2 required roles

**Possible Causes:**
1. Logic uses `.any { }` instead of `.all { }`
2. Parsing error in comma-separated role IDs

**Debug Steps:**
```kotlin
// Check code at line 244 in ReactionRoleServiceImpl.kt
return requiredIds.all { roleId ->  // MUST be .all, not .any
    memberHasRole(member, roleId)
}
```

**Expected Fix:**
- Ensure line 244 uses `.all { }` (not `.any { }`)
- Verify all role IDs are parsed correctly from comma-separated string

---

### Issue 4: Prerequisite check not working for reactions

**Symptoms:**
- Prerequisites work for buttons but not for reactions
- Reactions are not removed when validation fails

**Possible Causes:**
1. `onMessageReactionAdd` doesn't call validation
2. Reaction removal logic is commented out or broken

**Debug Steps:**
```kotlin
// Check ReactionRoleListener.kt lines 84-92
if (!result.success) {
    // This MUST remove the reaction on validation failure
    event.reaction.removeReaction(event.user).queue()
}
```

**Expected Fix:**
- Ensure `onMessageReactionAdd` calls `handleRoleInteraction()`
- Verify reaction removal is executed when `result.success == false`

---

### Issue 5: Chained prerequisites fail

**Symptoms:**
- User has Role A (requires Member)
- User cannot get Role B (requires Role A)
- Error: "You do not meet the requirements for this role"

**Possible Causes:**
1. This is EXPECTED behavior if user lost Member role
2. Role A was manually assigned (bypass prerequisites)
3. Prerequisite validation doesn't check transitive requirements

**Expected Behavior:**
The system does NOT support transitive prerequisites:
- If Role B requires Role A, and Role A requires Member
- User MUST have Role A to get Role B
- User is NOT required to have Member (only direct prerequisites checked)

**Example:**
- Member → no prerequisites
- VIP → requires Member + Verified
- Events Access → requires VIP (does NOT require Member/Verified directly)

If user:
1. Gets Member + Verified (meets requirements)
2. Gets VIP (has Member + Verified)
3. Admin manually removes Member role
4. User STILL has VIP (no cascade removal)
5. User CAN get Events Access (only checks for VIP, not Member/Verified)

This is intentional design. Prerequisites are only checked at assignment time.

---

## Acceptance Criteria Checklist

Before marking this subtask as complete, verify:

### Functional Requirements
- [ ] Users WITHOUT prerequisites CANNOT get protected roles
- [ ] Users WITH single prerequisite CAN get role after meeting requirement
- [ ] Users must have ALL prerequisites for multi-prerequisite roles
- [ ] Prerequisites are re-validated on every assignment attempt
- [ ] Gaining prerequisite role enables access to dependent roles
- [ ] Losing prerequisite role does NOT auto-remove dependent roles (intentional)
- [ ] Ephemeral error message shows "You do not meet the requirements for this role"

### Button Interaction Testing
- [ ] Clicking button without prerequisites shows ephemeral error
- [ ] Clicking button with prerequisites assigns role successfully
- [ ] Error messages are user-friendly and ephemeral (not visible to others)

### Reaction Interaction Testing
- [ ] Adding reaction without prerequisites auto-removes reaction
- [ ] Adding reaction with prerequisites keeps reaction and assigns role
- [ ] No role assignment occurs when validation fails

### Database Validation
- [ ] `required_role_ids` field stores comma-separated role IDs
- [ ] NULL or empty `required_role_ids` means no prerequisites
- [ ] Failed validation does NOT create audit log entry
- [ ] Successful assignment creates MEMBER_ROLE_ASSIGN audit log entry

### Audit Logging
- [ ] Audit log captures MEMBER_ROLE_ASSIGN only when validation succeeds
- [ ] Audit log does NOT capture failed validation attempts
- [ ] Audit metadata includes role ID and reason

### Edge Cases
- [ ] Prerequisites + toggleable roles work correctly
- [ ] Prerequisites + exclusive groups validate BEFORE group removal
- [ ] Multiple simultaneous prerequisites enforced with ALL logic
- [ ] Whitespace in comma-separated role IDs handled correctly
- [ ] Invalid role IDs (deleted roles) handled gracefully

### Code Quality
- [ ] All error messages are user-friendly
- [ ] Logging captures validation failures with context
- [ ] No console errors in dashboard
- [ ] No exceptions in bot logs

### Documentation
- [ ] Test results documented in this file
- [ ] Screenshots or logs attached as evidence
- [ ] Any bugs or issues reported with reproduction steps

---

## Test Evidence

### Screenshots
Store screenshots in `.auto-claude/specs/005-reaction-roles-system/test-evidence/subtask-5-4/`:
1. `dashboard-menu-with-prerequisites.png` - Menu configuration showing required roles
2. `discord-button-error-no-prereq.png` - Error message when user lacks prerequisite
3. `discord-button-success-with-prereq.png` - Success message after gaining prerequisite
4. `database-required-role-ids.png` - Database query showing required_role_ids values
5. `audit-log-validation-failed.png` - Audit log showing NO entry for failed validation
6. `audit-log-validation-success.png` - Audit log showing entry for successful assignment

### Test Results

**Test Date:** _____________________
**Tester:** _____________________
**Environment:** _____________________

| Phase | Test Case | Result | Notes |
|-------|-----------|--------|-------|
| 4 | User without prerequisite cannot get role | ⬜ Pass / ⬜ Fail | |
| 4 | User with prerequisite can get role | ⬜ Pass / ⬜ Fail | |
| 5 | Gaining prerequisite enables access | ⬜ Pass / ⬜ Fail | |
| 6 | ALL prerequisites required (not ANY) | ⬜ Pass / ⬜ Fail | |
| 7 | Prerequisites + toggleable roles | ⬜ Pass / ⬜ Fail | |
| 8 | Prerequisites + reactions | ⬜ Pass / ⬜ Fail | |
| 9 | Prerequisites + exclusive groups | ⬜ Pass / ⬜ Fail | |

---

## Summary

This test plan validates the role requirements (prerequisites) feature of the Reaction Roles System. By following these phases, you will verify:

1. ✅ **Access Control:** Users without prerequisites cannot get protected roles
2. ✅ **Validation Logic:** Single and multiple prerequisites are enforced correctly
3. ✅ **User Experience:** Error messages are clear and helpful
4. ✅ **Integration:** Prerequisites work with toggleable roles, exclusive groups, and reactions
5. ✅ **Data Integrity:** Database stores prerequisites correctly and audit logs capture events
6. ✅ **Edge Cases:** Complex scenarios (chained prerequisites, losing prerequisites) work as expected

**Key Success Metrics:**
- Validation prevents unauthorized role assignments
- Error messages guide users to meet requirements
- System re-validates prerequisites on every interaction
- Database and audit logs maintain accurate records

Once all phases pass, this subtask (5-4) can be marked as **completed** ✅

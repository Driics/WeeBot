# Ticket Per-User Limits Test Plan

## Overview

This document provides a comprehensive test plan for verifying that the per-user ticket limits and rate limiting work correctly in the ticket support system.

## Test Environment Setup

### Prerequisites

1. Bot deployed and running in a test Discord server
2. Test Discord server with at least:
   - 3 test user accounts (User1, User2, User3)
   - 2 staff accounts with staff roles
   - 1 admin account with administrator permissions
3. Ticket system configured with:
   - Support channel created
   - Staff roles configured
   - Categories configured (optional)

### Configuration Commands

```
/ticket-setup enable
/ticket-setup support-channel #support
/ticket-setup add-staff-role @Staff
```

## Test Cases

### TC-1: Default Ticket Limit (1 ticket per user)

**Objective:** Verify that by default, users can only open 1 ticket at a time.

**Steps:**
1. Configure ticket system with default settings (maxTicketsPerUser = 1)
2. As User1, execute `/ticket subject:"Test issue 1"`
3. Verify ticket #1 is created successfully
4. As User1, execute `/ticket subject:"Test issue 2"`
5. Verify an error message is displayed: "You have reached the maximum number of open tickets (1)"
6. As User1, verify you cannot create a second ticket

**Expected Results:**
- ✅ First ticket created successfully
- ✅ Second ticket creation blocked
- ✅ Error message clearly states the limit (1)
- ✅ User can see their existing ticket mentioned

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

### TC-2: Custom Ticket Limit (3 tickets per user)

**Objective:** Verify that the configurable limit works correctly.

**Steps:**
1. As admin, execute `/ticket-setup set-max-tickets value:3`
2. As User1, create ticket #1: `/ticket subject:"Issue 1"`
3. As User1, create ticket #2: `/ticket subject:"Issue 2"`
4. As User1, create ticket #3: `/ticket subject:"Issue 3"`
5. Verify all 3 tickets are created successfully
6. As User1, attempt to create ticket #4: `/ticket subject:"Issue 4"`
7. Verify the 4th ticket is blocked

**Expected Results:**
- ✅ First 3 tickets created successfully
- ✅ Fourth ticket creation blocked
- ✅ Error message states limit is 3
- ✅ Each ticket has unique thread

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

### TC-3: Limit Reset After Closing Ticket

**Objective:** Verify that closing a ticket decrements the user's active ticket count.

**Steps:**
1. Configure limit to 1 ticket per user
2. As User1, create ticket #1
3. Verify User1 cannot create a second ticket
4. As staff, close ticket #1 using the "Close Ticket" button
5. Verify ticket #1 is closed and thread is archived
6. As User1, create ticket #2
7. Verify ticket #2 is created successfully

**Expected Results:**
- ✅ Initial ticket created
- ✅ Second ticket blocked before closing first
- ✅ After closing, new ticket can be created
- ✅ Closed ticket no longer counts toward limit

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

### TC-4: Limit Increases After Reopening Ticket

**Objective:** Verify that reopening a ticket counts toward the active ticket limit.

**Steps:**
1. Configure limit to 1 ticket per user
2. As User1, create ticket #1
3. As staff, close ticket #1
4. As User1, create ticket #2 (should succeed)
5. As staff, reopen ticket #1 using the "Reopen Ticket" button
6. Verify User1 now has 2 active tickets
7. As User1, attempt to create ticket #3
8. Verify ticket #3 is blocked (limit exceeded)

**Expected Results:**
- ✅ Reopened ticket counts toward limit
- ✅ User with 2 active tickets (limit=1) cannot create more
- ✅ Error message reflects current limit

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

### TC-5: Per-User Isolation

**Objective:** Verify that ticket limits are enforced per-user, not globally.

**Steps:**
1. Configure limit to 2 tickets per user
2. As User1, create 2 tickets
3. Verify User1 cannot create a 3rd ticket
4. As User2, create 2 tickets
5. Verify User2 can create tickets despite User1's tickets
6. Verify User2 cannot create a 3rd ticket
7. As User3, create 1 ticket
8. Verify User3 can create tickets

**Expected Results:**
- ✅ Each user has independent ticket counter
- ✅ User1's tickets don't affect User2's limit
- ✅ All users subject to same maxTicketsPerUser config
- ✅ Each user blocked at their individual limit

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

### TC-6: Per-Guild Isolation

**Objective:** Verify that ticket limits are per-guild (user can have tickets in multiple guilds).

**Prerequisites:** Bot must be in at least 2 test servers with the same test user.

**Steps:**
1. In Guild1, configure limit to 1 ticket per user
2. In Guild2, configure limit to 1 ticket per user
3. As User1 in Guild1, create ticket #1
4. Verify User1 cannot create a second ticket in Guild1
5. As User1 in Guild2, create ticket #1
6. Verify ticket is created successfully in Guild2
7. Verify User1 has 1 active ticket in each guild

**Expected Results:**
- ✅ Limits enforced per-guild
- ✅ User can have tickets in multiple guilds simultaneously
- ✅ Each guild maintains separate ticket counters

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

### TC-7: Claimed Tickets Count Toward Limit

**Objective:** Verify that claimed tickets still count toward the user's limit.

**Steps:**
1. Configure limit to 1 ticket per user
2. As User1, create ticket #1
3. As staff, claim ticket #1 using the "Claim Ticket" button
4. Verify ticket status changes to CLAIMED
5. As User1, attempt to create ticket #2
6. Verify ticket #2 is blocked

**Expected Results:**
- ✅ Claimed tickets count toward limit
- ✅ User cannot create new tickets while having claimed tickets
- ✅ Status change to CLAIMED doesn't reset counter

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

### TC-8: Updating Limit Applies Immediately

**Objective:** Verify that changing the maxTicketsPerUser config takes effect immediately.

**Steps:**
1. Configure limit to 1 ticket per user
2. As User1, create ticket #1
3. As admin, update limit: `/ticket-setup set-max-tickets value:2`
4. As User1, attempt to create ticket #2
5. Verify ticket #2 is created successfully
6. As User1, attempt to create ticket #3
7. Verify ticket #3 is blocked

**Expected Results:**
- ✅ Config update applies immediately
- ✅ Users can create tickets up to new limit
- ✅ New limit is enforced correctly

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

### TC-9: Limit Set to Zero (Unlimited)

**Objective:** Verify behavior when limit is set to 0 (if supported).

**Steps:**
1. As admin, attempt: `/ticket-setup set-max-tickets value:0`
2. Check if this is allowed or if minimum is 1
3. If allowed, verify users can create multiple tickets without limit

**Expected Results:**
- Document actual behavior: unlimited or minimum enforced

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed | ⚠️ Not Applicable

---

### TC-10: High Limit Stress Test

**Objective:** Verify system handles high limits correctly.

**Steps:**
1. Configure limit to 10 tickets per user
2. As User1, create 10 tickets sequentially
3. Verify all 10 tickets are created successfully
4. Verify ticket numbers increment correctly (1-10)
5. Verify each ticket has a unique thread
6. As User1, attempt to create 11th ticket
7. Verify 11th ticket is blocked

**Expected Results:**
- ✅ System handles 10 active tickets per user
- ✅ Each ticket has proper database entry
- ✅ Each ticket has proper thread
- ✅ Limit still enforced at 10

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

### TC-11: Error Messages Are User-Friendly

**Objective:** Verify error messages provide clear information.

**Steps:**
1. Configure limit to 2 tickets per user
2. As User1, create 2 tickets
3. As User1, attempt to create a 3rd ticket
4. Examine the error message

**Expected Results:**
- ✅ Error message is ephemeral (only visible to user)
- ✅ Error states current limit (2)
- ✅ Error suggests closing existing ticket
- ✅ No confusing technical jargon
- ✅ Message format: "You have reached the maximum number of open tickets (2). Please close an existing ticket before opening a new one."

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

### TC-12: Database Consistency Check

**Objective:** Verify database accurately tracks active tickets.

**Prerequisites:** Database access for verification queries.

**Steps:**
1. Configure limit to 2 tickets per user
2. As User1, create 2 tickets
3. Query database: `SELECT COUNT(*) FROM ticket WHERE guild_id = ? AND user_id = ? AND active = true`
4. Verify count is 2
5. As staff, close 1 ticket
6. Query database again
7. Verify count is 1
8. As staff, reopen the ticket
9. Query database again
10. Verify count is 2

**SQL Queries:**
```sql
-- Check active ticket count for user
SELECT COUNT(*) FROM ticket
WHERE guild_id = 123456789
  AND user_id = '987654321'
  AND active = true;

-- List all tickets for user with status
SELECT ticket_number, status, active, subject
FROM ticket
WHERE guild_id = 123456789
  AND user_id = '987654321'
ORDER BY ticket_number DESC;
```

**Expected Results:**
- ✅ Database count matches application behavior
- ✅ active flag correctly set/unset
- ✅ Closed tickets have active=false
- ✅ Reopened tickets have active=true

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

## Performance Tests

### PT-1: Rapid Ticket Creation Attempts

**Objective:** Verify system handles rapid ticket creation attempts gracefully.

**Steps:**
1. Configure limit to 1 ticket per user
2. As User1, rapidly execute `/ticket` command 5 times in succession
3. Verify only 1 ticket is created
4. Verify all subsequent attempts show error message
5. Verify no duplicate tickets created
6. Verify no race conditions

**Expected Results:**
- ✅ Only 1 ticket created despite rapid attempts
- ✅ No database errors or race conditions
- ✅ All subsequent requests properly blocked

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

### PT-2: Concurrent Users Creating Tickets

**Objective:** Verify system handles multiple users creating tickets simultaneously.

**Steps:**
1. Configure limit to 2 tickets per user
2. Have 3 users (User1, User2, User3) create tickets simultaneously
3. Verify all tickets created successfully
4. Verify no cross-user interference
5. Verify ticket numbers increment correctly

**Expected Results:**
- ✅ All users can create tickets concurrently
- ✅ No user's limit affected by another user
- ✅ Ticket numbers remain sequential and unique

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

## Edge Cases

### EC-1: Tickets Disabled But User Has Active Tickets

**Objective:** Verify behavior when tickets are disabled after users have active tickets.

**Steps:**
1. Configure limit to 2 tickets per user
2. As User1, create 1 ticket
3. As admin, execute `/ticket-setup disable`
4. As User1, attempt to create another ticket
5. Verify new tickets blocked
6. Verify existing ticket #1 still accessible

**Expected Results:**
- ✅ New tickets cannot be created when disabled
- ✅ Existing tickets remain functional
- ✅ Error message indicates system is disabled

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

### EC-2: User With Active Tickets Leaves Server

**Objective:** Verify system behavior when user leaves server with active tickets.

**Steps:**
1. As User1, create 1 ticket
2. User1 leaves the Discord server
3. As staff, verify ticket still accessible
4. As staff, close the ticket
5. User1 rejoins the server
6. As User1, verify can create new ticket

**Expected Results:**
- ✅ Tickets persist after user leaves
- ✅ Staff can still manage tickets
- ✅ Ticket count resets properly when user rejoins

**Status:** ⬜ Not Tested | ✅ Passed | ❌ Failed

---

## Test Summary

**Total Test Cases:** 15
**Passed:** ___
**Failed:** ___
**Not Tested:** ___
**Not Applicable:** ___

## Test Execution Log

| Test Case | Date | Tester | Result | Notes |
|-----------|------|--------|--------|-------|
| TC-1 | | | | |
| TC-2 | | | | |
| TC-3 | | | | |
| TC-4 | | | | |
| TC-5 | | | | |
| TC-6 | | | | |
| TC-7 | | | | |
| TC-8 | | | | |
| TC-9 | | | | |
| TC-10 | | | | |
| TC-11 | | | | |
| TC-12 | | | | |
| PT-1 | | | | |
| PT-2 | | | | |
| EC-1 | | | | |
| EC-2 | | | | |

## Known Issues

Document any issues found during testing:

1.
2.
3.

## Recommendations

Based on test results:

1.
2.
3.

## Sign-off

**Tested by:** _________________
**Date:** _________________
**Status:** ⬜ Approved | ⬜ Approved with issues | ⬜ Rejected

---

## Automated Test Coverage

The following scenarios are covered by unit tests in `TicketServiceImplTest.kt`:

- ✅ Sequential ticket number generation
- ✅ Required field validation
- ✅ Category storage
- ✅ Count active tickets (zero, multiple)
- ✅ Only active tickets counted
- ✅ Get active user tickets
- ✅ Close ticket sets active=false
- ✅ Closed tickets don't count toward limit
- ✅ Reopen ticket sets active=true
- ✅ Reopened tickets count toward limit
- ✅ Claim ticket updates status
- ✅ Unclaim ticket removes assignment
- ✅ Multiple users independent limits
- ✅ Different guilds independent limits
- ✅ Metrics recorded for all actions

## Quick Verification Script

For rapid verification, execute these commands in sequence:

```
# Setup
/ticket-setup enable
/ticket-setup support-channel #support
/ticket-setup add-staff-role @Staff
/ticket-setup set-max-tickets value:1

# Test 1: Create first ticket (should succeed)
/ticket subject:"Test 1"

# Test 2: Create second ticket (should fail)
/ticket subject:"Test 2"

# Expected: Error message about limit reached

# Test 3: Close first ticket and try again (should succeed)
[Click "Close Ticket" button on ticket #1]
/ticket subject:"Test 3"

# Expected: New ticket created successfully
```

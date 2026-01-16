---
description: Generate test plan and spawn agent(s) to implement tests
---

You just finished implementing a feature. Now create a test plan and hand it off to be implemented.

## Step 1: Generate Test Plan

Write a unit test plan for the changes you just made. Requirements:

- Detail **what** each test verifies - not just "test X method" but what behavior/outcome it checks
- Focus on testing intended functionality, not implementation details
- Include error cases and edge cases
- Include the method signatures and relevant context so the implementer doesn't need to read source files
- Do NOT include target coverage percentages
- Test repository methods but do NOT add tests requiring DynamoDB local

**If the plan has more than 15 tests:** Split into multiple plans. Each plan must:
- Have no overlap in which test files are modified
- Be self-contained with all context needed

## Step 2: Spawn Test-Writing Agent(s)

After generating the plan(s), use the Task tool to spawn subagent(s) to implement the tests.

For each test plan, spawn a `test-automation-specialist` agent with this prompt structure:

```
Read context/UNIT_TESTING_PATTERNS.md first.

Then implement these unit tests:

[YOUR TEST PLAN HERE]
```

If you have multiple test plans, spawn the agents **in parallel** (single message with multiple Task tool calls).

## Output

After spawning, briefly tell the user:
- How many test plans were created
- Which agents were spawned
- What test files will be created/modified

---
description: Complete development cycle: code review, duplication & reusability check, fix issues, test plan, write tests, verify, and commit
---

You just finished implementing a feature. This command runs the full wrapup workflow to review, test, and commit your changes.

**Arguments**: $ARGUMENTS (optional: one-sentence summary of what you implemented)

## Phase 1: Code Review

1. Spawn the `code-reviewer` agent to review uncommitted changes
2. Wait for the review feedback
3. If issues are found:
   - Fix each valid issue
   - Briefly note what you changed
   - If feedback seems incorrect, skip it with explanation
4. If no issues found, proceed to Phase 2

## Phase 2: Duplication & Reusability Check

Spawn a subagent (`Explore`) to look at the diff and flag code that's likely useful in more than this one place. The agent reports; you decide what to act on.

Prompt:

> Read the uncommitted diff (`git diff HEAD` plus untracked files).
>
> **First, look only at the diff** and pick out chunks of new logic that look reusable beyond this immediate caller. Good candidates: normalizing values from external systems (third-party API status codes, magic strings), format parsing/validation (phone, dates, IDs), repeated transformations or serialization patterns. Skip glue code, controller flow, and logic that is specific to one feature.
>
> **Then, for each candidate**, do a targeted search of the codebase to see whether equivalent logic already exists.
>
> Report each candidate as one of:
> - **Already exists** — equivalent logic lives at `path:line`. Show both the new code and the existing helper.
> - **Should be extracted now** — no existing helper, but this is clearly the kind of thing that will be needed again. Explain in one line why (e.g., "every third-party API status string should be normalized in one place"). Suggest where the helper should live.
> - **Worth noting** — the code looks somewhat reusable but you're not sure extracting now is the right call. Describe briefly and let the main agent decide.
>
> Don't make code changes. Cite paths and line numbers. Skip findings that don't fall cleanly into one of the buckets — better to report nothing than to pad. If there is nothing to report, say "No duplication detected."

When the report returns, act on the findings using your own judgment. One thing to watch: for "already exists" findings, before swapping in the existing helper verify it actually fits — same semantics, error handling, nullability. Close-but-not-identical is sometimes a worse fit than leaving the new code alone.

## Phase 3: Build Verification

Run `./gradlew build` to ensure code compiles after any fixes.

If build fails:
- Fix compilation errors
- Re-run build until successful

## Phase 4: Test Plan and Implementation

1. Generate a test plan for your changes following these requirements:
   - Detail **what** each test verifies (behavior/outcome, not just method names)
   - Focus on intended functionality, not implementation details
   - Include error cases and edge cases
   - Include method signatures and context so implementer doesn't need to read source
   - Do NOT include target coverage percentages
   - Do NOT add tests requiring DynamoDB local

2. **If >15 tests**: Split into multiple self-contained plans with no overlap in test files

3. Spawn `test-automation-specialist` agent(s) to implement the tests:
   ```
   Read context/UNIT_TESTING_PATTERNS.md first.

   Then implement these unit tests:

   [TEST PLAN]
   ```

4. If multiple test plans, spawn agents **in parallel** (single message with multiple Task tool calls)

## Phase 5: Final Verification

Run `./gradlew test` to ensure all tests pass.

If tests fail:
- Analyze failures
- Fix issues in code or tests as appropriate
- Re-run until all tests pass

## Phase 6: Context Documentation Check

Review if your changes introduced:
- New architectural patterns
- New API endpoints or changed existing ones
- New DynamoDB access patterns
- Changes to authentication/authorization rules

If yes, ask the user if they want to update the relevant context file in `context/`.

## Phase 7: Commit

Create a git commit with:

1. Stage all relevant changes: `git add -A`
2. Generate a concise commit message:
   - One line summary (50 chars max) describing the change
   - Optionally a blank line + brief body if needed
   - **NEVER mention Claude, AI, or assistant**
   - Use conventional commit style: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`

3. Create the commit:
   ```bash
   git commit -m "$(cat <<'EOF'
   [commit message here]
   EOF
   )"
   ```

4. Show the commit with `git show --stat` so user can verify

## Output Summary

After completing all phases, provide a brief summary:
- What issues were found and fixed in code review
- How many tests were added/modified
- The commit message created
- Any context files that may need updating

---

**Skip options** (include in $ARGUMENTS):
- `--skip-review`: Skip code review phase (user is confident in code)
- `--skip-tests`: Skip test plan/implementation (tests already written)
- `--no-commit`: Run review and tests but don't commit

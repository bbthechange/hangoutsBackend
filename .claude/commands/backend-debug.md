---
description: Debug backend issues with thorough investigation before conclusions
---

You are an experienced software developer debugging an issue in this Spring Boot backend.

## Investigation Requirements

**Be thorough, not fast.** Before reaching any conclusions:

1. **Gather evidence from multiple sources:**
   - Read the relevant code paths end-to-end
   - Check related tests for expected behavior
   - Look at recent changes to involved files (git log/blame)
   - Search for similar patterns elsewhere in the codebase
   - Consult context files in `context/` for feature-specific knowledge
   - Use WebSearch for unfamiliar libraries, error messages, or edge cases

2. **Consider multiple hypotheses.** List at least 2-3 possible causes before investigating. Don't lock onto the first suspicious thing.

3. **Verify, don't assume.** If you think X is the cause, find evidence that confirms or refutes it before stating it as fact.

## Response Format

After thorough investigation, provide a **concise** response:

1. **Root Cause** (1-2 sentences): What's actually wrong
2. **Evidence**: The specific code/behavior that proves it
3. **Fix**: The minimal change needed
4. **Other Candidates Ruled Out** (optional): If you investigated other possibilities, briefly note why they weren't the issue

**Do NOT:**
- Provide lengthy explanations of how things work (unless asked)
- Suggest multiple alternative solutions when one is clearly correct
- Add disclaimers, caveats, or hedging language
- Pad with background context I already know

---

**The issue:** $ARGUMENTS

# Repository Review Prompt Template

This template is used to instruct an AI coding agent to review the current repository state without modifying any files.

The purpose is to identify implementation issues, inconsistencies, missing verification, documentation drift, and workflow problems before the work is considered complete.

## Workflow

Repository State
↓
Review Scope and Sources
↓
Inspect Code and Documentation
↓
Verify Relevant Checks
↓
Report Findings
↓
Human Decision

## Rules

- Write the review report in English.
- Do not modify any files.
- Do not create or switch branches.
- Do not commit, push, merge, or create Pull Requests.
- Use the GitHub Issue, Approved Design, repository state, completed implementation, and verification results as the source of truth.
- Review only the current GitHub Issue scope unless explicitly instructed to perform a broader repository review.
- Do not invent problems or report speculative concerns as confirmed findings.
- Prioritize correctness, safety, maintainability, and scope compliance over personal style preferences.
- Avoid low-value style comments unless they materially improve maintainability.
- Clearly distinguish confirmed findings from unverified concerns.
- If required information is unavailable, report the limitation instead of guessing.
- Follow the Command Execution Policy defined in `AGENTS.md`.
- Proactively execute relevant read-only and verification commands when appropriate.

---

Review the current repository state.

Before starting the review, read the following:

- `AGENTS.md`
- GitHub Issue
- Approved Design (if applicable)
- Implementation summary
- Verification results
- Changed files
- Relevant source code
- Relevant tests
- Relevant repository documentation
- `.ai/context.md`
- `.ai/state.json`

---

## Input

### GitHub Issue

{{GITHUB_ISSUE}}

### Approved Design

{{APPROVED_DESIGN}}

### Implementation Summary

{{IMPLEMENTATION_SUMMARY}}

### Verification Results

{{VERIFICATION_RESULTS}}

### Additional Context

{{ADDITIONAL_CONTEXT}}

---

# Review Areas

Review the repository from the following perspectives.

## 1. Scope Compliance

Confirm that:

- The implementation matches the GitHub Issue.
- The implementation follows the Approved Design.
- No Out of Scope changes were introduced.
- No unrelated files were modified.
- The completed work satisfies the Acceptance Criteria.

---

## 2. Implementation Quality

Review:

- Correctness
- Readability
- Maintainability
- Type safety
- Error handling
- State management
- Data flow
- Naming consistency
- Code duplication
- Unnecessary complexity
- Deprecated APIs or warnings

Only report issues that are relevant to the current implementation.

---

## 3. Verification

Confirm whether relevant verification was performed, such as:

- lint
- tests
- build
- type checking
- manual verification
- HTTP/API verification

If a relevant verification step was not performed, report it as a verification gap.

Do not claim that a verification step passed unless it was actually executed successfully.

---

## 4. Tests

Review whether:

- Existing tests still match the implementation.
- New behavior is appropriately covered when tests are expected.
- Important edge cases are missing.
- Tests verify behavior instead of implementation details.

Do not require tests for documentation-only changes unless a relevant automated check exists.

---

## 5. Documentation Consistency

Review whether:

- `README.md` reflects the current project.
- `AGENTS.md` reflects current AI-facing rules.
- The Playbook reflects the current human-facing workflow.
- Prompt documentation reflects the available prompt templates.
- Checklists remain consistent with the current workflow.
- ADRs exist when significant architectural decisions were introduced.

Do not update documentation during this review.

---

## 6. AI Context

Review whether:

- `.ai/context.md` reflects the current project status.
- `.ai/state.json` reflects the current branch, Issue, status, and next task.
- AI Context contains outdated or contradictory information.
- Repository documentation is unnecessarily duplicated inside `.ai/`.

Do not update AI Context during this review.

---

## 7. Repository State

Review:

- Current branch
- Working tree status
- Changed files
- Recent commits
- Current Pull Request (if available)
- Untracked or generated files
- Accidentally committed temporary files
- Unexpected repository structure changes

---

## 8. Security and Safety

Report clear and relevant issues involving:

- Secrets or credentials
- Unsafe handling of user input
- Missing authorization or access control
- Dangerous file or command operations
- Obvious dependency or configuration risks

Do not perform a full security audit unless explicitly requested.

---

# Severity Levels

Use one of the following severity levels for each finding.

- **Critical** — Immediate security, data loss, or repository integrity risk.
- **High** — Incorrect implementation or Issue requirements are not satisfied.
- **Medium** — Significant maintainability, reliability, or verification problem.
- **Low** — Minor issue that is still worth addressing.
- **Recommendation** — Useful improvement outside the current Issue scope.

Do not inflate severity.

---

# Finding Format

Report each finding using the following format.

```text
Finding ID: R-001
Severity: High
Category: Scope Compliance
Location: app/example/page.tsx:42

Summary:
The implementation includes behavior outside the approved GitHub Issue scope.

Evidence:
The GitHub Issue only requests X, but the implementation also introduces Y.

Impact:
The Pull Request becomes harder to review and contains unrelated behavior.

Recommended Action:
Remove Y or move it into a separate GitHub Issue.
```

If an exact line number is unavailable, provide the most specific file or section possible.

---

# Tasks

1. Confirm the GitHub Issue and review scope.
2. Inspect the current repository state.
3. Review the implementation against the GitHub Issue and Approved Design.
4. Execute relevant read-only and verification commands when appropriate.
5. Review:
   - Code
   - Tests
   - Documentation
   - AI Context
   - Repository state
6. Report findings using the required severity levels.
7. Report verification gaps and unavailable information.
8. Provide a concise final assessment.
9. Do not modify any files.

---

# Expected Output

Provide:

1. Review scope
2. Repository state summary
3. Verification performed
4. Findings ordered by severity
5. Verification gaps
6. Unavailable or unverified information
7. Recommendations (maximum 3)
8. Final assessment

Use the following final assessment format.

```text
Review Result: Changes required

Blocking Findings: 1

Non-blocking Findings: 2

Verification Status: Partial

Recommended Next Step:
Resolve R-001 and rerun the relevant verification.
```

Use one of the following values for **Review Result**:

- Approved
- Approved with recommendations
- Changes required
- Unable to complete review

Use one of the following values for **Verification Status**:

- Complete
- Partial
- Not performed
- Unable to verify

If no findings are identified, explicitly report:

```text
No review findings were identified within the reviewed scope.
```

---

# Out of Scope

- Modify application code
- Modify tests
- Modify repository documentation
- Update `.ai/context.md`
- Update `.ai/state.json`
- Generate an AI handoff document
- Create or modify GitHub Issues
- Create or modify Pull Requests
- Commit, push, merge, or modify Git history
- Implement recommendations
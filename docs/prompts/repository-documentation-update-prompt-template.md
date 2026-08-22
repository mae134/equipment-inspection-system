# Repository Documentation Update Prompt Template

This template is used to instruct an AI coding agent to review and update repository documentation after implementation has been completed.

The purpose is to keep repository documentation accurate without creating unnecessary changes.

## Workflow

Implementation Completed
↓
Review Documentation Impact
↓
Determine Required Updates
↓
Update Affected Documents
↓
Report Decisions
↓
Human Review

## Rules

- Write all updates in English unless the target document is explicitly intended for human-facing Japanese content.
- Use the GitHub Issue, Approved Design, completed implementation, and repository state as the source of truth.
- Review the relevant repository documentation before making changes.
- First determine whether each document requires an update.
- Update only documents that are directly affected by the completed implementation or its impact on the repository workflow.
- Do not modify unaffected documentation.
- Do not add speculative documentation.
- Do not duplicate information that already exists in another authoritative document.
- Preserve the existing language, structure, tone, and responsibility of each document.
- Keep documentation concise, accurate, and current.
- If required information is unavailable, clearly report it instead of guessing.
- If no documentation requires updates, do not modify any files.
- If the implementation does not correspond to the current GitHub Issue, report the mismatch before updating any documentation.

## Protected Documentation

Do not modify project rules, architecture decisions, or core workflow policies unless the GitHub Issue or Approved Design explicitly requires the change.

Protected documentation includes:

- `AGENTS.md`
- Architecture Decision Records
- Core Playbook workflow rules
- Other documents that define project-wide policy or architecture

If a protected document appears outdated but its update is outside the approved scope, report it as a recommendation instead of modifying it.

---

Review repository documentation after completing the implementation.

Before reviewing documentation, read the following:

- `AGENTS.md`
- GitHub Issue
- Approved Design (if applicable)
- Implementation summary
- Verification results
- Changed files
- Relevant repository documentation

---

## Documents to Review

Review applicable documentation, including:

- `README.md`
- `AGENTS.md`
- `docs/playbook/`
- `docs/checklists/`
- `docs/history/`
- Architecture Decision Records (if applicable)
- Other documentation directly related to the implementation

Do not update `.ai/context.md` or `.ai/state.json` with this prompt. Use the dedicated AI Context update prompts instead.

### README Review Checklist

Review whether the following sections are still accurate:

- Project Goals
- Documents
- Development Setup
- Available Scripts
- Development Flow
- AI workflow overview
- Repository structure

If any reviewed section is outdated or inconsistent with the current repository, update the document accordingly.

### Playbook Review Checklist

Review whether the following sections reflect the current workflow:

- Development Flow
- Team Roles
- Prompt Workflow
- Git Workflow
- AI Context Workflow
- Repository Documentation Workflow
- Prompt Template List

When reviewing documentation inventories or template indexes, ensure that every prompt template currently present in the repository is listed exactly once.

If any reviewed section is outdated or inconsistent with the current repository, update the document accordingly.

---

## Decision Criteria

Determine whether a document requires an update based on whether the implementation changes:

- Project setup or usage
- User-facing behavior
- Development workflow
- AI coding agent rules
- Architecture or design decisions
- Verification procedures
- Development checklists
- Historical workflow findings
- Important references or repository structure

A document does not require an update merely because it exists or is related to the project.

---

## Documentation Review

Before modifying any documentation:

- determine the update decision for each reviewed document;
- report the reason for each decision; and
- update every document whose decision is **Updated**.

Wait for Human approval only if explicitly instructed.

---

## Tasks

1. Review the completed implementation and its impact on repository documentation.
2. Determine which documents require updates.
3. Update only the affected documents.
4. Preserve all unaffected content.
5. Report the reason for each update decision.
6. Report relevant documentation improvements that were identified but not implemented because they were outside the approved scope.
7. If no documentation requires updates, report that no documentation changes were necessary.

---

## Expected Output

Provide:

1. Updated documentation files (if any)
2. A documentation review summary
3. A documentation review report in the following format:

Use one of the following values for `Decision`:

- Updated
- No update required
- Recommendation only

Report each reviewed document using the following format:

```text
Document: README.md
Decision: Updated
Reason: ...

Document: AGENTS.md
Decision: No update required
Reason: ...

Document: docs/history/
Decision: Recommendation only
Reason: ...
```

4. Recommendations for out-of-scope documentation improvements (maximum 3)
5. If no documentation was updated, explicitly report that no documentation changes were required.

## Out of Scope

- Update `.ai/context.md`
- Update `.ai/state.json`
- Generate an AI handoff document
- Modify application code
- Change project rules, architecture decisions, or core workflow policies unless explicitly required
- Update unrelated documentation

Prefer reporting "No update required" instead of making unnecessary documentation changes.
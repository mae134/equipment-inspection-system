# Approved Design Prompt Template

This template is used to instruct an AI assistant to generate an Approved Design document before implementation begins.

Generate an Approved Design proposal based on the following information.

The Approved Design will be reviewed by a Human before implementation begins.

The purpose is to separate implementation requirements (What) from implementation decisions (How), allowing Human review before an AI coding agent starts implementation.

## Workflow

GitHub Issue
↓
Review Requirements
↓
Create Approved Design
↓
Human Review
↓
Approved Design
↓
Implementation Prompt
↓
AI Coding Agent

## Rules

- Write the Approved Design in English.
- Output the Approved Design as a Markdown (`.md`) file.
- Use the GitHub Issue as the source of truth for implementation requirements.
- Describe how the implementation should be performed without modifying the GitHub Issue.
- Do not change the approved scope.
- Do not introduce new features that are outside the GitHub Issue.
- Keep the design concise, practical, and implementation-oriented.
- Clearly distinguish confirmed decisions from assumptions.
- If required information is unavailable, report it instead of guessing.
- Treat Additional Context as supplementary guidance. If it conflicts with the GitHub Issue, follow the GitHub Issue.
- The Approved Design is a proposal for Human review and approval.
- Do not assume the design is approved until a Human confirms it.

## Expected Output

Generate a single Markdown (`.md`) file.

The output filename must be:

`approved-design-{{ISSUE_ID}}.md`

The document should be suitable for Human review before creating the Implementation Prompt.

---

Before generating the design, review:

- `AGENTS.md`
- GitHub Issue
- Relevant repository documentation
- Existing architecture (if applicable)

---

## Input

### GitHub Issue

{{GITHUB_ISSUE}}

### Additional Context (Optional)

Provide any additional implementation preferences, design direction, project constraints, or other guidance that is not captured by the GitHub Issue.

Examples:

- Preferred UI style (e.g. Notion-inspired, Apple-inspired)
- Performance considerations
- Temporary project constraints
- Human design decisions

{{ADDITIONAL_CONTEXT}}

---

# Design Sections

Generate the Approved Design using the following sections.

## Goal

Summarize the implementation objective.

---

## Scope

Describe what will be implemented.

---

## Out of Scope

Describe what must not be implemented.

---

## Design Policy

Describe the overall implementation approach.

---

## Architecture Impact

Describe whether the implementation affects:

- Application architecture
- Repository workflow
- AI workflow
- Documentation
- AI Context

If there is no impact, explicitly state that.

---

## Implementation Plan

Describe the planned implementation steps.

---

## Files to Modify

List the files expected to be modified.

Example:

```text
docs/prompts/approved-design-prompt-template.md
README.md
```

---

## Verification Plan

Describe how the implementation will be verified.

Examples:

- Manual review
- lint
- tests
- build
- repository review

Only include verification that is expected to be performed.

---

## Risks

Describe implementation risks, if any.

If none are known, explicitly state:

```text
No significant implementation risks identified.
```

---

## Assumptions

List assumptions made while creating the design.

If none exist, explicitly state:

```text
No assumptions.
```

---

## Expected Deliverables

List the expected deliverables produced by the implementation.

---

## Human Approval Checklist

Before implementation begins, confirm:

- Scope is correct.
- Out of Scope is clear.
- Design matches the GitHub Issue.
- Planned files are appropriate.
- Verification plan is appropriate.

---

## Out of Scope

- Modify repository files
- Generate implementation code
- Update `.ai/context.md`
- Update `.ai/state.json`
- Generate an AI handoff document
- Generate a Pull Request draft
- Create Git metadata
- Modify the GitHub Issue

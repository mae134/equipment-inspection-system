# Git Metadata Generation Prompt Template

This template is used to instruct an AI assistant to generate consistent Git metadata for a GitHub Issue and its completed implementation.

The purpose is to keep the work category consistent across the Git branch, commit message, and Pull Request title.

## Workflow

GitHub Issue
↓
Determine Work Category
↓
Generate Branch Name
↓
Implementation
↓
Review Completed Changes
↓
Generate Commit Message
↓
Generate Pull Request Title

## Rules

- Write the branch name, commit message, and explanations in English.
- Generate the Pull Request title in Japanese.
- Use the GitHub Issue as the source of truth for the work category and branch name.
- Use the completed implementation and changed files as the source of truth for the commit message and Pull Request title.
- Use the same work category across the branch name, commit message, and Pull Request title.
- Do not invent implementation details.
- Do not generate a commit message or Pull Request title before implementation details are available.
- Use lowercase kebab-case for branch names.
- Use Conventional Commits prefixes for commit messages and Pull Request titles.
- Keep names concise and descriptive.
- If the work category is unclear, explain the ambiguity and provide no more than two candidates.

## Work Categories

Use the following category mapping:

| Work Type | Branch Prefix | Commit / Pull Request Prefix |
|-----------|---------------|--------------------------------|
| Feature | `feature/` | `feat:` |
| Documentation | `docs/` | `docs:` |
| Bug Fix | `fix/` | `fix:` |
| Refactoring | `refactor/` | `refactor:` |
| Maintenance | `chore/` | `chore:` |

## Phase 1: Before Implementation

Generate the initial Git metadata from the GitHub Issue.

### Input

#### GitHub Issue

{{GITHUB_ISSUE}}

### Tasks

1. Determine the most appropriate work category.
2. Generate a branch name.
3. Report the corresponding Conventional Commits prefix.
4. Do not generate a final commit message or Pull Request title.

### Output Format

```text
Work Category: Documentation
Branch Name: docs/57-update-prompt-templates
Commit Prefix: docs:
Pull Request Prefix: docs:
Reason: The Issue only adds or modifies prompt documentation.
```

---

## Phase 2: After Implementation

Generate the final Git metadata from the completed implementation.

### Input

#### GitHub Issue

{{GITHUB_ISSUE}}

#### Implementation Summary

{{IMPLEMENTATION_SUMMARY}}

#### Changed Files

{{CHANGED_FILES}}

#### Verification Results

{{VERIFICATION_RESULTS}}

### Tasks

1. Confirm whether the original work category still matches the completed implementation.
2. Generate a commit message that describes the actual completed changes.
3. Generate a Pull Request title that describes the overall Issue outcome.
4. Ensure the branch name, commit message, and Pull Request title use the same work category.
5. If the implementation changed category, clearly explain why.

### Output Format

```text
Work Category: Documentation
Branch Name: docs/57-update-prompt-templates
Commit Message: docs: add Git metadata generation prompt
Pull Request Title: docs: プロンプトテンプレートおよび関連ドキュメントの運用ルールを統一
Reason: The completed changes standardize prompt templates and related documentation rules.
```

## Naming Guidelines

### Branch Name

- Use lowercase kebab-case.
- Start with the selected branch prefix.
- Describe the Issue goal rather than implementation details.

### Branch Naming Convention

Use the following format:

<type>/<issue-number>-<short-description>

Example:

```text
docs/57-update-prompt-templates
```

### Commit Message

- Describe the changes included in the commit.
- Use imperative, concise wording.
- Do not describe work that was not committed.

### Commit Message Convention

Use the following format:

<type>: <summary>

Example:

```text
docs: add Git metadata generation prompt
```

### Pull Request Title

- Generate the title in Japanese.
- Prefix the title with the same Conventional Commits type as the branch and commit message.
- Describe the overall outcome of the GitHub Issue.
- Do not simply copy the commit message when the Pull Request includes broader changes.

Example:

```text
docs: プロンプトテンプレートおよび関連ドキュメントの運用ルールを統一
```

## Out of Scope

- Create or switch Git branches
- Execute Git commits
- Push changes
- Create or merge Pull Requests
- Modify the GitHub Issue
- Generate a Pull Request description

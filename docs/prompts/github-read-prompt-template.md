# GitHub Read Prompt Template

This template is used to instruct an AI assistant or coding agent to safely read GitHub data without performing any write operation.

## Purpose

- Read GitHub data required for planning, review, or decision-making.
- Separate GitHub read operations from GitHub write operations.
- Provide verified repository information for downstream prompts or Human review.
- Never modify GitHub state.

## Operation Mode

Specify one of the following modes:

- `Read`: Read the requested GitHub data.
- `Inspect`: Read and summarize the current state.
- `Compare`: Read and compare GitHub resources.
- `Export`: Read and export GitHub data into a structured format.

If the Operation Mode is missing, unclear, or unsupported, use `Read`.

## Source of Truth

Use the following sources in priority order:

1. Verified GitHub repository state
2. Verified GitHub Project state
3. Verified GitHub metadata
4. Human request

Do not guess or fabricate GitHub data.

## Inputs

- Operation Mode: `{{OPERATION_MODE}}`
- Target Repository: `{{TARGET_REPOSITORY}}`
- Resource: `{{RESOURCE}}`
- Scope: `{{SCOPE}}`
- Filter: `{{FILTER}}`
- Output Format: `{{OUTPUT_FORMAT}}`
- Additional Instructions: `{{ADDITIONAL_INSTRUCTIONS}}`

## Supported Resources

The Resource may be one or more of the following:

- Repository
- Issue
- Pull Request
- Project
- Project Item
- Project Field
- Milestone
- Label
- Branch
- Commit
- Release
- Workflow
- Workflow Run
- Discussion
- Contributor

Unknown resources must not be inferred.

## Supported Scopes

Specify one of the following scopes:

- `Single Resource`
- `Multiple Resources`
- `Entire Repository`

If omitted, use `Single Resource`.

## Workflow

1. Determine the requested Resource and Scope.
2. Read the requested GitHub data.
3. Verify that the retrieved data belongs to the requested repository.
4. Apply the requested filters, if any.
5. Apply the Additional Instructions.
6. Produce the requested output.
7. Do not perform any write operation.

## Output Formats

Supported output formats include:

- Markdown
- JSON
- Table
- Bullet List

If no Output Format is specified, use Markdown.

## Read Rules

- Never create, update, delete, close, reopen, or otherwise modify GitHub resources.
- Do not infer missing repository data.
- Clearly distinguish verified information from unavailable information.
- Preserve GitHub identifiers whenever available.
- Clearly report when requested resources do not exist.
- Apply Additional Instructions only after verified data has been retrieved.

## Capability Fallback

If the required GitHub read capability is unavailable:

- State which data could not be retrieved.
- Explain which capabilities are missing.
- Do not fabricate GitHub state.
- Do not substitute assumed or cached data unless explicitly requested.

If authentication or repository permissions are insufficient:

- Stop the operation.
- Report the missing capability.
- Do not broaden repository access.

## Character and Output Size Limits

Prefer returning the complete result directly.

If a character limit, message-size limit, terminal limit, tool limit, or output truncation risk prevents returning the complete result:

1. Write the complete output to a Markdown (`.md`) file.
2. Do not omit information solely to fit the limit.
3. Return the file path or artifact reference.
4. Clearly state that the complete output was written to the file.
5. Do not claim that a file was created unless its existence was verified.

## Output

### Operation Summary

- Operation Mode: `{{RESOLVED_OPERATION_MODE}}`
- Repository: `{{TARGET_REPOSITORY}}`
- Resource: `{{RESOURCE}}`
- Scope: `{{SCOPE}}`
- Filter: `{{FILTER}}`
- Output Format: `{{OUTPUT_FORMAT}}`

### Result

{{RESULT}}

### Verification

{{VERIFICATION}}

### Execution Result

- Status: `{{SUCCEEDED_FAILED_OR_NOT_EXECUTED}}`
- Verified Repository: `{{VERIFIED_REPOSITORY}}`
- Verified Resource: `{{VERIFIED_RESOURCE}}`

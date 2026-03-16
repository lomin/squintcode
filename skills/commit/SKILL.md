---
name: commit
description: Create atomic, stacked git commits from staged changes. Use when asked to craft commits, split staged changes into logical commits, write commit messages, or reorganize staged hunks/files into multiple commits.
argument-hint: "[sign]"
---

# Commit

Each commit is a unit of reasoning — it captures one coherent thought, what
changed, why it changed, and what was considered.

Create atomic, stacked commits from staged changes only. Keep each commit bisectable,
revertable, and self-explanatory. Never modify the working tree; use index-only operations
throughout. Use the repo's temp-index commit tasks to avoid staging leaks.

## Workflow

### 0. Preconditions

Check before anything else. If any fail, report which one and stop.

1. **No in-progress operations:** Bail if any exist:
   - `.git/MERGE_HEAD` (merge in progress)
   - `.git/rebase-merge/` or `.git/rebase-apply/` (rebase in progress)
   - `.git/CHERRY_PICK_HEAD` (cherry-pick in progress)
2. **Staged changes exist:** `git diff --cached --quiet` exits 0 → nothing staged, stop.

### 1. Analyze staged changes

```bash
bb git:plan                 # grouped by status (A/M/D)
git diff --cached --stat    # line counts
git diff --cached           # full diff
git log --oneline -5        # recent commit style
```

### 2. Snapshot staged file set (safety guard)

```bash
bb git:snapshot
```

This snapshot defines the only files that may be committed in this session.

### 3. Plan commit groups

Order commits as:
1. Interface/type changes (so downstream code can type-check)
2. Cleanup/refactor
3. Feature/bugfix (implementation)
4. Tests/docs/config

Keep one logical change per commit. Do not mix formatting with logic.

**Atomicity checks** — run these before committing each group:

- **"And" test:** If describing the commit requires "and" joining unrelated
  clauses, split it.
- **Revert test:** Could this commit be reverted independently without
  breaking unrelated code?
- **One-sentence test:** If you can't describe it in one sentence, it's too
  large.
- **Size check:** >~300 lines changed or 4+ unrelated directories → re-examine
  whether the commit should be split.

### 4. Create commits (temp index)

Use `bb git:commit` directly with paths for each logical change. This uses a temp index
to commit only the specified paths, then unstages them from the main index.

```bash
bb git:commit "[type]: [imperative summary]" path1 path2...
```

Repeat for each logical group. The tool automatically unstages committed paths, so
remaining staged files stay ready for the next commit.

**For renames:** Specify only the NEW (destination) path. The tooling handles the old
path automatically. Renames should generally be committed together as they represent
an atomic refactoring operation.

## Commit message format

```
<type>(<optional-scope>): <imperative summary>

<optional body: why and what>
```

Subject rules:
- Aim for 50 characters. Hard limit 72. The `type(scope):` prefix counts.
- Imperative mood — the subject completes: "If applied, this commit will..."
- No process language
- "Verb + object + qualifier" when possible

Types:
- feat, fix, refactor, test, docs, chore, style, perf, build, ci

Body — REQUIRED when any of these apply:
- More than 1 file changed
- Behavior altered (not just formatting/rename)
- Spans multiple modules
- Subject alone doesn't explain why

Body optional:
- Single-file renames or moves
- Formatting-only changes
- Trivial config tweaks
- Doc typo fixes

Body template (include applicable sections):
1. **Why** — motivation, what was broken/missing/suboptimal
2. **What was considered** — alternatives and why this approach was chosen
3. **What this enables/blocks** — next steps opened or closed
4. **Context the diff can't show** — links, paper refs, constraints

Wrap body lines at ~72 chars. Note behavioral impact or risk.

No author attribution:
- Do not add Co-Authored-By or Signed-off-by lines

## Grouping guidelines

Separate commits:
- Refactor vs new behavior
- Different modules for different reasons
- Formatting vs logic
- Generated/state churn vs semantic changes

Same commit:
- Implementation + its tests
- API change + all consumer updates

## Cross-module commits

When a commit spans multiple modules, the body MUST explain why it can't be
split. If a cross-module change CAN be split, split it.

## Pre-commit checklist

Do not create the commit until all applicable items pass:

- [ ] One logical thing? (passes the "and" test)
- [ ] Independently revertible?
- [ ] Body explains why? (if body is required per the trigger list above)
- [ ] Cross-module commit explains why it's atomic?
- [ ] Subject ≤50 chars? (hard limit 72)

## Error handling

- Pre-commit hook fails: stop and report
- Cannot split cleanly: ask for guidance
- `bb git:commit` fails snapshot validation or temp-index mismatch: stop and report

## Commit signing

This repo uses SSH commit signing via 1Password (`gpg.ssh.program=op-ssh-sign`).
The SSH private key lives exclusively in 1Password — only the `.pub` is on disk.

### Branch-based signing policy

| Branch pattern | `$ARGUMENTS` | Signing behavior |
|---------------|-------------|-----------------|
| `wip/*`       | *(empty)*   | **No signing.** Use `--no-gpg-sign` automatically. |
| `wip/*`       | `sign`      | **Signed.** Use `bb git:commit` (1Password). |
| All others    | *(any)*     | **Signed.** Use default `commit.gpgsign=true` (1Password). |

### Workflow: detect and apply

Before creating commits, check the current branch and `$ARGUMENTS`:

```bash
branch=$(git rev-parse --abbrev-ref HEAD)
```

- If `branch` starts with `wip/` **and** `$ARGUMENTS` is empty or does not
  contain `sign`: use `git commit --no-gpg-sign -m "message"` (since
  `bb git:commit` does not support `--no-gpg-sign`, fall back to raw
  `git add` + `git commit --no-gpg-sign`).
- If `branch` starts with `wip/` **and** `$ARGUMENTS` contains `sign`:
  use `bb git:commit` as normal (signing via 1Password).
- Otherwise: use `bb git:commit` as normal (signing happens via 1Password).

### When 1Password fails on a non-wip branch

If signing fails unexpectedly (e.g., `failed to fill whole buffer`, agent not
running), create a `wip/` branch first, commit there unsigned, then
rebase onto the target branch with signing when 1Password is available.

## Known limitations

**Do NOT use the unstage→re-add pattern.** Using `bb git:unstage` followed by `git add`
breaks rename tracking because `git add` only stages the new file without the old
file's deletion. Instead, use `bb git:commit` directly with specific paths - it
handles renames atomically via temp index.

**`bb git:commit` does not support `--no-gpg-sign`.** On `wip/` branches, fall
back to raw `git add` + `git commit --no-gpg-sign`.

**Large changesets (>100 files) can exceed shell argument limits.** Write
paths to a temp file and use `--paths-from`:

    git diff --cached --name-only -- src/ test/ > /tmp/paths.txt
    bb git:commit --paths-from /tmp/paths.txt "message"

**Binary files modified by scripts need explicit staging.** After running
scripts, check `git status` for unstaged binary files before taking a snapshot.
These files don't appear in `git diff` output by default.

## Output

```
## Commits Created

1. [hash] [type]: [description]
   Files: [list]
   Why: [brief reason]

Total: N commits
```

---
name: commit
description: Create atomic, stacked git commits from staged changes. Use when asked to craft commits, split staged changes into logical commits, write commit messages, or reorganize staged files into multiple commits.
model: haiku
---

# Commit

Each commit is a unit of reasoning — it captures one coherent thought, what
changed, why it changed, and what was considered.

Create atomic, stacked commits from staged changes only. Keep each commit
bisectable, revertable, and self-explanatory. Never modify the working tree;
every step below is an index-only operation through the repo's `bb git:*`
tasks, which commit from a temp index so nothing leaks between commits.

Signing is not your concern: `bb git:commit` signs when a signer is available
(the host, via 1Password) and commits unsigned when it is not (the
devcontainer). Unsigned commits live on a `wip/` branch and reach `main`
through `/land` on the host, which re-signs them.

## Workflow

### 0. Preconditions

```bash
bb git:preflight
```

It fails when a merge, rebase or cherry-pick is in progress, or when nothing
is staged. Report the reason and stop. It also prints the branch and whether
commits will be signed; on an unsigned session the branch must be `wip/*` —
if it is not, stop and ask for a branch.

### 1. Analyze staged changes

```bash
bb git:plan                 # grouped by status (A/M/D/R)
git diff --cached --stat    # line counts
git log --oneline -5        # recent commit style
```

Check the diff size from the `--stat` summary line: insertions plus deletions.

- **≤500 lines changed:** read the full diff in one shot with `git diff --cached`.
- **>500 lines changed:** do NOT read a monolithic diff. Plan tentative
  groups from `--stat` (by brick, by change type), then read one group at a
  time with `git diff --cached -- path1 path2`. Move to the next group only
  after planning that commit.

### 2. Snapshot the staged file set (safety guard)

```bash
bb git:snapshot
```

The snapshot defines the only files that may be committed in this session;
`bb git:commit --from-snapshot` refuses anything staged later.

### 3. Plan commit groups

Order commits as:
1. Interface changes (so downstream code compiles)
2. Cleanup/refactor
3. Feature/bugfix (implementation)
4. Tests/docs/config

Keep one logical change per commit. Do not mix formatting with logic.

**Atomicity checks** — run these before committing each group:

- **"And" test:** if describing the commit needs "and" joining unrelated
  clauses, split it.
- **Revert test:** could this commit be reverted alone without breaking
  unrelated code?
- **One-sentence test:** if you can't describe it in one sentence, it's too
  large.
- **Size check:** >~300 lines changed or 4+ unrelated directories → re-examine.

### 4. Create commits

**Body gate — check before each commit:** does this group have >1 file or
alter behaviour? If yes, draft a body before composing the command. A subject
that "feels descriptive enough" is not a substitute — the body exists for
`git log` archaeology.

```bash
bb git:commit --from-snapshot "[type](scope): [imperative summary]" path1 path2...
```

Repeat for each logical group. The task unstages the committed paths, so the
remaining staged files stay ready for the next commit. A body goes in the
message after a blank line; keep the whole message in one quoted argument.

**For renames:** give only the NEW (destination) path; the tooling handles the
old path. Commit a rename together with the edits that motivated it.

After the final commit, verify nothing is left staged: `git diff --cached --quiet`.

## Commit message format

```
<type>(<optional-scope>): <imperative summary>

<optional body: why and what>
```

Subject rules:
- Aim for 50 characters; hard limit 72. The `type(scope):` prefix counts.
- Imperative mood — the subject completes "If applied, this commit will…".
- No process language.
- "Verb + object + qualifier" when possible.

Types: feat, fix, refactor, test, docs, chore, style, perf, build, ci

Scopes: the brick or area touched — `components/<name>` → `<name>`, `bases/app`
→ `app`, `projects/<name>` → `<name>`, `bb/` → `scripts`, `.devcontainer/` →
`devcontainer`, `skills/` → `skills`. Omit the scope when the type alone is
unambiguous.

Body — REQUIRED when any of these apply:
- More than 1 file changed
- Behaviour altered (not just formatting/rename)
- Exploration or dead-end work
- Spans multiple bricks
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

Wrap body lines at ~72 characters. Note behavioural impact or risk.

No author attribution: do not add Co-Authored-By or Signed-off-by lines.

## Grouping guidelines

Separate commits:
- Refactor vs new behaviour
- Different bricks for different reasons
- Formatting vs logic
- Generated churn vs semantic changes

Same commit:
- Implementation + its tests
- Interface change + all consumer updates
- Binary or fixture files with the content that caused them to change

## Cross-brick commits

When a commit spans multiple bricks, the body MUST explain why it can't be
split:

```
feat(ex,ex-otel): add span links to the exchange surface

The surface declares the link slot and the extension fills it; adding one
without the other leaves an exchange that compiles but traces nothing.
```

If a cross-brick change CAN be split, split it.

## Pre-commit checklist

- [ ] One logical thing? (passes the "and" test)
- [ ] Independently revertible?
- [ ] Body explains why? (if required per the trigger list above)
- [ ] Exploration commit records what was learned?
- [ ] Cross-brick commit explains why it's atomic?
- [ ] Subject ≤50 chars? (hard limit 72)

## Error handling

- `bb git:preflight` fails: stop and report.
- Cannot split cleanly: ask for guidance.
- `bb git:commit` fails snapshot validation or reports a temp-index mismatch:
  stop and report; nothing was committed.

## Known limitations

**Large changesets (>100 files) can exceed shell argument limits.** Write the
paths to a file and use `--paths-from`. Always use `-z` so Unicode paths stay
raw; without it `git diff --name-only` octal-escapes them.

```bash
git diff --cached -z --name-only -- src/ test/ | tr '\0' '\n' > /tmp/paths.txt
bb git:commit --from-snapshot --paths-from /tmp/paths.txt "message"
```

**Files written by scripts need explicit staging.** After generating fixtures
or baselines, check `git status` for unstaged files before taking the snapshot.

## Output

```
## Commits Created

1. [hash] [type]: [description]
   Files: [list]
   Why: [brief reason]

Total: N commits
```

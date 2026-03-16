## Why a Temp Index

`git commit` normally commits everything staged. When splitting staged changes
into multiple atomic commits, you need to selectively commit some paths while
leaving others staged for the next commit.

`bb git:commit` solves this by building a temporary index containing only the
requested paths, committing from that index, then unstaging the committed
paths from the main index. The main index is never modified during the commit
itself — only afterward, to remove what was committed.

## Workflow

    $ bb git:snapshot                  # lock down the staged file set
    $ bb git:plan                      # review grouped by status
    $ bb git:commit "msg" file1 file2  # commit a subset
    $ bb git:commit "msg" file3        # commit another subset
    $ bb git:staged                    # verify nothing left

Each `bb git:commit` automatically unstages committed paths, so remaining
staged files are ready for the next commit without manual intervention.

## Related Commands

    bb git:snapshot [out]   Save staged file list (safety guard for multi-commit)
    bb git:plan             Show staged changes grouped by status (A/M/D/R)
    bb git:staged           List staged files with status codes
    bb git:unstage <path>   Unstage files (all states including deletes)

## Renames

Git renames appear as a paired delete + add. All git:* commands use the
**new (destination) path** to refer to a rename:

    $ bb git:staged
    R100  old/path.clj -> new/path.clj

    $ bb git:commit "refactor: move path" new/path.clj

Specifying the old path is an error — the command suggests the correct path.
The old path's deletion is handled automatically.

## Snapshot Validation

For multi-commit sessions, `--from-snapshot` (or `-s <file>`) validates that
every requested path exists in the snapshot taken at the start:

    $ bb git:snapshot
    $ bb git:commit --from-snapshot "first commit" file1 file2

This catches typos and ensures you don't accidentally commit a path that was
staged after the snapshot was taken.

## Large Changesets

When committing more than ~100 files (e.g., migrations), shell argument
expansion may silently fail. Use `--paths-from` to read paths from a file:

    $ git diff --cached --name-only -- src/ test/ > /tmp/paths.txt
    $ bb git:commit --paths-from /tmp/paths.txt "refactor: rename archives"

Paths from `--paths-from` are merged with any positional path arguments.
The file format is one path per line (or NUL-separated).

# REQUIREMENTS

REQ-1: Commit uses a temp index built from HEAD + requested staged paths.
       The main index is not modified until after the commit succeeds.

REQ-2: Committed paths are automatically unstaged from the main index
       unless --keep-staged is set.

REQ-3: Temp index contents are verified against expected paths before
       committing. Mismatch aborts the operation.

REQ-4: Renames are specified by new (destination) path only. Old path
       deletion is handled automatically. Specifying an old rename path
       is an error with a corrective suggestion.

REQ-5: Snapshot validation (--from-snapshot, --snapshot) rejects paths
       not present in the snapshot file.

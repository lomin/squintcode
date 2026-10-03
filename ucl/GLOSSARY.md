# ucl

The language of `ucl` ("Uncommon Lisp"): a Common Lisp-shaped contract for
high-performance code that runs unchanged on several hosts. The design of
record is [README.md](./README.md).

## Language

### Hosts and the contract

**Contract**:
The host-agnostic vocabulary and its meaning; every host implements all of it.
_Avoid_: core, spec

**Host**:
A language a ucl program runs on: Squint, ClojureScript or Clojure (JVM).
_Avoid_: platform, target, dialect

**Backend**:
A host's implementation of the contract.
_Avoid_: driver, adapter

### Bindings

**Variable**:
A name bound by ucl that the program can assign with `setf`, `incf` or
`decf`: one bound by `ucl/let` or `ucl/let*`, or a `ucl/defun` /
`ucl/defmethod` parameter.
_Avoid_: mutable local, var, cell, atom

**Local**:
A name bound by Clojure itself (`let`, `loop`, `fn`, `ucl/dotimes`' counter);
it can never be assigned.
_Avoid_: variable (for these), immutable variable

**Place**:
A form that names a storage location `setf` can write: a variable, or an
`elt`, `gethash` or `slot-value` form.
_Avoid_: lvalue, reference, accessor

### Positions

**Statement position**:
Where a form's value is discarded: any form of a body but the last.

**Return position**:
Where a form's value becomes the value of the enclosing form: the last form of
a body, or either branch of an `if` in return position.
_Avoid_: tail position

**Expression position**:
Where a form's value is used by another form: an argument, or the init of a
`let` binding.

# Glyph Semantics

This document details the semantic rules and runtime behavior of the Glyph language.

# Glyph Language – Semantic Rules

## 1. Type Inference and Checking
- Types are inferred unless explicitly annotated.
- Type errors are reported at compile-time (if possible), otherwise at runtime.
- Gradual typing: code can mix typed and untyped variables, but type errors in typed code are strict.
- Function parameters and return types can be inferred from usage if not annotated.
- Generic types are resolved at call-site.

**Example:**
```glyph
let x = 5         # x: int
let y: float = 2  # y: float
func add(a, b):   # a, b: inferred from usage
    return a + b
```

---

## 2. Scoping and Variable Lifetimes
- Variables are block-scoped (scoped to the nearest INDENT/DEDENT block).
- Function, entity, and component fields are scoped to their declaration.
- Shadowing is allowed but discouraged; a warning is issued if a variable is redefined in an inner scope.

---

## 3. Mutability and Constness
- `let` variables are immutable after assignment.
- `var` variables are mutable.
- `const` variables are compile-time constants and must be initialized with a literal or constant expression.
- Attempting to mutate an immutable or constant variable results in a compile-time or runtime error.

---

## 4. Pattern Matching Resolution
- Patterns are matched top-to-bottom; the first matching case is executed.
- Patterns can destructure enums, tuples, lists, and objects.
- The wildcard `_` matches any value.
- If no pattern matches and an `else` is present, the `else` block is executed; otherwise, a runtime error is raised.

**Example:**
```glyph
match event:
    case PlayerDamaged(player, amount):
        # ...
    case _:
        # fallback
```

---

## 5. Trait/Interface Resolution
- Traits define required methods; entities/components implement traits by providing those methods.
- At runtime, trait conformance is checked when a trait-typed value is used.
- Duck typing is supported: if an entity/component has the required methods, it is considered to implement the trait.
- Static analysis can warn if a trait is declared but not fully implemented.

---

## 6. Async/Await and Concurrency
- `async func` defines a coroutine; `await` suspends execution until the awaited value is ready.
- The runtime schedules async functions using an event loop.
- Async systems can yield control and resume on the next tick or when awaited events complete.
- Deadlocks and unhandled promises are reported as runtime errors.

---

## 7. Error Handling and Propagation
- `try:` blocks catch exceptions raised in their body.
- `catch` receives the error object.
- Uncaught errors propagate up the call stack; if unhandled, they terminate the current system or function.
- Errors can be user-defined or built-in (e.g., TypeError, ValueError, NullReferenceError).

**Example:**
```glyph
try:
    risky_operation()
catch err:
    print("Error:", err)
```

---

## 8. ECS and Event Semantics
- Entities are unique objects composed of components.
- Components are data containers; systems operate on entities with matching components.
- Systems are scheduled based on triggers (tick, input, event, etc.) and priority.
- Events are dispatched via `emit` and handled by `on` or system triggers.
- Event payloads are type-checked against event definitions.
- Entity and component lifetimes are managed by the ECS registry; destroyed entities/components are removed from all systems.

---

## 9. Attribute/Decorator Semantics
- Attributes (e.g., `@networked`) attach metadata to declarations.
- The runtime and tooling can use attributes to modify behavior (e.g., network sync, serialization).
- Custom attributes can be defined and queried via reflection APIs.

---

## 10. Macro Expansion
- Macros are expanded at compile-time.
- Macro arguments are substituted into the macro body before parsing.
- Macros cannot introduce new syntax but can generate code from existing constructs.
- Recursive macros are disallowed to prevent infinite expansion.

---

## 11. Testing Semantics
- `test` blocks are collected and run by the test runner.
- Tests are isolated: each test runs in a fresh environment.
- Assertions throw errors if conditions are not met.
- Test results are reported with pass/fail and error messages.

---

## 12. FFI/Interop Semantics
- `extern` declarations bind to host language functions/modules.
- FFI calls are type-checked at the boundary; type mismatches raise errors.
- Host errors are propagated as exceptions in Glyph.

---

## 13. Documentation Comments
- `///` doc comments are attached to the following declaration.
- Doc comments are extracted for documentation generation and editor tooltips.

---

## 14. Indentation and Block Structure
- Indentation is significant; INDENT/DEDENT tokens define block boundaries.
- Mixing tabs and spaces is disallowed; the first indentation style is enforced throughout the file.
- Indentation errors are reported at parse time.

---

## 15. Miscellaneous
- All identifiers are Unicode; reserved keywords cannot be used as identifiers.
- Comments (`#` and `### ... ###`) are ignored by the parser.
- The entry point for execution is the top-level program or a specified `main` system/function. 
# Glyph Standard Library Reference

This document describes the built-in types, functions, and modules available in Glyph.

# Glyph Language – Standard Library Outline

## 1. Core Types

### Numeric Types
- `int`: Integer numbers
- `float`: Floating-point numbers

### Boolean
- `bool`: `true` or `false`

### String
- `string`: Unicode text

### Collections
- `list<T>`: Ordered, mutable sequence
- `dict<K, V>`: Key-value mapping

### Math Types
- `Vec2`, `Vec3`: 2D/3D vectors
- `Color`: RGBA color
- `Rect`, `Bounds`: Geometric primitives

### ECS Types
- `Entity`: Reference to an entity
- `Component`: Base type for components
- `System`: Base type for systems
- `Event`: Base type for events

### Option/Result Types
- `Option<T>`: Nullable/optional value
- `Result<T, E>`: Success or error value

---

## 2. Essential Functions

### Math
- `abs(x)`, `min(a, b)`, `max(a, b)`, `clamp(x, min, max)`
- `sin(x)`, `cos(x)`, `tan(x)`, `sqrt(x)`, `pow(x, y)`
- `lerp(a, b, t)`: Linear interpolation

### String
- `len(s)`: Length of string
- `s.upper()`, `s.lower()`, `s.split(sep)`, `s.join(list)`
- `s.replace(old, new)`

### List/Dict
- `len(list)`, `list.append(x)`, `list.remove(x)`, `list.sort()`
- `dict.keys()`, `dict.values()`, `dict.items()`

### IO
- `print(x)`: Output to console
- `input(prompt)`: Read user input (if interactive)
- `read_file(path)`, `write_file(path, data)`

### Entity/ECS
- `find(Type, [filter])`: Query entities
- `emit(Event, payload)`: Emit event
- `destroy(entity)`: Remove entity
- `add_component(entity, component)`
- `remove_component(entity, ComponentType)`

### Async/Concurrency
- `sleep(ms)`: Pause async function
- `await promise`: Await async result

---

## 3. Modules

### Math
- `import math`
- Provides advanced math functions/constants

### Collections
- `import collections`
- Extra data structures (set, queue, etc.)

### ECS
- `import ecs`
- ECS helpers, registry access

### Input
- `import input`
- Functions for keyboard, mouse, gamepad, etc.

### Networking
- `import net`
- WebSocket, ENet, HTTP helpers

### Async
- `import async`
- Utilities for async programming

### Time
- `import time`
- `now()`, `sleep(ms)`, timers

### OS/Filesystem
- `import os`
- File and directory operations

---

## 4. Importing and Extending the Standard Library
- Use `import module` or `from module import name` to access stdlib features.
- Users can extend the stdlib by creating their own modules and placing them in the project or a shared library path.
- FFI allows binding to host language libraries for advanced extensions.

**Example:**
```glyph
import math
let r = math.sqrt(16)

from collections import set
let s = set([1, 2, 3])
```

---

## 5. Notes
- The standard library is available in all Glyph environments (interpreter, compiler, editor runtime).
- Some modules (e.g., `os`, `net`) may be restricted in sandboxed or web environments for security.
- The stdlib is versioned and documented; users can check compatibility and browse docs via the editor. 
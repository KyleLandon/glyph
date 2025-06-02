# Core Concepts in Glyph

This document introduces the foundational ideas and patterns that define the Glyph language and engine. Each concept is illustrated with concise explanations and code examples.

---

## 1. Entity-Component-System (ECS)

Glyph is built around the ECS paradigm:
- **Entities** are unique IDs representing objects in your world.
- **Components** are data attached to entities (e.g., Position, Health).
- **Systems** are logic that runs on entities with specific components.

```glyph
entity Player:
    Position(x=0, y=0)
    Health(value=100)
```

---

## 2. Components

Components are simple data structures:
```glyph
component Position:
    x: float
    y: float

component Health:
    value: int
```

---

## 3. Systems

Systems define logic that runs on matching entities:
```glyph
system Move:
    for e in entities.with(Position, Velocity):
        e.Position.x += e.Velocity.dx
        e.Position.y += e.Velocity.dy
```

---

## 4. Events

Events are messages that trigger reactions across systems:
```glyph
event Damage:
    amount: int

on Damage as dmg for e in entities.with(Health):
    e.Health.value -= dmg.amount
```

---

## 5. Queries

Query entities by their components:
```glyph
for enemy in entities.with(Position, EnemyTag):
    # Do something with each enemy
```

---

## 6. Async/Await

Glyph supports asynchronous code for real-time and multiplayer:
```glyph
async system NetworkSync:
    while true:
        data = await receive_network_update()
        apply_update(data)
```

---

## 7. Traits & Interfaces

Traits define shared behavior:
```glyph
trait Damageable:
    fn take_damage(amount: int)

entity Wall implements Damageable:
    Health(value=200)
    fn take_damage(amount):
        self.Health.value -= amount
```

---

## 8. Pattern Matching

Pattern matching simplifies control flow:
```glyph
match event:
    case Damage(amount):
        handle_damage(amount)
    case Heal(amount):
        handle_heal(amount)
    case _:
        log("Unknown event")
```

---

## 9. Option & Result Types

Handle absence and errors safely:
```glyph
fn find_player(name: str) -> Option[Entity]:
    for e in entities.with(PlayerTag):
        if e.name == name:
            return Some(e)
    return None

fn try_divide(a: int, b: int) -> Result[int, str]:
    if b == 0:
        return Err("Division by zero")
    return Ok(a / b)
```

---

## 10. Visual Scripting (GlyphFlow)

Every code construct can be represented as nodes:
- **Nodes**: Logic, math, event, flow, variable, custom script
- **AST ↔ Node sync**: Edits in code or nodes are reflected in both views

Example: A node graph for player movement would mirror the `Move` system above, with nodes for input, position, and velocity.

---

## 11. Additional Concepts
- **Macros & Attributes**: For code generation and metadata
- **Doc Comments**: `///` for documentation
- **Testing**: Built-in test blocks for systems and functions

---

For more details, see the [Language Tour](language_tour.md) and [Sample Programs](sample_programs.md). 
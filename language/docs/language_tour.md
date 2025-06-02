# Glyph Language Tour

## Syntax Overview
- Indentation-based blocks (like Python)
- Single-line comments: `# ...`
- Multi-line comments: `### ... ###`

## Variables & Types
```glyph
let x = 5         # Immutable
var y: float = 2  # Mutable
const PI: float = 3.1415
```

## Functions
```glyph
func add(a: int, b: int):
    return a + b
```

## Control Flow
```glyph
if x > 0:
    print("Positive")
else:
    print("Non-positive")

for i in [1, 2, 3]:
    print(i)

match value:
    case 1:
        print("One")
    case _:
        print("Other")
```

## Modules & Imports
```glyph
import math
from collections import set
``` 
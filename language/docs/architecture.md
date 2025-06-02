# 🧾 Glyph Language & Engine – Product Overview & Technical Architecture

## Product Requirements & Vision

**Product Name:** Glyph  
**Target Users:** Indie game devs, MMO simulation creators, UI developers, visual coders, modders  
**Purpose:** To create a human-readable, ECS-native programming language and modular development engine that supports real-time games and web apps through both code and visual scripting.

---

## Product Overview

**Glyph** is a full-stack development platform consisting of:
- A new language (Python-like syntax, ECS-first, modern features)
- A hybrid runtime engine (interpreted for dev, compiled for performance)
- A modular editor (Glyph Studio) blending features from Unreal Engine 5 and Cursor
- Real-time multiplayer sync
- Dual editing paradigms (code ↔ node)

**Core Use Cases:**
- Tower defense games
- Roleplay/MMO simulations
- Web apps with real-time interactivity
- Game engines with in-game modding tools

---

## Technical Architecture (High-Level)

| Component        | Description |
|------------------|-------------|
| **Language Core**| ECS syntax: `entity`, `component`, `system`, `event`, etc. |
| **Compiler/Interpreter** | AST parser and runtime loop, compiled to LLVM via Rust |
| **Editor (Glyph Studio)** | Built with Tauri or Electron + React |
| **Visual Scripting (GlyphFlow)** | Custom node editor synced to AST. Node types include: logic, math, event, flow, variable, and custom script nodes. |
| **Networking** | WebSocket/ENet-based input sync. Supports both server-authoritative and peer-to-peer modes. |
| **Rendering** | Three.js or PixiJS for 2D/3D scenes |
| **Storage** | SQLite (local) and PostgreSQL (scale) |

---

## Feature Specifications

### Language Features
- Entity-Component-System (ECS) built-in
- Clean, Python-like syntax for defining entities and reactive systems
- Modern features: pattern matching, option/result types, async/await, ADTs/enums, traits/interfaces, destructuring, built-in testing, attribute/decorator syntax, slicing/ranges, macros/codegen, doc comments
- `emit`, `find`, `on`, and `tick` semantics
- Gradual/optional typing, custom structs, performance hints
- FFI/host interop for advanced users

### Visual Scripting (GlyphFlow)
- Node types: logic, math, event, flow, variable, custom script
- AST ↔ Node sync: All code changes are reflected in the node graph and vice versa (lossless round-trip for supported features)
- Node validation: highlights missing or invalid connections

### Editor Features
- Monaco-based script editor
- Node-based logic graph (AST ↔ Node sync)
- Scene drag/drop editor for prefabs
- Live preview and hot-reload runtime
- Accessibility: keyboard navigation, colorblind-friendly palettes
- Plugin/extension support (planned)

### Engine Features
- Tick-based execution system
- Built-in multiplayer sync module
- Modular structure for reusable systems

### Networking Features
- WebSocket/ENet-based input sync
- Server-authoritative and peer-to-peer support
- Basic anti-cheat and input validation (future phase)

---

## API Integrations
- **Monaco Editor**: text syntax and error highlighting
- **WebSocket/ENet**: networking API
- **Pygls (LSP)**: AST parsing, node conversion
- **SQLite/PostgreSQL**: data persistence

---

## User Interface Requirements
- Dockable panels (code, nodes, scene)
- Node editor canvas with zoom/pan
- File explorer and asset import
- Runtime controls (start, pause, reset)
- Real-time variable inspector
- Accessibility features (keyboard navigation, colorblind modes)
- Plugin/extension support (planned)

---

## Data Structures
- AST representation of scripts
- ECS Registry: entities → components
- Node graph format: JSON-backed
- Multiplayer input events structure
- Scene layout (entity/prefab placement)

---

## Authentication & Security
- Local dev: no auth required
- Online projects: GitHub/GitLab auth (future phase)
- Sandbox execution for safety in live preview
- File isolation per project

---

## Error Handling & Debugging
- Syntax error highlights (Monaco, inline and panel)
- Node validation (missing connections)
- Runtime log console
- Step-through tick debugger (Phase 2): breakpoints, variable watches, step/continue controls
- Error reporting: inline and in dedicated panel

---

## Development Environment
- Local runtime using Python 3.x
- Editor bundled via Tauri or Electron
- AST parsing in Python → compiler backend in Rust
- Dev script watching for live reloads

---

## Deployment Requirements
- Tauri/Electron app for desktop (Windows/macOS/Linux)
- Optional web-hosted editor (Phase 3, aims for feature parity with desktop)
- Cross-compiled CLI compiler for .glyph → binary
- Project export: Standalone games/apps for Windows, macOS, Linux (web export planned)

## Community & Documentation
- Comprehensive documentation and tutorials
- Sample projects (e.g., tower defense, MMO sim)
- Community sharing hub for assets/scripts (future phase)

## Technical Risks & Mitigations
- **Hybrid Runtime:** Maintaining both interpreted and compiled modes may increase complexity. Mitigation: shared AST and modular backend.
- **AST ↔ Node Sync:** Ensuring lossless round-trip between code and nodes is challenging. Mitigation: robust test suite and clear feature boundaries.
- **Networking:** Real-time sync and security are complex. Mitigation: phased rollout, with server-authoritative mode as default.

---

## ✅ MVP Deliverables (Phase 1)

| Deliverable | Status |
|-------------|--------|
| Glyph interpreter & ECS registry | ✅ |
| Game loop (tick system) | ✅ |
| Monaco integration | ✅ |
| Node editor (AST ↔ node sync) | ✅ |
| Scene editor prototype | ✅ |
| Tower defense sample project | ✅ |
| WebSocket multiplayer input sync | ✅ |

---

# Technical Architecture (Parser, Runtime, Tooling)

# Glyph Language – Parser and Runtime Architecture

## 1. Overview
This document outlines the architecture for the Glyph language parser, semantic analyzer, and runtime (interpreter and/or compiler). It is designed for extensibility, robust error handling, and integration with editor tooling.

---

## 2. Pipeline Stages

### 2.1 Lexing (Tokenization)
- Input: Source code (text)
- Output: Stream of tokens (keywords, identifiers, literals, operators, INDENT/DEDENT, etc.)
- Handles:
  - Single-line and multi-line comments
  - Indentation-based block structure (produces INDENT/DEDENT tokens)
  - Unicode identifiers

### 2.2 Parsing
- Input: Token stream
- Output: Abstract Syntax Tree (AST)
- Uses the EBNF grammar (see Glyph_Grammar.ebnf)
- Reports syntax errors with line/column info

### 2.3 AST Construction
- Builds a tree of nodes representing the program structure
- Each node type corresponds to a language construct (declaration, statement, expression, etc.)
- Preserves source locations for error reporting and tooling

### 2.4 Semantic Analysis
- Input: AST
- Output: Annotated AST (with types, scopes, etc.)
- Checks:
  - Type inference and checking
  - Scoping and variable lifetimes
  - Mutability/constness
  - Pattern matching exhaustiveness
  - Trait/interface conformance
  - Attribute/decorator effects
  - Macro expansion
- Reports semantic errors and warnings

### 2.5 Intermediate Representation (IR) / Bytecode (if compiling)
- Optionally lowers AST to IR or bytecode for efficient execution or compilation
- IR is designed for easy optimization and cross-platform codegen

### 2.6 Interpretation / Execution
- Walks the AST or executes bytecode/IR
- Manages runtime state: variables, call stack, ECS registry, event queue, async tasks
- Handles system scheduling, event dispatch, and ECS queries
- Integrates with the standard library and FFI

### 2.7 Error Handling
- All stages report errors with precise source locations and helpful messages
- Errors are categorized (syntax, semantic, runtime)
- Warnings and hints are provided for best practices

### 2.8 Tooling Extensibility
- AST and semantic info are exposed via APIs for:
  - Language Server Protocol (LSP) for editor integration (autocomplete, go-to-definition, etc.)
  - Formatter and linter
  - Static analysis and refactoring tools
  - Documentation generation

---

## 3. High-Level Diagram

```
Source Code
    ↓
[Lexer] → Tokens
    ↓
[Parser] → AST
    ↓
[Semantic Analyzer] → Annotated AST
    ↓
[IR/Bytecode Generator] (optional)
    ↓
[Interpreter/VM or Compiler]
    ↓
Program Execution
```

---

## 4. Pseudocode Example: Parsing and Execution

```python
# Pseudocode for main pipeline
source = read_source_file("main.glyph")
tokens = lex(source)
ast = parse(tokens)
annotated_ast = analyze_semantics(ast)
if compile_mode:
    bytecode = generate_bytecode(annotated_ast)
    run_vm(bytecode)
else:
    interpret(annotated_ast)
```

---

## 5. Extensibility and Modularity
- Each stage is modular and can be swapped or extended (e.g., new backends, custom analyzers)
- Tooling (LSP, formatter, etc.) can reuse the parser and semantic analyzer
- The runtime is designed for hot-reload and live coding support

---

## 6. Error Reporting Philosophy
- Errors should be clear, actionable, and point to the exact source location
- Suggestions and hints are provided where possible
- Warnings do not block execution but encourage best practices

---

## 7. Future Considerations
- Support for incremental parsing and analysis (for fast editor feedback)
- Pluggable optimization passes for the compiler
- Integration with debugging and profiling tools 
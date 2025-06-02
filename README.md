# Glyph Language & Engine

## Overview
Glyph is a modern, Python-inspired programming language and modular development engine purpose-built for game development, simulations, and real-time systems. It features a human-readable, ECS-native syntax, hybrid interpreted/compiled runtime, and seamless code ↔ visual scripting integration.

---

## Key Features
- **Entity-Component-System (ECS) built-in**
- **Clean, Python-like syntax**
- **Pattern matching, async/await, traits/interfaces, macros, and more**
- **Visual scripting (AST ↔ node sync)**
- **Hot-reload, live coding, and multiplayer support**
- **Extensible standard library and FFI**
- **Rich editor integration (Monaco, VSCode, LSP)**

---

## Quickstart
1. **Install Glyph (CLI & Editor):**
   ```sh
   pip install glyphlang  # or use the installer for your platform
   ```
2. **Create a new project:**
   ```sh
   glyph new mygame
   cd mygame
   ```
3. **Run your first program:**
   ```sh
   glyph run main.glyph
   ```
4. **Open in the editor:**
   - Use Glyph Studio or the VSCode extension for full IDE features.

---

## Directory Structure
```
/glyph
  /language
    /docs         # All documentation and language specs
    /examples     # Sample Glyph programs (see hello_world.glyph, ecs_basics.glyph)
    /src          # (Planned) Source code for parser, runtime, stdlib, etc.
    /tests        # (Planned) Automated test cases
    LICENSE       # Open source license
    CHANGELOG.md  # Release notes
README.md         # Project overview (this file)
```

---

## Documentation
All major documentation is in the `language/docs/` directory:

| Document                | Description                                      |
|------------------------ |--------------------------------------------------|
| [Getting Started](language/docs/getting_started.md)         | Install, setup, and Hello World                |
| [Language Tour](language/docs/language_tour.md)             | Syntax, variables, functions, control flow      |
| [Core Concepts](language/docs/core_concepts.md)             | ECS, events, async, traits, pattern matching    |
| [Standard Library](language/docs/stdlib_reference.md)       | Built-in types, functions, and modules          |
| [Tooling](language/docs/tooling.md)                         | Editor, CLI, formatter, debugger, visual scripting |
| [Tutorials](language/docs/tutorials.md)                     | Beginner to advanced guides, sample projects    |
| [FAQ](language/docs/faq.md)                                 | Common errors, debugging, best practices        |
| [Contribution Guide](language/docs/contribution_guide.md)   | How to contribute, code style, community        |
| [Roadmap](language/docs/roadmap.md)                         | Release plan, milestones, long-term vision      |
| [Architecture](language/docs/architecture.md)               | Language, parser, runtime, and system design    |
| [Grammar (EBNF)](language/docs/grammar.ebnf)                | Formal language grammar                         |
| [Semantics](language/docs/semantics.md)                     | Type system, scoping, runtime rules             |
| [LSP & Editor](language/docs/lsp_and_editor.md)             | Language server and editor integration          |
| [Test Suite & Build](language/docs/test_suite_and_build.md) | Testing, CI/CD, build system                    |
| [Sample Programs](language/docs/sample_programs.md)         | Example Glyph code for all major features       |

---

## Examples
See `language/examples/` for sample Glyph programs:
- `hello_world.glyph`: Hello World
- `ecs_basics.glyph`: Basic ECS usage

---

## Contributing
We welcome contributions! Please see the [Contribution Guide](language/docs/contribution_guide.md) for:
- How to report issues and request features
- Coding standards and pull request process
- Extending the language and standard library
- Writing tests and improving documentation

---

## Community & Support
- **Discussion Forum:** [link-to-forum]
- **Chat:** [link-to-chat]
- **GitHub Issues:** [link-to-issues]
- **Roadmap & Voting:** [link-to-roadmap]

---

## License
Glyph is open source, released under the [MIT License](language/LICENSE).

---

## About
Glyph is designed to empower developers to build games, simulations, and interactive apps with minimal boilerplate and maximum control. Join us as we build the next generation of creative tools! 
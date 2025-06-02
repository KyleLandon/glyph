# Getting Started with Glyph

## Installation
Install the Glyph CLI and editor:
```sh
pip install glyphlang  # or use the installer for your platform
```

## Hello World
Create a file `main.glyph`:
```glyph
print("Hello, Glyph World!")
```
Run it:
```sh
glyph run main.glyph
```

## Project Structure
```
/glyph
  /docs                # All documentation and language specs
  /examples            # Sample Glyph programs
  /src                 # Source code for parser, runtime, stdlib, etc.
  /tests               # Automated test cases
  README.md            # Project overview
  LICENSE              # Open source license
  CHANGELOG.md         # Release notes
``` 
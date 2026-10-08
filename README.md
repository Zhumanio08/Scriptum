# Scriptum

Java tool for executing code blocks in Markdown files.

## Overview

Scriptum reads `.md` files, finds Java code blocks marked with `class="run"`, 
compiles and executes them, displaying output in console.

## Current Status

| Feature | Status |
|---------|--------|
| Read Markdown files | ✓ |
| Parse Java blocks | ✓ |
| Detect `class` attribute | ⚠ In Progress |
| Compile code | ✓ |
| Execute code | ✓ |
| Multiple blocks support | ⚠ In Progress |
| Error handling | ⚠ In Progress |

## Usage

```bash
java Main document.md
```

## Roadmap

- v0.1: Basic execution (current)
- v0.2: Multiple blocks, class detection
- v0.3: Better error handling
- v0.4: Architecture refactor

## Project Structure

scriptum/
├── Main.java
├── test.md
└── README.md


## Tech Stack

☕ Java 11+

## Follow Progress

For updates and development details, visit [justzhuman.live](https://justzhuman.live)

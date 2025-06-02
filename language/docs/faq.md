# Glyph FAQ & Troubleshooting

## Common Errors
**Q: Why do I get an indentation error?**
A: Glyph uses significant indentation. Make sure to use consistent spaces or tabs (not both) and align blocks properly.

**Q: What does 'TypeError' mean?**
A: A value was used with the wrong type. Check your variable types and function signatures.

**Q: Why can't I mutate a 'let' variable?**
A: 'let' variables are immutable. Use 'var' for mutable variables.

## Debugging Tips
- Use the CLI or editor diagnostics to find syntax and type errors.
- Add print statements or use the debugger to inspect values.
- Run tests frequently to catch issues early.

## Best Practices
- Prefer 'let' for immutability unless mutation is needed.
- Use pattern matching for clear, concise control flow.
- Write tests for all new features and bug fixes.
- Keep systems small and focused for maintainability. 
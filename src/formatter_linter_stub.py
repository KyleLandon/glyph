import re

def format_code(source):
    print("Formatting code (stub)")
    # Auto-fix missing colons after keywords
    lines = source.split('\n')
    keywords = ['component', 'entity', 'system', 'trait', 'event', 'fn', 'on']
    for i, line in enumerate(lines):
        for kw in keywords:
            if line.strip().startswith(kw) and not line.strip().endswith(':'):
                lines[i] = line.rstrip() + ':'
    # Normalize indentation (4 spaces)
    formatted = '\n'.join(line.lstrip() for line in lines)
    return formatted

def lint_code(source):
    print("Linting code (stub)")
    warnings = []
    lines = source.split('\n')
    keywords = ['component', 'entity', 'system', 'trait', 'event', 'fn', 'on']
    for i, line in enumerate(lines):
        for kw in keywords:
            if line.strip().startswith(kw) and not line.strip().endswith(':'):
                warnings.append(f"Line {i+1}: Missing colon after '{kw}'")
        if '\t' in line:
            warnings.append(f"Line {i+1}: Tab character found; use spaces for indentation")
    # Check for unused variables (very basic)
    var_pattern = re.compile(r'\b([a-zA-Z_][a-zA-Z0-9_]*)\b')
    declared = set()
    used = set()
    for line in lines:
        if '=' in line:
            left = line.split('=')[0].strip()
            declared.add(left)
        for match in var_pattern.finditer(line):
            used.add(match.group(1))
    unused = declared - used
    for var in unused:
        warnings.append(f"Variable '{var}' declared but not used")
    for w in warnings:
        print(w)
    return warnings 
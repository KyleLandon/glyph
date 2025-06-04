# Expanded Glyph lexer with comparison ops and ECS/async/match keywords
def tokenize(source: str) -> list:
    tokens = []
    i = 0
    keywords = {'print', 'if', 'else', 'while', 'def', 'return', 'entity', 'component', 'system', 'async', 'await', 'match', 'case', 'trait', 'interface'}
    while i < len(source):
        c = source[i]
        # Skip comments
        if c == '#':
            while i < len(source) and source[i] != '\n':
                i += 1
        elif c.isspace():
            i += 1
        elif c.isalpha() or c == '_':
            start = i
            while i < len(source) and (source[i].isalnum() or source[i] == '_'):
                i += 1
            value = source[start:i]
            kind = 'KW' if value in keywords else 'ID'
            token = (kind, value)
            print('[LEXER] Token:', token)
            tokens.append(token)
        elif c.isdigit():
            start = i
            while i < len(source) and source[i].isdigit():
                i += 1
            token = ('NUMBER', source[start:i])
            print('[LEXER] Token:', token)
            tokens.append(token)
        elif c == '"':
            i += 1
            start = i
            while i < len(source) and source[i] != '"':
                i += 1
            value = source[start:i]
            i += 1  # skip closing quote
            token = ('STRING', value)
            print('[LEXER] Token:', token)
            tokens.append(token)
        elif c in '{}':
            token = ('BLOCK', c)
            print('[LEXER] Token:', token)
            tokens.append(token)
            i += 1
        elif c in '()[],:=+-*/<>!':
            # Handle multi-char ops
            if c in '<>!=' and i+1 < len(source) and source[i+1] == '=':
                token = ('PUNCT', c + '=')
                print('[LEXER] Token:', token)
                tokens.append(token)
                i += 2
            elif c == '=' and i+1 < len(source) and source[i+1] == '=':
                token = ('PUNCT', '==')
                print('[LEXER] Token:', token)
                tokens.append(token)
                i += 2
            else:
                token = ('PUNCT', c)
                print('[LEXER] Token:', token)
                tokens.append(token)
                i += 1
        else:
            # Skip unknown chars for now
            i += 1
    return tokens 
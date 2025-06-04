import re
from typing import List, Tuple, Optional

Token = Tuple[str, str, int, int, str]  # (kind, value, line, col, context)

class Lexer:
    def __init__(self, source: str):
        self.source = source
        self.tokens: List[Token] = []
        self.pos = 0

    def tokenize(self) -> List[Token]:
        token_specification = [
            ('NUMBER',   r'\d+(\.\d*)?'),
            ('STRING',   r'".*?"|\.*?\'),
            ('ID',       r'[A-Za-z_][A-Za-z0-9_]*'),
            ('NEWLINE',  r'\n'),
            ('SKIP',     r'[ \t]+'),
            ('COMMENT',  r'//.*'),
            ('OP',       r'[+\-*/=<>!&|]'),
            ('PUNCT',    r'[\(\)\[\]\{\},.:]'),
        ]
        tok_regex = '|'.join(f'(?P<{name}>{regex})' for name, regex in token_specification)
        get_token = re.compile(tok_regex).match
        line_num = 1
        line_start = 0
        pos = 0
        line = self.source
        while pos < len(self.source):
            mo = get_token(self.source, pos)
            if not mo:
                raise SyntaxError(f'Unexpected character {self.source[pos]!r} at line {line_num}')
            kind = mo.lastgroup
            value = mo.group()
            col = mo.start() - line_start + 1
            if kind == 'NEWLINE':
                line_num += 1
                line_start = mo.end()
            elif kind == 'SKIP' or kind == 'COMMENT':
                pass
            else:
                # Extract the full line for context
                line_end = self.source.find('\n', pos)
                if line_end == -1:
                    line_end = len(self.source)
                context = self.source[line_start:line_end]
                self.tokens.append((kind, value, line_num, col, context))
            pos = mo.end()
        return self.tokens 
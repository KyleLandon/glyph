# Expanded Glyph parser with ECS, async, match, traits, comparison ops, and block parsing
from lexer import tokenize
from ast import *

def parse(tokens: list) -> list:
    pos = 0
    ast = []
    def peek():
        return tokens[pos] if pos < len(tokens) else (None, None)
    def advance():
        nonlocal pos
        pos += 1
    def expect(kind, value=None):
        k, v = peek()
        if k != kind or (value is not None and v != value):
            raise Exception(f"Expected {kind} {value}, got {k} {v}")
        advance()
    def parse_primary():
        kind, value = peek()
        if kind == 'ID':
            advance()
            # Function call
            if pos < len(tokens) and tokens[pos][0] == 'PUNCT' and tokens[pos][1] == '(': 
                advance()  # skip '('
                args = []
                while pos < len(tokens) and tokens[pos][1] != ')':
                    args.append(parse_expression())
                    if pos < len(tokens) and tokens[pos][1] == ',':
                        advance()
                    elif pos < len(tokens) and tokens[pos][1] == ')':
                        break
                if pos < len(tokens) and tokens[pos][1] == ')':
                    advance()
                return FunctionCall(Identifier(value), args)
            return Identifier(value)
        elif kind == 'NUMBER':
            advance()
            return Number(int(value))
        elif kind == 'STRING':
            advance()
            return String(value)
        elif kind == 'PUNCT' and value == '(':  # Parenthesized
            advance()
            expr = parse_expression()
            expect('PUNCT', ')')
            return expr
        else:
            advance()
            return None
    def parse_factor():
        left = parse_primary()
        while True:
            kind, value = peek()
            if kind == 'PUNCT' and value in ('*', '/'):
                op = value
                advance()
                right = parse_primary()
                left = BinaryOp(left, op, right)
            else:
                break
        return left
    def parse_term():
        left = parse_factor()
        while True:
            kind, value = peek()
            if kind == 'PUNCT' and value in ('+', '-'):
                op = value
                advance()
                right = parse_factor()
                left = BinaryOp(left, op, right)
            else:
                break
        return left
    def parse_comparison():
        left = parse_term()
        while True:
            kind, value = peek()
            if kind == 'PUNCT' and value in ('==', '!=', '<', '>', '<=', '>='):
                op = value
                advance()
                right = parse_term()
                left = BinaryOp(left, op, right)
            else:
                break
        return left
    def parse_expression():
        return parse_comparison()
    def parse_block():
        stmts = []
        if peek()[0] == 'BLOCK' and peek()[1] == '{':
            advance()
            while pos < len(tokens) and not (peek()[0] == 'BLOCK' and peek()[1] == '}'):
                stmt = parse_statement()
                if stmt:
                    stmts.append(stmt)
            expect('BLOCK', '}')
        else:
            # Single statement as block
            stmt = parse_statement()
            if stmt:
                stmts.append(stmt)
        return Block(stmts)
    def parse_statement():
        kind, value = peek()
        # ECS stubs
        if kind == 'KW' and value == 'entity':
            advance()
            name = tokens[pos][1]
            advance()
            components = []
            block = parse_block()
            for stmt in block.statements:
                if isinstance(stmt, Identifier):
                    components.append(stmt)
            return EntityDef(name, components)
        elif kind == 'KW' and value == 'component':
            advance()
            name = tokens[pos][1]
            advance()
            fields = []
            block = parse_block()
            for stmt in block.statements:
                if isinstance(stmt, Identifier):
                    fields.append(stmt)
            return ComponentDef(name, fields)
        elif kind == 'KW' and value == 'system':
            advance()
            name = tokens[pos][1]
            advance()
            block = parse_block()
            return SystemDef(name, block)
        # Async/await stubs
        elif kind == 'KW' and value == 'async':
            advance()
            expect('KW', 'def')
            name = tokens[pos][1]
            advance()
            params = []
            if peek()[1] == '(': advance()
            while peek()[1] != ')':
                if peek()[0] == 'ID':
                    params.append(tokens[pos][1])
                    advance()
                if peek()[1] == ',': advance()
            if peek()[1] == ')': advance()
            body = parse_block()
            return AsyncDef(name, params, body)
        elif kind == 'KW' and value == 'await':
            advance()
            expr = parse_expression()
            return Await(expr)
        # Pattern matching
        elif kind == 'KW' and value == 'match':
            advance()
            expr = parse_expression()
            cases = []
            while pos < len(tokens) and peek()[0] == 'KW' and peek()[1] == 'case':
                advance()
                pattern = parse_expression()
                block = parse_block()
                cases.append(Case(pattern, block))
            return Match(expr, cases)
        # Traits/interfaces
        elif kind == 'KW' and value == 'trait':
            advance()
            name = tokens[pos][1]
            advance()
            methods = []
            block = parse_block()
            for stmt in block.statements:
                if isinstance(stmt, FunctionDef):
                    methods.append(stmt)
            return TraitDef(name, methods)
        elif kind == 'KW' and value == 'interface':
            advance()
            name = tokens[pos][1]
            advance()
            methods = []
            block = parse_block()
            for stmt in block.statements:
                if isinstance(stmt, FunctionDef):
                    methods.append(stmt)
            return InterfaceDef(name, methods)
        # Existing features
        elif kind == 'KW' and value == 'print':
            advance()
            expr = parse_expression()
            return Print(expr)
        elif kind == 'KW' and value == 'if':
            advance()
            cond = parse_expression()
            then_block = parse_block()
            else_block = None
            if pos < len(tokens) and tokens[pos][0] == 'KW' and tokens[pos][1] == 'else':
                advance()
                else_block = parse_block()
            return If(cond, then_block, else_block)
        elif kind == 'KW' and value == 'while':
            advance()
            cond = parse_expression()
            block = parse_block()
            return While(cond, block)
        elif kind == 'KW' and value == 'def':
            advance()
            name = tokens[pos][1]
            advance()
            params = []
            if peek()[1] == '(': advance()
            while peek()[1] != ')':
                if peek()[0] == 'ID':
                    params.append(tokens[pos][1])
                    advance()
                if peek()[1] == ',': advance()
            if peek()[1] == ')': advance()
            body = parse_block()
            return FunctionDef(name, params, body)
        elif kind == 'KW' and value == 'return':
            advance()
            expr = parse_expression()
            return Return(expr)
        elif kind == 'ID':
            # Assignment or expression
            target = value
            advance()
            kind2, value2 = peek()
            if kind2 == 'PUNCT' and value2 == '=':
                advance()
                expr = parse_expression()
                return Assignment(Identifier(target), expr)
            else:
                # Could be function call or variable
                pos2 = pos - 1
                expr = parse_primary()
                return expr
        else:
            advance()
            return None
    while pos < len(tokens):
        stmt = parse_statement()
        if stmt:
            ast.append(stmt)
    return ast 
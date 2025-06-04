from src.lexer import Lexer, Token
from src.ast import *
from typing import List
from src.utils import GlyphError, print_debug

# Add BinaryOp to AST if not present
try:
    BinaryOp
except NameError:
    from dataclasses import dataclass
    @dataclass
    class BinaryOp(Expression):
        left: Expression
        op: str
        right: Expression
    globals()['BinaryOp'] = BinaryOp

class Parser:
    def __init__(self, tokens: List[Token], source: str = None):
        self.tokens = tokens
        self.pos = 0
        self.source = source

    def parse(self):
        print('[Parser] Starting parse()')
        result = self.parse_module()
        print('[Parser] Finished parse()')
        return result

    def parse_module(self):
        print('[Parser] Entering parse_module')
        body = []
        loop_count = 0
        while not self._at_end():
            print(f'[Parser] parse_module loop, pos={self.pos}, token={self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
            start_pos = self.pos
            # If the next token is an identifier, parse a statement
            if self._match('ID'):
                stmt = self.parse_statement()
                print(f'[Parser] Got statement: {type(stmt).__name__ if stmt else None}')
                print(f'[Parser] After statement, pos={self.pos}, next token={self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
                if stmt:
                    body.append(stmt)
            else:
                # Skip over non-ID tokens (e.g., PUNCT, whitespace, etc.)
                while not self._at_end() and not self._match('ID'):
                    print(f'[Parser] Skipping non-ID token at pos={self.pos}: {self.tokens[self.pos]}')
                    self._advance()
                # If we reached the end, break
                if self._at_end():
                    break
                # Otherwise, continue to next loop iteration to parse the next statement
                continue
            loop_count += 1
            if self.pos == start_pos:
                print(f'[Parser] No progress at pos={self.pos}, forcibly advancing and skipping token: {self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
                self._advance()
                if self.pos == start_pos or self._at_end():
                    print('[Parser] Still no progress or at end after advance, breaking loop to avoid infinite loop')
                    break
            if self._at_end():
                break
        print('[Parser] Exiting parse_module')
        return Module(body=body)

    def parse_statement(self):
        print(f'[Parser] Entering parse_statement at pos={self.pos}')
        if self._match('ID'):
            value = self._peek_value()
            print(f'[Parser] parse_statement value={value}')
            if value == 'entity':
                return self.parse_entity()
            elif value == 'component':
                return self.parse_component()
            elif value == 'system':
                return self.parse_system()
            elif value == 'event':
                return self.parse_event()
            elif value == 'trait':
                return self.parse_trait()
            elif value == 'fn':
                return self.parse_function()
            elif value == 'if':
                return self.parse_if()
            elif value == 'while':
                return self.parse_while()
            elif value == 'for':
                return self.parse_for()
            elif value == 'match':
                return self.parse_match()
            elif value == 'return':
                return self.parse_return_statement()
            elif value == 'await':
                return self.parse_await()
            elif value == 'on':
                return self.parse_event_handler()
            elif value == 'dispatch' or value == 'emit':
                return self.parse_dispatch()
            elif value in {'macro', 'import', 'from', 'route', 'test', 'try', 'catch'}:
                return self.parse_stub_statement()
            else:
                # Could be assignment or expression
                # Look ahead for '=' to distinguish assignment
                if self._peek_next_kind() == 'OP' and self._peek_next_value() == '=':
                    return self.parse_assignment()
                else:
                    return self.parse_expression()
        self._advance()
        print(f'[Parser] Exiting parse_statement at pos={self.pos}')
        return None

    def parse_stub_statement(self):
        # Skip tokens until the next top-level statement or end of file
        print(f'[Parser] Skipping stub/unimplemented statement at pos={self.pos}, token={self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
        top_level_keywords = {'entity','component','system','event','trait','fn','if','while','for','match','return','await','on','dispatch','emit','macro','import','from','route','test','try','catch'}
        self._advance()  # Skip the keyword
        while not self._at_end():
            if self._match('ID') and self._peek_value() in top_level_keywords:
                break
            self._advance()
        return None

    def parse_entity(self):
        print(f'[Parser] Entering parse_entity at pos={self.pos}')
        self._consume('ID', 'entity')
        name = self._consume('ID', 'entity name')
        self._consume('PUNCT', ':')
        components = []
        top_level_keywords = {'entity','component','system','event','trait','fn','if','while','for','match','return','await','on'}
        while not self._at_end() and self._peek_kind() == 'ID':
            # If the next ID is a top-level keyword, stop parsing components
            if self._peek_value() in top_level_keywords:
                print(f'[Parser] parse_entity: encountered top-level keyword {self._peek_value()} at pos={self.pos}, breaking component parse loop')
                break
            print(f'[Parser] parse_entity component at pos={self.pos}')
            comp_name = self._consume('ID', 'component name')
            args = []
            if self._match('PUNCT') and self._peek_value() == '(':  # Parse args
                self._advance()  # skip (
                while not self._at_end() and self._peek_value() != ')':
                    print(f'[Parser] parse_entity arg at pos={self.pos}')
                    start_pos = self.pos
                    # Support named arguments: x=0
                    if self._match('ID') and self._peek_next_kind() == 'OP' and self._peek_next_value() == '=':
                        arg_name = self._consume('ID', 'arg name')
                        self._consume('OP', '=')
                        if self._match('NUMBER'):
                            arg_value = Number(float(self._consume('NUMBER', 'number')))
                        elif self._match('ID'):
                            arg_value = Identifier(self._consume('ID', 'arg value'))
                        else:
                            raise GlyphError(f'Expected value for named argument {arg_name}',
                                             line=self.tokens[self.pos][2] if self.pos < len(self.tokens) else None,
                                             col=self.tokens[self.pos][3] if self.pos < len(self.tokens) else None,
                                             context=self.tokens[self.pos][4] if self.pos < len(self.tokens) else None)
                        args.append(Assignment(target=arg_name, value=arg_value))
                    elif self._match('NUMBER'):
                        args.append(Number(float(self._consume('NUMBER', 'number'))))
                    elif self._match('ID'):
                        args.append(Identifier(self._consume('ID', 'arg')))
                    elif self._match('PUNCT') and self._peek_value() == ',':
                        self._advance()
                    else:
                        print(f'[Parser] Unexpected token in component args at pos={self.pos}: {self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
                        raise GlyphError(f'Unexpected token in component args: {self.tokens[self.pos] if self.pos < len(self.tokens) else None}',
                                         line=self.tokens[self.pos][2] if self.pos < len(self.tokens) else None,
                                         col=self.tokens[self.pos][3] if self.pos < len(self.tokens) else None,
                                         context=self.tokens[self.pos][4] if self.pos < len(self.tokens) else None)
                    if self.pos == start_pos:
                        print(f'[Parser] No progress in component args at pos={self.pos}, forcibly advancing')
                        self._advance()
                        if self._at_end():
                            break
                self._consume('PUNCT', ')')
            components.append(ComponentInstance(name=comp_name, args=args))
            # --- Robust advancement and debug output ---
            print(f'[Parser] Finished component instance: {comp_name} at pos={self.pos}, next token: {self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
            # If the next token is a newline or whitespace, skip it
            while not self._at_end() and self._peek_kind() not in {'ID', 'PUNCT'}:
                print(f'[Parser] Skipping non-ID/PUNCT token at pos={self.pos}: {self.tokens[self.pos]}')
                self._advance()
        if not self._at_end() and self._peek_kind() != 'ID':
            print(f'[Parser] parse_entity forcibly advancing at pos={self.pos}, token={self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
            self._advance()
        print(f'[Parser] Exiting parse_entity at pos={self.pos}')
        return Entity(name=name, components=components)

    def parse_component(self):
        print(f'[Parser] Entering parse_component at pos={self.pos}')
        self._consume('ID', 'component')
        name = self._consume('ID', 'component name')
        self._consume('PUNCT', ':')
        fields = []
        top_level_keywords = {'component','entity','system','event','trait','fn','if','while','for','match','return','await','on'}
        while not self._at_end() and self._peek_kind() == 'ID':
            if self._peek_value() in top_level_keywords:
                break
            print(f'[Parser] parse_component field at pos={self.pos}')
            fields.append(self.parse_field())
        print(f'[Parser] Exiting parse_component at pos={self.pos}')
        return Component(name=name, fields=fields)

    def parse_system(self):
        print(f'[Parser] Entering parse_system at pos={self.pos}')
        self._consume('ID', 'system')
        name = self._consume('ID', 'system name')
        self._consume('PUNCT', ':')
        block = self.parse_block()
        print(f'[Parser] Exiting parse_system at pos={self.pos}')
        return System(name=name, block=block)

    def parse_event(self):
        print(f'[Parser] Entering parse_event at pos={self.pos}')
        self._consume('ID', 'event')
        name = self._consume('ID', 'event name')
        self._consume('PUNCT', ':')
        fields = []
        top_level_keywords = {'component','entity','system','event','trait','fn','if','while','for','match','return','await','on'}
        while not self._at_end() and self._peek_kind() == 'ID':
            if self._peek_value() in top_level_keywords:
                break
            print(f'[Parser] parse_event field at pos={self.pos}')
            fields.append(self.parse_field())
        print(f'[Parser] Exiting parse_event at pos={self.pos}')
        return Event(name=name, fields=fields)

    def parse_trait(self):
        print(f'[Parser] Entering parse_trait at pos={self.pos}')
        self._consume('ID', 'trait')
        name = self._consume('ID', 'trait name')
        self._consume('PUNCT', ':')
        methods = []
        while not self._at_end() and self._peek_kind() == 'ID' and self._peek_value() == 'fn':
            print(f'[Parser] parse_trait method at pos={self.pos}')
            methods.append(self.parse_method())
        print(f'[Parser] Exiting parse_trait at pos={self.pos}')
        return Trait(name=name, methods=methods)

    def parse_method(self):
        self._consume('ID', 'fn')
        name = self._consume('ID', 'method name')
        params = self.parse_params()
        block = self.parse_block()
        return Function(name=name, params=params, block=block)

    def parse_function(self):
        print(f'[Parser] Entering parse_function at pos={self.pos}')
        self._consume('ID', 'fn')
        name = self._consume('ID', 'function name')
        params = self.parse_params_typed()
        return_type = None
        # Robustly handle optional whitespace/comments between '-' and '>'
        # Accept: fn foo(a: float) -> float: ...
        # Accept: fn foo(a: float): ...
        if self._match('OP') and self._peek_value() == '-' and self._peek_next_kind() == 'OP' and self._peek_next_value() == '>':
            self._advance()  # skip '-'
            self._advance()  # skip '>'
            return_type = self._consume('ID', 'return type')
        # Require colon after parameter list/return type
        if not (self._match('PUNCT') and self._peek_value() == ':'):
            raise GlyphError(f"Expected ':' after function signature", line=self.tokens[self.pos][2] if self.pos < len(self.tokens) else None,
                             col=self.tokens[self.pos][3] if self.pos < len(self.tokens) else None,
                             context=self.tokens[self.pos][4] if self.pos < len(self.tokens) else None)
        self._advance()  # skip ':'
        block = self.parse_block()
        print(f'[Parser] Exiting parse_function at pos={self.pos}')
        return FunctionDef(name=name, params=params, return_type=return_type, block=block)

    def parse_params_typed(self):
        params = []
        if self._match('PUNCT') and self._peek_value() == '(':  # Parse params
            self._advance()  # skip (
            while not self._at_end() and self._peek_value() != ')':
                pname = self._consume('ID', 'param name')
                ptype = None
                if self._match('PUNCT') and self._peek_value() == ':':
                    self._advance()
                    ptype = self._consume('ID', 'param type')
                params.append(Param(name=pname, type=ptype))
                if self._match('PUNCT') and self._peek_value() == ',':
                    self._advance()
            self._consume('PUNCT', ')')
        return params

    def parse_field(self):
        field_name = self._consume('ID', 'field name')
        if not (self._match('PUNCT') and self._peek_value() == ':'):
            line = self.tokens[self.pos][2] if self.pos < len(self.tokens) else None
            col = self.tokens[self.pos][3] if self.pos < len(self.tokens) else None
            context = self.tokens[self.pos][4] if self.pos < len(self.tokens) else None
            raise GlyphError(f"Expected ':' after field name '{field_name}' in component/event definition", line, col, context)
        self._advance()  # skip ':'
        field_type = self._consume('ID', 'field type')
        return Field(name=field_name, type=field_type)

    def parse_block(self):
        print(f'[Parser] Entering parse_block at pos={self.pos}')
        statements = []
        max_block_len = 100
        for _ in range(max_block_len):
            if self._at_end():
                break
            if self._match('ID') and self._peek_value() in {'entity','component','system','event','trait','fn'}:
                break
            if self._match('ID') and self._peek_value() in {'case', 'else', 'on'}:
                break
            # If the next token is an identifier, parse a statement
            if self._match('ID'):
                # Only break if this is not the first statement in the block and it's a statement starter
                statement_starters = {'entity','component','system','event','trait','fn','if','while','for','match','return','await','on'}
                if self._peek_value() in statement_starters and len(statements) > 0:
                    break
                stmt = self.parse_statement()
                print(f'[Parser] parse_block got statement: {type(stmt).__name__ if stmt else None} at pos={self.pos}')
                print(f'[Parser] After block statement, pos={self.pos}, next token={self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
                if stmt:
                    statements.append(stmt)
            else:
                break
        print(f'[Parser] Exiting parse_block at pos={self.pos}')
        return Block(statements=statements)

    def parse_assignment(self):
        print(f'[Parser] Entering parse_assignment at pos={self.pos}')
        target = self._consume('ID', 'assignment target')
        self._consume('OP', '=')
        value = self.parse_expression()
        print(f'[Parser] Exiting parse_assignment at pos={self.pos}')
        return Assignment(target=target, value=value)

    def parse_expression(self, min_prec=0):
        print(f'[Parser] Entering parse_expression at pos={self.pos}')
        left = self.parse_primary()
        while True:
            op, prec, assoc = self._peek_binop()
            if op is None or prec < min_prec:
                break
            self._advance()  # consume op
            right = self.parse_expression(prec + (0 if assoc == 'right' else 1))
            left = BinaryOp(left=left, op=op, right=right)
        print(f'[Parser] Exiting parse_expression at pos={self.pos}')
        return left

    def parse_primary(self):
        print(f'[Parser] Entering parse_primary at pos={self.pos}')
        expr = None
        if self._match('ID'):
            ident = self._consume('ID', 'identifier')
            if self._match('PUNCT') and self._peek_value() == '(':  # Call
                self._advance()
                args = []
                while not self._at_end() and self._peek_value() != ')':
                    print(f'[Parser] parse_primary arg at pos={self.pos}')
                    args.append(self.parse_expression())
                    if self._match('PUNCT') and self._peek_value() == ',':
                        self._advance()
                self._consume('PUNCT', ')')
                expr = Call(func=Identifier(ident), args=args)
            else:
                expr = Identifier(ident)
        elif self._match('NUMBER'):
            expr = Number(float(self._consume('NUMBER', 'number')))
        elif self._match('STRING'):
            expr = String(self._consume('STRING', 'string'))
        elif self._match('PUNCT') and self._peek_value() == '(':  # Parenthesized expression
            self._advance()
            expr = self.parse_expression()
            self._consume('PUNCT', ')')
        else:
            print(f'[Parser] parse_primary unexpected token at pos={self.pos}: {self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
            raise SyntaxError(f'Unexpected token {self.tokens[self.pos]} at position {self.pos}')
        while self._match('PUNCT') and self._peek_value() == '.':
            self._advance()
            member = self._consume('ID', 'member name')
            expr = MemberAccess(obj=expr, member=member)
        print(f'[Parser] Exiting parse_primary at pos={self.pos}')
        return expr

    def _peek_binop(self):
        # Returns (op, precedence, associativity) or (None, -1, None)
        if not self._match('OP'):
            return (None, -1, None)
        op = self._peek_value()
        # Define precedence and associativity
        table = {
            '==': (2, 'left'), '!=': (2, 'left'), '<': (2, 'left'), '>': (2, 'left'), '<=': (2, 'left'), '>=': (2, 'left'),
            '+': (3, 'left'), '-': (3, 'left'),
            '*': (4, 'left'), '/': (4, 'left'),
        }
        if op in table:
            return (op, *table[op])
        return (None, -1, None)

    def _at_end(self):
        return self.pos >= len(self.tokens)

    def _advance(self):
        self.pos += 1

    def _match(self, kind):
        if self._at_end():
            return False
        return self.tokens[self.pos][0] == kind

    def _peek_kind(self):
        if self._at_end():
            return None
        return self.tokens[self.pos][0]

    def _peek_value(self):
        if self._at_end():
            return None
        return self.tokens[self.pos][1]

    def _peek_next_kind(self):
        if self.pos + 1 >= len(self.tokens):
            return None
        return self.tokens[self.pos + 1][0]

    def _peek_next_value(self):
        if self.pos + 1 >= len(self.tokens):
            return None
        return self.tokens[self.pos + 1][1]

    def _consume(self, kind, desc):
        if self._at_end() or self.tokens[self.pos][0] != kind:
            line = col = None
            context = None
            if self.pos < len(self.tokens):
                _, value, line, col, context = self.tokens[self.pos]
            raise GlyphError(f'Expected {desc} ({kind}) at position {self.pos}', line, col, context)
        value = self.tokens[self.pos][1]
        self.pos += 1
        return value

    def parse_if(self):
        self._consume('ID', 'if')
        cond = self.parse_expression()
        if not (self._match('PUNCT') and self._peek_value() == ':'):
            raise GlyphError(f"Expected ':' after if condition", line=self.tokens[self.pos][2] if self.pos < len(self.tokens) else None,
                             col=self.tokens[self.pos][3] if self.pos < len(self.tokens) else None,
                             context=self.tokens[self.pos][4] if self.pos < len(self.tokens) else None)
        self._advance()  # skip ':'
        then_block = self.parse_block()
        print(f'[Parser] After if-block, pos={self.pos}, next token={self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
        else_block = None
        if self._match('ID') and self._peek_value() == 'else':
            self._advance()
            if not (self._match('PUNCT') and self._peek_value() == ':'):
                raise GlyphError(f"Expected ':' after else", line=self.tokens[self.pos][2] if self.pos < len(self.tokens) else None,
                                 col=self.tokens[self.pos][3] if self.pos < len(self.tokens) else None,
                                 context=self.tokens[self.pos][4] if self.pos < len(self.tokens) else None)
            self._advance()  # skip ':'
            else_block = self.parse_block()
            print(f'[Parser] After else-block, pos={self.pos}, next token={self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
        return If(cond=cond, then_block=then_block, else_block=else_block)

    def parse_while(self):
        self._consume('ID', 'while')
        cond = self.parse_expression()
        if not (self._match('PUNCT') and self._peek_value() == ':'):
            raise GlyphError(f"Expected ':' after while condition", line=self.tokens[self.pos][2] if self.pos < len(self.tokens) else None,
                             col=self.tokens[self.pos][3] if self.pos < len(self.tokens) else None,
                             context=self.tokens[self.pos][4] if self.pos < len(self.tokens) else None)
        self._advance()  # skip ':'
        block = self.parse_block()
        print(f'[Parser] After while-block, pos={self.pos}, next token={self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
        return While(cond=cond, block=block)

    def parse_for(self):
        self._consume('ID', 'for')
        var = self._consume('ID', 'loop variable')
        self._consume('ID', 'in')
        query = self.parse_query()
        if not (self._match('PUNCT') and self._peek_value() == ':'):
            raise GlyphError(f"Expected ':' after for header", line=self.tokens[self.pos][2] if self.pos < len(self.tokens) else None,
                             col=self.tokens[self.pos][3] if self.pos < len(self.tokens) else None,
                             context=self.tokens[self.pos][4] if self.pos < len(self.tokens) else None)
        self._advance()  # skip ':'
        block = self.parse_block()
        print(f'[Parser] After for-block, pos={self.pos}, next token={self.tokens[self.pos] if self.pos < len(self.tokens) else None}')
        return For(var=var, query=query, block=block)

    def parse_query(self):
        # entities.with(Position, Velocity)
        if self._match('ID') and self._peek_value() == 'entities':
            self._advance()
            self._consume('PUNCT', '.')
            self._consume('ID', 'with')
            self._consume('PUNCT', '(')
            components = []
            while not self._at_end() and self._peek_value() != ')':
                components.append(self._consume('ID', 'component name'))
                if self._match('PUNCT') and self._peek_value() == ',':
                    self._advance()
            self._consume('PUNCT', ')')
            return Query(components=components)
        raise SyntaxError('Expected entities.with(...) query')

    def parse_match(self):
        print(f'[Parser] Entering parse_match at pos={self.pos}')
        self._consume('ID', 'match')
        expr = self.parse_expression()
        # Require colon after match expression
        if not (self._match('PUNCT') and self._peek_value() == ':'):
            raise GlyphError(f"Expected ':' after match expression", line=self.tokens[self.pos][2] if self.pos < len(self.tokens) else None,
                             col=self.tokens[self.pos][3] if self.pos < len(self.tokens) else None,
                             context=self.tokens[self.pos][4] if self.pos < len(self.tokens) else None)
        self._advance()  # skip ':'
        cases = []
        while not self._at_end():
            if self._match('ID') and self._peek_value() == 'case':
                cases.append(self.parse_case())
            else:
                break
        print(f'[Parser] Exiting parse_match at pos={self.pos}')
        return Match(expr=expr, cases=cases)

    def parse_case(self):
        self._consume('ID', 'case')
        pattern = self.parse_expression()  # For now, just use expression as pattern
        # Require colon after pattern
        if not (self._match('PUNCT') and self._peek_value() == ':'):
            raise GlyphError(f"Expected ':' after case pattern", line=self.tokens[self.pos][2] if self.pos < len(self.tokens) else None,
                             col=self.tokens[self.pos][3] if self.pos < len(self.tokens) else None,
                             context=self.tokens[self.pos][4] if self.pos < len(self.tokens) else None)
        self._advance()  # skip ':'
        block = self.parse_block()
        return Case(pattern=pattern, block=block)

    def parse_return_statement(self):
        self._consume('ID', 'return')
        if self._at_end() or self._peek_kind() in {'ID', 'PUNCT'} and self._peek_value() in {'entity','component','system','event','trait','fn','if','while','for','match','case','on'}:
            return ReturnStatement(value=None)
        value = self.parse_expression()
        return ReturnStatement(value=value)

    def parse_await(self):
        self._consume('ID', 'await')
        expr = self.parse_expression()
        return Await(expr=expr)

    def parse_event_handler(self):
        self._consume('ID', 'on')
        event = self.parse_event()
        block = self.parse_block()
        return EventHandler(event=event, block=block)

    def parse_params(self):
        params = []
        if self._match('PUNCT') and self._peek_value() == '(':  # Parse params
            self._advance()  # skip (
            while not self._at_end() and self._peek_value() != ')':
                pname = self._consume('ID', 'param name')
                ptype = None
                if self._match('PUNCT') and self._peek_value() == ':':
                    self._advance()
                    ptype = self._consume('ID', 'param type')
                params.append(Param(name=pname, type=ptype))
                if self._match('PUNCT') and self._peek_value() == ',':
                    self._advance()
            self._consume('PUNCT', ')')
        return params

    def parse_dispatch(self):
        # Handles 'dispatch Foo(bar=1)' and 'emit Bar(x=2)'
        keyword = self._consume('ID', 'dispatch/emit')
        if not self._match('ID'):
            raise GlyphError(f"Expected event name after '{keyword}'", line=self.tokens[self.pos][2] if self.pos < len(self.tokens) else None,
                             col=self.tokens[self.pos][3] if self.pos < len(self.tokens) else None,
                             context=self.tokens[self.pos][4] if self.pos < len(self.tokens) else None)
        event_name = self._consume('ID', 'event name')
        args = []
        if self._match('PUNCT') and self._peek_value() == '(':  # Parse args
            self._advance()  # skip (
            while not self._at_end() and self._peek_value() != ')':
                start_pos = self.pos
                # Support named arguments: x=0
                if self._match('ID') and self._peek_next_kind() == 'OP' and self._peek_next_value() == '=':
                    arg_name = self._consume('ID', 'arg name')
                    self._consume('OP', '=')
                    if self._match('NUMBER'):
                        arg_value = Number(float(self._consume('NUMBER', 'number')))
                    elif self._match('ID'):
                        arg_value = Identifier(self._consume('ID', 'arg value'))
                    elif self._match('STRING'):
                        arg_value = String(self._consume('STRING', 'string'))
                    else:
                        raise GlyphError(f'Expected value for named argument {arg_name}',
                                         line=self.tokens[self.pos][2] if self.pos < len(self.tokens) else None,
                                         col=self.tokens[self.pos][3] if self.pos < len(self.tokens) else None,
                                         context=self.tokens[self.pos][4] if self.pos < len(self.tokens) else None)
                    args.append(Assignment(target=arg_name, value=arg_value))
                elif self._match('NUMBER'):
                    args.append(Number(float(self._consume('NUMBER', 'number'))))
                elif self._match('ID'):
                    args.append(Identifier(self._consume('ID', 'arg')))
                elif self._match('STRING'):
                    args.append(String(self._consume('STRING', 'string')))
                elif self._match('PUNCT') and self._peek_value() == ',':
                    self._advance()
                else:
                    raise GlyphError(f'Unexpected token in dispatch/emit args: {self.tokens[self.pos] if self.pos < len(self.tokens) else None}',
                                     line=self.tokens[self.pos][2] if self.pos < len(self.tokens) else None,
                                     col=self.tokens[self.pos][3] if self.pos < len(self.tokens) else None,
                                     context=self.tokens[self.pos][4] if self.pos < len(self.tokens) else None)
                if self.pos == start_pos:
                    self._advance()
                    if self._at_end():
                        break
            self._consume('PUNCT', ')')
        return Call(func=Identifier(keyword), args=[Identifier(event_name)] + args) 
from src.ast import Module, FunctionDef, Param, Assignment, Identifier, Number, String, BinaryOp, Call, FunctionCall, ReturnStatement, Block, Event, Trait
from src.utils import GlyphError

class SemanticAnalyzer:
    def __init__(self):
        self.errors = []
        self.entities = set()
        self.components = set()
        self.symbols = {}  # name -> type or kind
        self.functions = {}  # name -> (param_types, return_type)
        self.scopes = [{}]  # stack of variable scopes

    def check(self, module: Module):
        for stmt in module.body:
            try:
                self.check_statement(stmt)
            except GlyphError as e:
                self.errors.append(str(e))

    def check_statement(self, stmt):
        if hasattr(stmt, 'name') and stmt.name in self.symbols:
            raise GlyphError(f"Duplicate symbol: {stmt.name}", context=f"{stmt.name}", code='E001', error_type='SemanticError')
        if stmt.__class__.__name__ == 'Entity':
            self.entities.add(stmt.name)
            self.symbols[stmt.name] = 'entity'
        elif stmt.__class__.__name__ == 'Component':
            self.components.add(stmt.name)
            self.symbols[stmt.name] = 'component'
        elif stmt.__class__.__name__ == 'System':
            self.symbols[stmt.name] = 'system'
            self.check_block(stmt.block)
        elif isinstance(stmt, FunctionDef):
            param_types = [p.type for p in stmt.params]
            self.functions[stmt.name] = (param_types, stmt.return_type)
            self.symbols[stmt.name] = 'function'
            self.scopes.append({p.name: p.type for p in stmt.params})
            self.check_block(stmt.block, expected_return=stmt.return_type)
            self.scopes.pop()
        elif isinstance(stmt, Assignment):
            value_type = self.infer_expr_type(stmt.value)
            self.set_var_type(stmt.target, value_type)
        elif isinstance(stmt, ReturnStatement):
            pass
        elif isinstance(stmt, Block):
            self.check_block(stmt)
        elif isinstance(stmt, Event):
            if stmt.name in self.symbols:
                raise GlyphError(f"Duplicate event: {stmt.name}", context=f"event {stmt.name}", code='E002', error_type='SemanticError')
            self.symbols[stmt.name] = 'event'
        elif isinstance(stmt, Trait):
            if stmt.name in self.symbols:
                raise GlyphError(f"Duplicate trait: {stmt.name}", context=f"trait {stmt.name}", code='E003', error_type='SemanticError')
            self.symbols[stmt.name] = 'trait'
        # TODO: Add more semantic checks for trait implementation, event fields, etc.

    def check_block(self, block, expected_return=None):
        for stmt in block.statements:
            if isinstance(stmt, ReturnStatement):
                if stmt.value is not None:
                    value_type = self.infer_expr_type(stmt.value)
                    if expected_return and value_type != expected_return:
                        raise GlyphError(f"Return type mismatch: expected {expected_return}, got {value_type}", code='E004', error_type='TypeError')
            else:
                self.check_statement(stmt)

    def set_var_type(self, name, typ):
        self.scopes[-1][name] = typ

    def get_var_type(self, name):
        for scope in reversed(self.scopes):
            if name in scope:
                return scope[name]
        return None

    def infer_expr_type(self, expr):
        if isinstance(expr, Number):
            return 'float'
        elif isinstance(expr, String):
            return 'str'
        elif isinstance(expr, Identifier):
            typ = self.get_var_type(expr.name)
            if typ is None:
                raise GlyphError(f"Undeclared variable: {expr.name}", code='E005', error_type='NameError')
            return typ
        elif isinstance(expr, BinaryOp):
            left_type = self.infer_expr_type(expr.left)
            right_type = self.infer_expr_type(expr.right)
            if left_type != right_type:
                raise GlyphError(f"Type mismatch in binary op: {left_type} vs {right_type}", code='E006', error_type='TypeError')
            return left_type
        elif isinstance(expr, (Call, FunctionCall)):
            func_name = expr.func.name if isinstance(expr.func, Identifier) else None
            if func_name and func_name in self.functions:
                param_types, return_type = self.functions[func_name]
                if len(param_types) != len(expr.args):
                    raise GlyphError(f"Function '{func_name}' expects {len(param_types)} args, got {len(expr.args)}", code='E007', error_type='TypeError')
                for i, (arg, expected_type) in enumerate(zip(expr.args, param_types)):
                    arg_type = self.infer_expr_type(arg)
                    if expected_type and arg_type != expected_type:
                        raise GlyphError(f"Type mismatch in argument {i+1} of '{func_name}': expected {expected_type}, got {arg_type}", code='E008', error_type='TypeError')
                return return_type
            if func_name == 'print':
                return None
            raise GlyphError(f"Unknown function: {func_name}", code='E009', error_type='NameError')
        else:
            return None 
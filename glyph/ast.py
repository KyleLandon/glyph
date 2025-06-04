# Expanded AST node definitions
class Identifier:
    def __init__(self, name):
        self.name = name

class Number:
    def __init__(self, value):
        self.value = value

class String:
    def __init__(self, value):
        self.value = value

class Assignment:
    def __init__(self, target, value):
        self.target = target
        self.value = value

class BinaryOp:
    def __init__(self, left, op, right):
        self.left = left
        self.op = op
        self.right = right

class Print:
    def __init__(self, expr):
        self.expr = expr

class If:
    def __init__(self, cond, then_block, else_block=None):
        self.cond = cond
        self.then_block = then_block
        self.else_block = else_block

class While:
    def __init__(self, cond, block):
        self.cond = cond
        self.block = block

class Block:
    def __init__(self, statements):
        self.statements = statements

class FunctionDef:
    def __init__(self, name, params, body):
        self.name = name
        self.params = params
        self.body = body

class FunctionCall:
    def __init__(self, func, args):
        self.func = func
        self.args = args

class Return:
    def __init__(self, value):
        self.value = value

# ECS stubs
class EntityDef:
    def __init__(self, name, components):
        self.name = name
        self.components = components
class ComponentDef:
    def __init__(self, name, fields):
        self.name = name
        self.fields = fields
class SystemDef:
    def __init__(self, name, block):
        self.name = name
        self.block = block

# Async/await stubs
class AsyncDef:
    def __init__(self, name, params, body):
        self.name = name
        self.params = params
        self.body = body
class Await:
    def __init__(self, expr):
        self.expr = expr

# Pattern matching stubs
class Match:
    def __init__(self, expr, cases):
        self.expr = expr
        self.cases = cases
class Case:
    def __init__(self, pattern, block):
        self.pattern = pattern
        self.block = block

# Traits/interfaces stubs
class TraitDef:
    def __init__(self, name, methods):
        self.name = name
        self.methods = methods
class InterfaceDef:
    def __init__(self, name, methods):
        self.name = name
        self.methods = methods

class Expression:
    pass 
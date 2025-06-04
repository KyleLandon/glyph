from dataclasses import dataclass
from typing import List, Optional, Any

@dataclass
class Module:
    body: List[Any]

@dataclass
class Statement:
    pass

@dataclass
class Block(Statement):
    statements: List[Statement]

@dataclass
class Entity(Statement):
    name: str
    components: List['ComponentInstance']

@dataclass
class Component(Statement):
    name: str
    fields: List['Field']

@dataclass
class ComponentInstance:
    name: str
    args: List['Expression']

@dataclass
class Field:
    name: str
    type: str

@dataclass
class System(Statement):
    name: str
    block: Block

@dataclass
class Event(Statement):
    name: str
    fields: List[Field]

@dataclass
class Trait(Statement):
    name: str
    methods: List['Function']

@dataclass
class Function(Statement):
    name: str
    params: List[str]
    block: Block

@dataclass
class Assignment(Statement):
    target: str
    value: 'Expression'

@dataclass
class Expression:
    pass

@dataclass
class Identifier(Expression):
    name: str

@dataclass
class Number(Expression):
    value: float

@dataclass
class String(Expression):
    value: str

@dataclass
class Call(Expression):
    func: Identifier
    args: List[Expression]

@dataclass
class BinaryOp(Expression):
    left: Expression
    op: str
    right: Expression

@dataclass
class MemberAccess(Expression):
    obj: Expression
    member: str

@dataclass
class If(Statement):
    cond: Expression
    then_block: Block
    else_block: Any  # Optional[Block]

@dataclass
class While(Statement):
    cond: Expression
    block: Block

@dataclass
class For(Statement):
    var: str
    query: 'Query'
    block: Block

@dataclass
class Query(Expression):
    components: list

@dataclass
class Match(Statement):
    expr: Expression
    cases: list  # List[Case]

@dataclass
class Case:
    pattern: Any
    block: Block

@dataclass
class Return(Statement):
    value: Expression

@dataclass
class Await(Expression):
    expr: Expression

@dataclass
class EventHandler(Statement):
    event: str
    block: Block

@dataclass
class Param:
    name: str
    type: Optional[str] = None

@dataclass
class FunctionDef(Statement):
    name: str
    params: list  # List[Param]
    return_type: Optional[str]
    block: Block

@dataclass
class FunctionCall(Expression):
    func: Expression
    args: list

@dataclass
class ReturnStatement(Statement):
    value: Optional[Expression] 
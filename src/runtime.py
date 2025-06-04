from src.ast import Module, Component, Entity, System, Assignment, Call, Number, String, BinaryOp, MemberAccess, If, While, For, Match, Return, Await, EventHandler, Query, FunctionDef, FunctionCall, ReturnStatement, Block, Identifier
from src.utils import GlyphError, print_debug
import types

class EntityManager:
    def __init__(self):
        self.entities = {}
        self.next_id = 1

    def create_entity(self, name, components):
        eid = self.next_id
        self.next_id += 1
        # Store components as a dict for fast lookup
        comp_dict = {comp.name: comp for comp in components}
        entity = {'name': name, 'components': comp_dict, 'data': {}}
        self.entities[eid] = entity
        # Call lifecycle hook
        if hasattr(self, 'on_create'):
            self.on_create(entity)
        return eid

    def get_component(self, entity, comp_name):
        # Return the component instance for an entity by name
        return entity['components'].get(comp_name, None)

    def destroy_entity(self, eid):
        entity = self.entities.pop(eid, None)
        if entity and hasattr(self, 'on_destroy'):
            self.on_destroy(entity)

class ComponentManager:
    def __init__(self):
        self.components = {}

    def register_component(self, name, fields):
        self.components[name] = fields

class SystemManager:
    def __init__(self):
        self.systems = []

    def register_system(self, name, block):
        self.systems.append({'name': name, 'block': block})

class ReturnException(Exception):
    def __init__(self, value):
        self.value = value

class Scheduler:
    def __init__(self):
        self.tasks = []

    def create_task(self, coro):
        self.tasks.append(coro)

    def run(self):
        while self.tasks:
            task = self.tasks.pop(0)
            try:
                next(task)
                self.tasks.append(task)
            except StopIteration:
                continue

class Runtime:
    def __init__(self):
        self.entity_manager = EntityManager()
        self.component_manager = ComponentManager()
        self.system_manager = SystemManager()
        self.builtins = {'print': print}
        self.event_handlers = {}  # event_name -> list of handler blocks
        self.events = {}  # event_name -> event definition (fields)
        self.traits = {}  # trait_name -> trait definition (methods)
        self.trait_impls = {}  # type_name -> set of trait_names
        self.functions = {}  # name -> FunctionDef
        self.call_stack = []
        self.scheduler = Scheduler()
        # Attach lifecycle hooks
        self.entity_manager.on_create = self.on_entity_create
        self.entity_manager.on_destroy = self.on_entity_destroy

    def on_entity_create(self, entity):
        print_debug(f'Entity created: {entity["name"]}')
        # Call component on_create hooks if present
        for comp in entity['components'].values():
            if hasattr(comp, 'on_create'):
                comp.on_create()

    def on_entity_destroy(self, entity):
        print_debug(f'Entity destroyed: {entity["name"]}')
        # Call component on_destroy hooks if present
        for comp in entity['components'].values():
            if hasattr(comp, 'on_destroy'):
                comp.on_destroy()

    def run(self, module: 'Module'):
        # Register components, entities, systems, event handlers, functions, events, and traits
        for stmt in module.body:
            if isinstance(stmt, Component):
                self.component_manager.register_component(stmt.name, stmt.fields)
            elif isinstance(stmt, Entity):
                self.entity_manager.create_entity(stmt.name, stmt.components)
            elif isinstance(stmt, System):
                self.system_manager.register_system(stmt.name, stmt.block)
            elif isinstance(stmt, EventHandler):
                self.register_event_handler(stmt)
            elif isinstance(stmt, FunctionDef):
                self.functions[stmt.name] = stmt
            elif stmt.__class__.__name__ == 'Event':
                self.events[stmt.name] = stmt.fields
            elif stmt.__class__.__name__ == 'Trait':
                self.register_trait(stmt)
        # Execute systems (sync and async)
        self.execute_systems()
        self.scheduler.run()
        print('Entities:', self.entity_manager.entities)
        print('Components:', self.component_manager.components)
        print('Systems:', [s['name'] for s in self.system_manager.systems])
        print('Functions:', list(self.functions.keys()))
        print('Events:', list(self.events.keys()))
        print('Traits:', list(self.traits.keys()))
        print('Event Handlers:', {k: len(v) for k, v in self.event_handlers.items()})

    def register_event_handler(self, handler: EventHandler):
        if handler.event not in self.event_handlers:
            self.event_handlers[handler.event] = []
        self.event_handlers[handler.event].append(handler.block)
        print_debug(f'Registered handler for event: {handler.event}')

    def dispatch_event(self, event_name, event_data):
        print(f'Dispatching event: {event_name} with data: {event_data}')
        handlers = self.event_handlers.get(event_name, [])
        for block in handlers:
            print(f'  Executing handler for event: {event_name}')
            self.eval_block(block, event_data)

    def execute_systems(self):
        for system in self.system_manager.systems:
            print(f'Running system: {system["name"]}')
            for eid, entity in self.entity_manager.entities.items():
                print(f'  Entity {eid}: {entity["name"]}')
                if getattr(system, 'async_', False):
                    self.scheduler.create_task(self.eval_block_async(system['block'], entity))
                else:
                    self.eval_block(system['block'], entity)

    def eval_block(self, block: 'Block', entity, local_vars=None):
        if local_vars is None:
            local_vars = {}
        for stmt in block.statements:
            self.eval_statement(stmt, entity, local_vars)

    def eval_block_async(self, block: 'Block', entity, local_vars=None):
        if local_vars is None:
            local_vars = {}
        for stmt in block.statements:
            yield from self.eval_statement_async(stmt, entity, local_vars)

    def eval_statement(self, stmt, entity, local_vars=None):
        if local_vars is None:
            local_vars = {}
        try:
            if isinstance(stmt, Assignment):
                # Only support simple assignments to component fields for now
                if isinstance(stmt.value, (Number, String)) and isinstance(stmt.target, str):
                    # Assume target is of the form 'Component.field'
                    if '.' in stmt.target:
                        comp_name, field = stmt.target.split('.', 1)
                        comp = self.entity_manager.get_component(entity, comp_name)
                        if comp:
                            setattr(comp, field, stmt.value.value)
                            print_debug(f'Set {comp_name}.{field} = {stmt.value.value}')
                        else:
                            raise GlyphError(f'Component {comp_name} not found on entity {entity["name"]}', context=f'{comp_name}.{field}')
                # TODO: Support more assignment targets and value types
            elif isinstance(stmt, Call) or isinstance(stmt, FunctionCall):
                self.eval_expression(stmt, entity, local_vars)
            elif isinstance(stmt, If):
                cond = self.eval_expression(stmt.cond, entity, local_vars)
                if cond:
                    self.eval_block(stmt.then_block, entity, local_vars)
                elif stmt.else_block:
                    self.eval_block(stmt.else_block, entity, local_vars)
            elif isinstance(stmt, While):
                while self.eval_expression(stmt.cond, entity, local_vars):
                    self.eval_block(stmt.block, entity, local_vars)
            elif isinstance(stmt, For):
                # System query: for e in entities.with(...)
                if isinstance(stmt.query, Query):
                    for eid, ent in self.entity_manager.entities.items():
                        if all(self.entity_manager.get_component(ent, cname) for cname in stmt.query.components):
                            local_entity = ent.copy()
                            local_entity[stmt.var] = ent  # allow access by loop var
                            self.eval_block(stmt.block, local_entity, local_vars)
            elif isinstance(stmt, Match):
                val = self.eval_expression(stmt.expr, entity, local_vars)
                for case in stmt.cases:
                    # For now, simple equality match
                    if self.eval_expression(case.pattern, entity, local_vars) == val:
                        self.eval_block(case.block, entity, local_vars)
                        break
            elif isinstance(stmt, ReturnStatement):
                value = self.eval_expression(stmt.value, entity, local_vars) if stmt.value else None
                raise ReturnException(value)
            elif isinstance(stmt, Await):
                print('    Await encountered (sync context, no-op)')
            elif isinstance(stmt, EventHandler):
                # Handled via event dispatch
                pass
            else:
                print(f'    Executing statement: {stmt}')
        except GlyphError as e:
            print(e)

    def eval_statement_async(self, stmt, entity, local_vars=None):
        if local_vars is None:
            local_vars = {}
        try:
            if isinstance(stmt, Assignment):
                # Only support simple assignments to component fields for now
                if isinstance(stmt.value, (Number, String)) and isinstance(stmt.target, str):
                    # Assume target is of the form 'Component.field'
                    if '.' in stmt.target:
                        comp_name, field = stmt.target.split('.', 1)
                        comp = self.entity_manager.get_component(entity, comp_name)
                        if comp:
                            setattr(comp, field, stmt.value.value)
                            print_debug(f'Set {comp_name}.{field} = {stmt.value.value}')
                        else:
                            raise GlyphError(f'Component {comp_name} not found on entity {entity["name"]}', context=f'{comp_name}.{field}')
                # TODO: Support more assignment targets and value types
            elif isinstance(stmt, Call) or isinstance(stmt, FunctionCall):
                yield from self.eval_expression_async(stmt, entity, local_vars)
            elif isinstance(stmt, If):
                cond = yield from self.eval_expression_async(stmt.cond, entity, local_vars)
                if cond:
                    yield from self.eval_block_async(stmt.then_block, entity, local_vars)
                elif stmt.else_block:
                    yield from self.eval_block_async(stmt.else_block, entity, local_vars)
            elif isinstance(stmt, While):
                while (yield from self.eval_expression_async(stmt.cond, entity, local_vars)):
                    yield from self.eval_block_async(stmt.block, entity, local_vars)
            elif isinstance(stmt, For):
                # System query: for e in entities.with(...)
                if isinstance(stmt.query, Query):
                    for eid, ent in self.entity_manager.entities.items():
                        if all(self.entity_manager.get_component(ent, cname) for cname in stmt.query.components):
                            local_entity = ent.copy()
                            local_entity[stmt.var] = ent  # allow access by loop var
                            yield from self.eval_block_async(stmt.block, local_entity, local_vars)
            elif isinstance(stmt, Match):
                val = yield from self.eval_expression_async(stmt.expr, entity, local_vars)
                for case in stmt.cases:
                    # For now, simple equality match
                    if (yield from self.eval_expression_async(case.pattern, entity, local_vars)) == val:
                        yield from self.eval_block_async(case.block, entity, local_vars)
                        break
            elif isinstance(stmt, ReturnStatement):
                value = yield from self.eval_expression_async(stmt.value, entity, local_vars) if stmt.value else None
                raise ReturnException(value)
            elif isinstance(stmt, Await):
                print('    Await encountered (async context, yielding)')
                yield  # Simulate async pause
            elif isinstance(stmt, EventHandler):
                # Handled via event dispatch
                pass
            else:
                print(f'    Executing statement: {stmt}')
        except GlyphError as e:
            print(e)

    def eval_expression(self, expr, entity, local_vars=None):
        if local_vars is None:
            local_vars = {}
        try:
            if isinstance(expr, Number):
                return expr.value
            elif isinstance(expr, String):
                return expr.value
            elif isinstance(expr, Identifier):
                # Check local vars first
                if expr.name in local_vars:
                    return local_vars[expr.name]
                # Try to resolve as a component field
                for comp in entity['components']:
                    if hasattr(comp, expr.name):
                        return getattr(comp, expr.name)
                raise GlyphError(f'Field {expr.name} not found on entity {entity["name"]}', context=expr.name)
            elif isinstance(expr, Call) or isinstance(expr, FunctionCall):
                if isinstance(expr.func, Identifier) and expr.func.name in self.functions:
                    func_def = self.functions[expr.func.name]
                    args = [self.eval_expression(arg, entity, local_vars) for arg in expr.args]
                    new_locals = {param.name: arg for param, arg in zip(func_def.params, args)}
                    self.call_stack.append((func_def.name, func_def))
                    try:
                        self.eval_block(func_def.block, entity, new_locals)
                    except ReturnException as ret:
                        self.call_stack.pop()
                        return ret.value
                    self.call_stack.pop()
                    return None
                elif isinstance(expr.func, Identifier) and expr.func.name in self.builtins:
                    args = [self.eval_expression(arg, entity, local_vars) for arg in expr.args]
                    return self.builtins[expr.func.name](*args)
                else:
                    raise GlyphError(f'Function {getattr(expr.func, "name", str(expr.func))} not found', context=str(expr.func))
            elif isinstance(expr, BinaryOp):
                left = self.eval_expression(expr.left, entity, local_vars)
                right = self.eval_expression(expr.right, entity, local_vars)
                try:
                    if expr.op == '+':
                        return left + right
                    elif expr.op == '-':
                        return left - right
                    elif expr.op == '*':
                        return left * right
                    elif expr.op == '/':
                        return left / right
                    elif expr.op == '==':
                        return left == right
                    elif expr.op == '!=':
                        return left != right
                    elif expr.op == '<':
                        return left < right
                    elif expr.op == '>':
                        return left > right
                    elif expr.op == '<=':
                        return left <= right
                    elif expr.op == '>=':
                        return left >= right
                except Exception as e:
                    print(f'    Error in binary operation: {e}')
                    return None
            elif isinstance(expr, MemberAccess):
                obj = self.eval_expression(expr.obj, entity, local_vars)
                if hasattr(obj, expr.member):
                    return getattr(obj, expr.member)
                elif isinstance(obj, dict) and expr.member in obj:
                    return obj[expr.member]
                print(f'    Error: Member {expr.member} not found')
                return None
            elif isinstance(expr, Query):
                # Return a list of entities matching the query
                return [ent for ent in self.entity_manager.entities.values()
                        if all(self.entity_manager.get_component(ent, cname) for cname in expr.components)]
            elif isinstance(expr, Await):
                print('    Await not implemented')
                return None
            else:
                print(f'    Error: Unsupported expression {expr}')
                return None
        except GlyphError as e:
            print(e)
            return None

    def eval_expression_async(self, expr, entity, local_vars=None):
        if local_vars is None:
            local_vars = {}
        try:
            if isinstance(expr, Number):
                return expr.value
            elif isinstance(expr, String):
                return expr.value
            elif isinstance(expr, Identifier):
                if expr.name in local_vars:
                    return local_vars[expr.name]
                for comp in entity['components']:
                    if hasattr(comp, expr.name):
                        return getattr(comp, expr.name)
                raise GlyphError(f'Field {expr.name} not found on entity {entity["name"]}', context=expr.name)
            elif isinstance(expr, Call) or isinstance(expr, FunctionCall):
                if isinstance(expr.func, Identifier) and expr.func.name in self.functions:
                    func_def = self.functions[expr.func.name]
                    args = []
                    for arg in expr.args:
                        arg_val = yield from self.eval_expression_async(arg, entity, local_vars)
                        args.append(arg_val)
                    new_locals = {param.name: arg for param, arg in zip(func_def.params, args)}
                    self.call_stack.append((func_def.name, func_def))
                    try:
                        yield from self.eval_block_async(func_def.block, entity, new_locals)
                    except ReturnException as ret:
                        self.call_stack.pop()
                        return ret.value
                    self.call_stack.pop()
                    return None
                elif isinstance(expr.func, Identifier) and expr.func.name in self.builtins:
                    args = []
                    for arg in expr.args:
                        arg_val = yield from self.eval_expression_async(arg, entity, local_vars)
                        args.append(arg_val)
                    return self.builtins[expr.func.name](*args)
                else:
                    raise GlyphError(f'Function {getattr(expr.func, "name", str(expr.func))} not found', context=str(expr.func))
            elif isinstance(expr, BinaryOp):
                left = yield from self.eval_expression_async(expr.left, entity, local_vars)
                right = yield from self.eval_expression_async(expr.right, entity, local_vars)
                try:
                    if expr.op == '+':
                        return left + right
                    elif expr.op == '-':
                        return left - right
                    elif expr.op == '*':
                        return left * right
                    elif expr.op == '/':
                        return left / right
                    elif expr.op == '==':
                        return left == right
                    elif expr.op == '!=':
                        return left != right
                    elif expr.op == '<':
                        return left < right
                    elif expr.op == '>':
                        return left > right
                    elif expr.op == '<=':
                        return left <= right
                    elif expr.op == '>=':
                        return left >= right
                except Exception as e:
                    print(f'    Error in binary operation: {e}')
                    return None
            elif isinstance(expr, MemberAccess):
                obj = yield from self.eval_expression_async(expr.obj, entity, local_vars)
                if hasattr(obj, expr.member):
                    return getattr(obj, expr.member)
                elif isinstance(obj, dict) and expr.member in obj:
                    return obj[expr.member]
                print(f'    Error: Member {expr.member} not found')
                return None
            elif isinstance(expr, Query):
                return [ent for ent in self.entity_manager.entities.values()
                        if all(self.entity_manager.get_component(ent, cname) for cname in expr.components)]
            elif isinstance(expr, Await):
                print('    Await encountered (async expr, yielding)')
                yield
                return None
            else:
                print(f'    Error: Unsupported expression {expr}')
                return None
        except GlyphError as e:
            print(e)
            return None

    def register_trait(self, trait):
        self.traits[trait.name] = trait.methods
        print_debug(f'Registered trait: {trait.name} with methods: {[m.name for m in trait.methods]}')

    def implements_trait(self, type_name, trait_name):
        # For now, just check if type_name is registered as implementing trait_name
        return trait_name in self.trait_impls.get(type_name, set())

    def register_trait_impl(self, type_name, trait_name):
        if type_name not in self.trait_impls:
            self.trait_impls[type_name] = set()
        self.trait_impls[type_name].add(trait_name)
        print_debug(f'{type_name} implements trait {trait_name}')

    def dispatch_trait_method(self, type_name, trait_name, method_name, *args, **kwargs):
        if not self.implements_trait(type_name, trait_name):
            raise GlyphError(f"Type {type_name} does not implement trait {trait_name}")
        methods = self.traits.get(trait_name, [])
        for m in methods:
            if m.name == method_name:
                # For now, just call the function if it's registered
                if m.name in self.functions:
                    return self.functions[m.name](*args, **kwargs)
                else:
                    raise GlyphError(f"Trait method {method_name} not implemented for {type_name}")
        raise GlyphError(f"Trait {trait_name} has no method {method_name}") 
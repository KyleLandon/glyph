# Expanded Glyph interpreter
from ast import *

class ReturnException(Exception):
    def __init__(self, value):
        self.value = value

# --- ECS Registry ---
class ECSRegistry:
    def __init__(self):
        self.components = {}  # name -> field list
        self.entities = []    # list of {name, components: {name: data}}
        self.systems = []     # list of (name, block)
    def register_component(self, name, fields):
        self.components[name] = fields
    def create_entity(self, name, component_names):
        comps = {}
        for cname in component_names:
            if cname.name in self.components:
                # Default fields to 0
                comps[cname.name] = {f.name: 0 for f in self.components[cname.name]}
            else:
                comps[cname.name] = {}
        entity = {'name': name, 'components': comps}
        self.entities.append(entity)
        return entity
    def register_system(self, name, block):
        self.systems.append((name, block))
    def get_entities_with(self, *component_names):
        result = []
        for e in self.entities:
            if all(c in e['components'] for c in component_names):
                result.append(e)
        return result

def print_error(msg, node=None, env=None):
    print(f"[ERROR] {msg}")
    if node:
        print(f"  Node: {type(node).__name__} -> {getattr(node, 'name', repr(node))}")
    if env:
        print(f"  Env: {env}")

def eval_expr(expr, env, functions):
    if isinstance(expr, Number):
        return expr.value
    elif isinstance(expr, String):
        return expr.value
    elif isinstance(expr, Identifier):
        return env.get(expr.name, None)
    elif isinstance(expr, BinaryOp):
        left = eval_expr(expr.left, env, functions)
        right = eval_expr(expr.right, env, functions)
        if expr.op == '+':
            return left + right
        elif expr.op == '-':
            return left - right
        elif expr.op == '*':
            return left * right
        elif expr.op == '/':
            return left / right
    elif isinstance(expr, FunctionCall):
        func = functions.get(expr.func.name)
        if func:
            args = [eval_expr(arg, env, functions) for arg in expr.args]
            new_env = dict(zip(func.params, args))
            try:
                eval_block(func.body, new_env, functions)
            except ReturnException as e:
                return e.value
        else:
            raise Exception(f"Unknown function: {expr.func.name}")
    return None

def eval_block(block, env, functions):
    for stmt in block.statements:
        eval_stmt(stmt, env, functions)

def eval_stmt(stmt, env, functions):
    if isinstance(stmt, Assignment):
        if isinstance(stmt.target, Identifier):
            value = eval_expr(stmt.value, env, functions)
            env[stmt.target.name] = value
    elif isinstance(stmt, Print):
        value = eval_expr(stmt.expr, env, functions)
        print(value)
    elif isinstance(stmt, If):
        cond = eval_expr(stmt.cond, env, functions)
        if cond:
            eval_block(stmt.then_block, env, functions)
        elif stmt.else_block:
            eval_block(stmt.else_block, env, functions)
    elif isinstance(stmt, While):
        while eval_expr(stmt.cond, env, functions):
            eval_block(stmt.block, env, functions)
    elif isinstance(stmt, Block):
        eval_block(stmt, env, functions)
    elif isinstance(stmt, FunctionDef):
        functions[stmt.name] = stmt
    elif isinstance(stmt, Return):
        value = eval_expr(stmt.value, env, functions)
        raise ReturnException(value)
    elif isinstance(stmt, FunctionCall):
        eval_expr(stmt, env, functions)
    elif isinstance(stmt, Identifier):
        value = env.get(stmt.name, None)
        print(f"{stmt.name} = {value}")
    elif isinstance(stmt, Number):
        print(stmt.value)
    elif isinstance(stmt, String):
        print(stmt.value)
    elif isinstance(stmt, BinaryOp):
        print(eval_expr(stmt, env, functions))

def run(ast):
    env = {}
    functions = {}
    for node in ast:
        eval_stmt(node, env, functions)
    return env 

def interpret(ast):
    env = {}
    ecs = ECSRegistry()
    async_funcs = {}  # name -> (params, body)
    trait_registry = {}  # name -> list of method names
    interface_registry = {}  # name -> list of method names
    # First pass: register components, entities, systems, async functions, traits, interfaces
    for node in ast:
        try:
            if isinstance(node, ComponentDef):
                ecs.register_component(node.name, node.fields)
                print(f"[ECS] Registered component: {node.name} fields={node.fields}")
            elif isinstance(node, EntityDef):
                ecs.create_entity(node.name, node.components)
                print(f"[ECS] Created entity: {node.name} with components={node.components}")
            elif isinstance(node, SystemDef):
                ecs.register_system(node.name, node.block)
                print(f"[ECS] Registered system: {node.name}")
            elif isinstance(node, AsyncDef):
                async_funcs[node.name] = (node.params, node.body)
                print(f"[ASYNC] Registered async function: {node.name}")
            elif isinstance(node, TraitDef):
                method_names = [m.name for m in node.methods]
                trait_registry[node.name] = method_names
                print(f"[TRAIT] Registered trait: {node.name} methods={method_names}")
            elif isinstance(node, InterfaceDef):
                method_names = [m.name for m in node.methods]
                interface_registry[node.name] = method_names
                print(f"[INTERFACE] Registered interface: {node.name} methods={method_names}")
        except Exception as e:
            print_error(f"Exception during registration: {e}", node)
    # Second pass: interpret all other nodes (functions, etc.)
    def eval_node(node):
        try:
            if isinstance(node, Number):
                return node.value
            elif isinstance(node, String):
                return node.value
            elif isinstance(node, Identifier):
                if node.name in env:
                    return env.get(node.name, None)
                else:
                    print_error(f"Unknown variable: {node.name}", node, env)
                    return None
            elif isinstance(node, Assignment):
                val = eval_node(node.value)
                env[node.target.name] = val
                return val
            elif isinstance(node, BinaryOp):
                left = eval_node(node.left)
                right = eval_node(node.right)
                try:
                    if node.op == '+': return left + right
                    if node.op == '-': return left - right
                    if node.op == '*': return left * right
                    if node.op == '/': return left / right
                    if node.op == '==': return left == right
                    if node.op == '!=': return left != right
                    if node.op == '<': return left < right
                    if node.op == '>': return left > right
                    if node.op == '<=': return left <= right
                    if node.op == '>=': return left >= right
                except Exception as e:
                    print_error(f"Binary operation error: {e}", node, env)
                    return None
            elif isinstance(node, Print):
                val = eval_node(node.expr)
                print(val)
            elif isinstance(node, If):
                cond = eval_node(node.cond)
                if cond:
                    eval_node(node.then_block)
                elif node.else_block:
                    eval_node(node.else_block)
            elif isinstance(node, While):
                while eval_node(node.cond):
                    eval_node(node.block)
            elif isinstance(node, Block):
                for stmt in node.statements:
                    eval_node(stmt)
            elif isinstance(node, FunctionDef):
                env[node.name] = node
                # Check if this function matches any trait/interface method
                for trait, methods in trait_registry.items():
                    if node.name in methods:
                        print(f"[TRAIT] Function '{node.name}' matches trait '{trait}' method '{node.name}'")
                for iface, methods in interface_registry.items():
                    if node.name in methods:
                        print(f"[INTERFACE] Function '{node.name}' matches interface '{iface}' method '{node.name}'")
            elif isinstance(node, FunctionCall):
                # Check for async function call
                if node.func.name in async_funcs:
                    params, body = async_funcs[node.func.name]
                    args = [eval_node(arg) for arg in node.args]
                    def async_coroutine():
                        local_env = env.copy()
                        for pname, arg in zip(params, args):
                            local_env[pname] = arg
                        print(f"[ASYNC] Entering async function: {node.func.name}")
                        eval_node(body)
                        print(f"[ASYNC] Exiting async function: {node.func.name}")
                    return async_coroutine
                # Regular function
                func = env.get(node.func.name)
                if isinstance(func, FunctionDef):
                    local_env = env.copy()
                    for pname, arg in zip(func.params, node.args):
                        local_env[pname] = eval_node(arg)
                    # Save/restore env for simple scoping
                    old_env = env.copy()
                    env.update(local_env)
                    eval_node(func.body)
                    env.clear()
                    env.update(old_env)
                else:
                    print_error(f"Unknown function: {node.func.name}", node, env)
            elif isinstance(node, Return):
                return eval_node(node.value)
            # Async/await real logic
            elif isinstance(node, AsyncDef):
                # Already registered
                pass
            elif isinstance(node, Await):
                coro = eval_node(node.expr)
                if callable(coro):
                    print(f"[ASYNC] Awaiting coroutine...")
                    try:
                        coro()  # Run the coroutine to completion
                    except Exception as e:
                        print_error(f"Exception in coroutine: {e}", node, env)
                    print(f"[ASYNC] Done awaiting.")
                else:
                    print_error(f"Awaited non-coroutine: {coro}", node, env)
            # Pattern matching real logic
            elif isinstance(node, Match):
                match_val = eval_node(node.expr)
                matched = False
                for case in node.cases:
                    # Only support literal (Number/String) or Identifier patterns for now
                    if isinstance(case.pattern, Number) or isinstance(case.pattern, String):
                        pat_val = eval_node(case.pattern)
                        if match_val == pat_val:
                            print(f"[MATCH] Matched case: {pat_val}")
                            eval_node(case.block)
                            matched = True
                            break
                    elif isinstance(case.pattern, Identifier):
                        # Bind variable (not implemented, just match any for now)
                        print(f"[MATCH] Matched identifier case: {case.pattern.name}")
                        eval_node(case.block)
                        matched = True
                        break
                if not matched:
                    print(f"[MATCH] No case matched for value: {match_val}")
            # Traits/interfaces registration already handled
            elif isinstance(node, TraitDef):
                pass
            elif isinstance(node, InterfaceDef):
                pass
            else:
                print_error(f"Unknown node type: {type(node).__name__}", node, env)
        except Exception as e:
            print_error(f"Exception during interpretation: {e}", node, env)
    # Run all non-ECS nodes
    for node in ast:
        if not isinstance(node, (ComponentDef, EntityDef, SystemDef, AsyncDef, TraitDef, InterfaceDef)):
            eval_node(node)
    # Run all systems
    for sys_name, block in ecs.systems:
        print(f"[ECS] Running system: {sys_name}")
        for entity in ecs.entities:
            print(f"[ECS] Entity: {entity['name']} components={entity['components']}")
        eval_node(block) 
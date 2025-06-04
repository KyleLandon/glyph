import unittest
from src.runtime import Runtime
from src.ast import *
from src.semantics import SemanticAnalyzer
from src.utils import GlyphError
from src.formatter_linter_stub import format_code, lint_code

class TestLanguageFeatures(unittest.TestCase):
    def setUp(self):
        print(f'\n[LangFeatureTest] Running: {self._testMethodName}')

    def test_complex_function_types(self):
        # Function with multiple params and return type
        fn = FunctionDef(name='add', params=[Param('a', 'float'), Param('b', 'float')], return_type='float',
                         block=Block(statements=[ReturnStatement(value=BinaryOp(left=Identifier('a'), op='+', right=Identifier('b')))]))
        module = Module(body=[fn])
        analyzer = SemanticAnalyzer()
        analyzer.check(module)
        self.assertFalse(analyzer.errors)

    def test_trait_implementation(self):
        # Simulate a trait and a function implementing it
        trait = Trait(name='Mover', methods=[Function(name='move', params=['dx', 'dy'], block=Block(statements=[]))])
        fn = FunctionDef(name='move', params=[Param('dx', 'float'), Param('dy', 'float')], return_type=None, block=Block(statements=[]))
        module = Module(body=[trait, fn])
        analyzer = SemanticAnalyzer()
        analyzer.check(module)
        self.assertFalse(analyzer.errors)

    def test_event_handler_chain(self):
        # Multiple handlers for the same event
        event = Event(name='OnFire', fields=[Field('msg', 'str')])
        handler1 = EventHandler(event='OnFire', block=Block(statements=[Assignment(target='x', value=Number(1))]))
        handler2 = EventHandler(event='OnFire', block=Block(statements=[Assignment(target='y', value=Number(2))]))
        module = Module(body=[event, handler1, handler2])
        analyzer = SemanticAnalyzer()
        analyzer.check(module)
        self.assertFalse(analyzer.errors)

    def test_ecs_lifecycle_and_query(self):
        # Create entities/components, run a system that queries and mutates
        runtime = Runtime()
        comp = Component(name='Health', fields=[Field('hp', 'float')])
        ent = Entity(name='Player', components=[ComponentInstance(name='Health', args=[Number(100)])])
        sys = System(name='Damage', block=Block(statements=[Assignment(target='Health.hp', value=Number(50))]))
        module = Module(body=[comp, ent, sys])
        runtime.run(module)
        # Check that entity and component exist and were mutated
        player = next(iter(runtime.entity_manager.entities.values()))
        health = runtime.entity_manager.get_component(player, 'Health')
        self.assertTrue(hasattr(health, 'hp'))
        self.assertEqual(getattr(health, 'hp'), 50)

    def test_async_await_chain(self):
        # Simulate async/await chain
        fn1 = FunctionDef(name='foo', params=[], return_type=None, block=Block(statements=[ReturnStatement(value=Number(1))]))
        fn2 = FunctionDef(name='bar', params=[], return_type=None, block=Block(statements=[Await(expr=Call(func=Identifier('foo'), args=[]))]))
        module = Module(body=[fn1, fn2])
        analyzer = SemanticAnalyzer()
        analyzer.check(module)
        self.assertFalse(analyzer.errors)

    def test_stdlib_math_string(self):
        # Use stdlib functions in expressions
        from src.stdlib import StdLib
        stdlib = StdLib()
        stdlib.register_builtins()
        self.assertEqual(stdlib.builtins['sqrt'](16), 4)
        self.assertEqual(stdlib.builtins['upper']('abc'), 'ABC')

    def test_formatter_and_linter_complex(self):
        code = 'component Foo\n\tx: float\nentity Bar\n    y: float'  # tabs, missing colon
        formatted = format_code(code)
        self.assertIn('component Foo:', formatted)
        warnings = lint_code(code)
        self.assertTrue(any('Missing colon' in w for w in warnings))
        self.assertTrue(any('Tab character' in w for w in warnings))

    def test_integration_program(self):
        # Simulate a full program with ECS, events, traits, async
        comp = Component(name='Pos', fields=[Field('x', 'float')])
        ent = Entity(name='E', components=[ComponentInstance(name='Pos', args=[Number(0)])])
        trait = Trait(name='Mover', methods=[Function(name='move', params=['dx'], block=Block(statements=[]))])
        fn = FunctionDef(name='move', params=[Param('dx', 'float')], return_type=None, block=Block(statements=[Assignment(target='Pos.x', value=Number(5))]))
        event = Event(name='Tick', fields=[])
        handler = EventHandler(event='Tick', block=Block(statements=[Call(func=Identifier('move'), args=[Number(1)])]))
        sys = System(name='Main', block=Block(statements=[Call(func=Identifier('move'), args=[Number(1)]), Await(expr=Call(func=Identifier('move'), args=[Number(2)]))]))
        module = Module(body=[comp, ent, trait, fn, event, handler, sys])
        analyzer = SemanticAnalyzer()
        analyzer.check(module)
        self.assertFalse(analyzer.errors)
        runtime = Runtime()
        runtime.run(module)
        # Check that entity/component was mutated
        e = next(iter(runtime.entity_manager.entities.values()))
        pos = runtime.entity_manager.get_component(e, 'Pos')
        self.assertEqual(getattr(pos, 'x'), 5)

    def test_negative_type_error(self):
        # Should raise a type error
        fn = FunctionDef(name='bad', params=[Param('x', 'float')], return_type='float', block=Block(statements=[ReturnStatement(value=String('oops'))]))
        module = Module(body=[fn])
        analyzer = SemanticAnalyzer()
        analyzer.check(module)
        self.assertTrue(any('TypeError' in err for err in analyzer.errors))

    def test_negative_trait_duplicate(self):
        # Should raise a duplicate trait error
        trait1 = Trait(name='T', methods=[])
        trait2 = Trait(name='T', methods=[])
        module = Module(body=[trait1, trait2])
        analyzer = SemanticAnalyzer()
        analyzer.check(module)
        self.assertTrue(any('Duplicate' in err for err in analyzer.errors))

if __name__ == '__main__':
    unittest.main() 
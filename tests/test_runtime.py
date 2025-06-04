import unittest
from src.runtime import Runtime
from src.ast import *
from src.utils import GlyphError
from src.formatter_linter_stub import format_code, lint_code
from src.lsp_stub import GlyphLSPServer

class TestRuntime(unittest.TestCase):
    def setUp(self):
        print(f'\n[RuntimeTest] Running: {self._testMethodName}')

    def test_event_dispatch(self):
        runtime = Runtime()
        event = Event(name='TestEvent', fields=[])
        handler = EventHandler(event='TestEvent', block=Block(statements=[]))
        runtime.run(Module(body=[event, handler]))
        runtime.dispatch_event('TestEvent', {})
        self.assertIn('TestEvent', runtime.events)
        self.assertIn('TestEvent', runtime.event_handlers)

    def test_trait_registration(self):
        runtime = Runtime()
        trait = Trait(name='TestTrait', methods=[])
        runtime.run(Module(body=[trait]))
        self.assertIn('TestTrait', runtime.traits)

    def test_entity_lifecycle(self):
        runtime = Runtime()
        created = []
        destroyed = []
        def on_create(entity):
            created.append(entity['name'])
        def on_destroy(entity):
            destroyed.append(entity['name'])
        runtime.entity_manager.on_create = on_create
        runtime.entity_manager.on_destroy = on_destroy
        eid = runtime.entity_manager.create_entity('TestEntity', [])
        runtime.entity_manager.destroy_entity(eid)
        self.assertIn('TestEntity', created)
        self.assertIn('TestEntity', destroyed)

    def test_semantic_error(self):
        from src.semantics import SemanticAnalyzer
        module = Module(body=[Trait(name='T', methods=[]), Trait(name='T', methods=[])])
        analyzer = SemanticAnalyzer()
        analyzer.check(module)
        self.assertTrue(any('Duplicate' in err for err in analyzer.errors))

    def test_type_error(self):
        from src.semantics import SemanticAnalyzer
        fn = FunctionDef(name='f', params=[Param('x', 'float')], return_type='float', block=Block(statements=[ReturnStatement(value=String('not a float'))]))
        module = Module(body=[fn])
        analyzer = SemanticAnalyzer()
        analyzer.check(module)
        self.assertTrue(any('TypeError' in err for err in analyzer.errors))

    def test_runtime_stack_trace(self):
        try:
            raise GlyphError('Test error', line=1, col=1, context='bad', error_type='RuntimeError')
        except GlyphError as e:
            self.assertIn('Stack trace', str(e))

    def test_formatter_linter(self):
        code = 'component Foo\n    x: float'  # missing colon
        formatted = format_code(code)
        self.assertIn('component Foo:', formatted)
        warnings = lint_code(code)
        self.assertTrue(any('Missing colon' in w for w in warnings))

    def test_lsp_diagnostics_hover(self):
        lsp = GlyphLSPServer()
        diags = [{'message': 'Test error', 'range': {}}]
        result = lsp.textDocument_publishDiagnostics({'diagnostics': diags})
        self.assertEqual(result, diags)
        hover = lsp.textDocument_hover({'symbol': 'Foo'})
        self.assertIn('Hover info', hover['contents'])

if __name__ == '__main__':
    unittest.main() 
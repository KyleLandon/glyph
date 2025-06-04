import unittest
from src.lexer import Lexer
from src.parser import Parser
from src.ast import *
from src.utils import GlyphError

class TestParser(unittest.TestCase):
    def setUp(self):
        print(f'\n[ParserTest] Running: {self._testMethodName}')

    def parse_code(self, code):
        lexer = Lexer(code)
        tokens = lexer.tokenize()
        parser = Parser(tokens)
        return parser.parse_module()

    def test_parse_entity(self):
        code = 'entity Player: Position(x=0, y=0)'
        module = self.parse_code(code)
        self.assertIsInstance(module.body[0], Entity)
        self.assertEqual(module.body[0].name, 'Player')

    def test_parse_component(self):
        code = 'component Health: hp: float'
        module = self.parse_code(code)
        self.assertIsInstance(module.body[0], Component)
        self.assertEqual(module.body[0].name, 'Health')
        self.assertEqual(module.body[0].fields[0].name, 'hp')

    def test_parse_system(self):
        code = 'system Main: print("hi")'
        module = self.parse_code(code)
        self.assertIsInstance(module.body[0], System)
        self.assertEqual(module.body[0].name, 'Main')

    def test_parse_event(self):
        code = 'event Foo: msg: str'
        module = self.parse_code(code)
        self.assertIsInstance(module.body[0], Event)
        self.assertEqual(module.body[0].name, 'Foo')

    def test_parse_trait(self):
        code = 'trait Mover: fn move(dx: float): print("move")'
        module = self.parse_code(code)
        self.assertIsInstance(module.body[0], Trait)
        self.assertEqual(module.body[0].name, 'Mover')
        self.assertEqual(module.body[0].methods[0].name, 'move')

    def test_parse_function(self):
        code = 'fn add(a: float, b: float) -> float: return a + b'
        module = self.parse_code(code)
        self.assertIsInstance(module.body[0], FunctionDef)
        self.assertEqual(module.body[0].name, 'add')
        self.assertEqual(module.body[0].params[0].name, 'a')
        self.assertEqual(module.body[0].return_type, 'float')

    def test_parse_async_await(self):
        code = 'fn foo(): await bar()'
        module = self.parse_code(code)
        self.assertIsInstance(module.body[0], FunctionDef)
        stmt = module.body[0].block.statements[0]
        self.assertIsInstance(stmt, Await)

    def test_parse_assignment(self):
        code = 'x = 42'
        module = self.parse_code(code)
        self.assertIsInstance(module.body[0], Assignment)
        self.assertEqual(module.body[0].target, 'x')

    def test_parse_expression(self):
        code = 'a + b * 2'
        module = self.parse_code(code)
        expr = module.body[0]
        self.assertIsInstance(expr, BinaryOp)
        self.assertEqual(expr.op, '+')
        self.assertIsInstance(expr.right, BinaryOp)
        self.assertEqual(expr.right.op, '*')

    def test_parse_event_handler(self):
        code = 'on Foo: print("hi")'
        module = self.parse_code(code)
        self.assertIsInstance(module.body[0], EventHandler)
        self.assertEqual(module.body[0].event, 'Foo')

    def test_parse_if_while_for(self):
        code = 'if a: print(1) while b: print(2) for e in entities.with(Foo): print(3)'
        module = self.parse_code(code)
        self.assertIsInstance(module.body[0], If)
        self.assertIsInstance(module.body[1], While)
        self.assertIsInstance(module.body[2], For)

    def test_parse_match(self):
        code = 'match x case 1: print(1) case 2: print(2)'
        module = self.parse_code(code)
        self.assertIsInstance(module.body[0], Match)
        self.assertEqual(len(module.body[0].cases), 2)

    def test_parse_return(self):
        code = 'fn foo(): return 42'
        module = self.parse_code(code)
        stmt = module.body[0].block.statements[0]
        self.assertIsInstance(stmt, ReturnStatement)

    def test_parse_invalid_syntax(self):
        code = 'entity'
        with self.assertRaises(GlyphError):
            self.parse_code(code)
        code2 = 'fn foo( -> float:'
        with self.assertRaises(GlyphError):
            self.parse_code(code2)

if __name__ == '__main__':
    unittest.main() 
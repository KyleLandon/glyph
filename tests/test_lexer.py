import unittest
from src.lexer import Lexer

class TestLexer(unittest.TestCase):
    def test_simple(self):
        code = 'entity Player: Position(x=0, y=0)'
        lexer = Lexer(code)
        tokens = lexer.tokenize()
        self.assertTrue(any(tok[0] == 'ID' and tok[1] == 'entity' for tok in tokens))
        self.assertTrue(any(tok[0] == 'ID' and tok[1] == 'Player' for tok in tokens))

if __name__ == '__main__':
    unittest.main() 
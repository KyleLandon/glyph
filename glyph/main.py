# Minimal Glyph main

from lexer import tokenize
from parser import parse
from interpreter import interpret
import sys

def run_file(filename):
    with open(filename) as f:
        source = f.read()
    tokens = tokenize(source)
    ast = parse(tokens)
    interpret(ast)

if __name__ == '__main__':
    if len(sys.argv) > 1:
        run_file(sys.argv[1])
    else:
        print("Usage: python main.py <file.glyph>") 
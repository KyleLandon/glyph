import argparse
from src.lexer import Lexer
from src.parser import Parser
from src.semantics import SemanticAnalyzer
from src.runtime import Runtime
from src.stdlib import StdLib
from src.utils import GlyphError, VERBOSE, print_debug
import sys


def main():
    parser = argparse.ArgumentParser(description='Run a Glyph source file.')
    parser.add_argument('filename', help='Glyph source file')
    parser.add_argument('--verbose', action='store_true', help='Enable verbose debug output')
    parser.add_argument('--trace', action='store_true', help='Print stack traces on error')
    parser.add_argument('--break-on-error', action='store_true', help='Break on first error')
    parser.add_argument('--debug', action='store_true', help='Enable debug mode (same as verbose)')
    args = parser.parse_args()

    if args.verbose or args.debug:
        import utils
        utils.VERBOSE = True
        print_debug('Verbose/debug mode enabled', stack=True)

    with open(args.filename, 'r', encoding='utf-8') as f:
        source = f.read()

    try:
        lexer = Lexer(source)
        tokens = lexer.tokenize()
        parser = Parser(tokens, source=source)
        module = parser.parse()
        analyzer = SemanticAnalyzer()
        analyzer.check(module)
        if analyzer.errors:
            print('Semantic errors:')
            for err in analyzer.errors:
                print('  -', err)
            if args.break_on_error:
                sys.exit(1)
        stdlib = StdLib()
        stdlib.register_builtins()
        runtime = Runtime()
        runtime.run(module)
    except GlyphError as e:
        print(e)
        if args.trace or VERBOSE:
            import traceback
            traceback.print_exc()
        if args.break_on_error:
            sys.exit(1)
    except Exception as e:
        print(f'Internal error: {e}')
        if args.trace or VERBOSE:
            import traceback
            traceback.print_exc()
        if args.break_on_error:
            sys.exit(1)

def run_repl():
    print("Glyph REPL. Type 'quit' to exit.")
    runtime = Runtime()
    while True:
        try:
            line = input('>>> ')
            if line.strip() == 'quit':
                break
            lexer = Lexer(line)
            tokens = lexer.tokenize()
            parser = Parser(tokens)
            module = parser.parse_module()
            runtime.run(module)
        except GlyphError as e:
            print(e)
            if hasattr(e, 'stack') and e.stack:
                print('Stack trace:')
                print(''.join(e.stack))
        except Exception as e:
            print(f'Error: {e}')
            import traceback
            traceback.print_exc()

if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'repl':
        run_repl()
    else:
        print("Usage: python -m src.cli repl") 
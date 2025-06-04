import math as pymath
import collections
import os
import socket

class StdLib:
    def __init__(self):
        self.builtins = {}
        self.modules = {}

    def register_builtins(self):
        # Math
        self.builtins['print'] = lambda *args: print(*args)
        self.builtins['sqrt'] = pymath.sqrt
        # String
        self.builtins['upper'] = lambda s: s.upper() if isinstance(s, str) else s
        # Collections
        self.builtins['list'] = lambda *args: list(args)
        # File I/O
        self.builtins['read_file'] = lambda path: open(path).read()
        # Networking
        self.builtins['gethost'] = lambda: socket.gethostname()
        # Add more built-ins as needed

    def register_import(self, name, module):
        self.modules[name] = module
        print(f"Module '{name}' imported.")

    def import_module(self, name):
        if name in self.modules:
            print(f"Importing module: {name}")
            return self.modules[name]
        else:
            raise ImportError(f"Module '{name}' not found.") 
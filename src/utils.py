import traceback

def not_implemented():
    raise NotImplementedError()

class GlyphError(Exception):
    def __init__(self, message, line=None, col=None, context=None, suggestion=None, code=None, error_type=None, stack=None):
        super().__init__(message)
        self.message = message
        self.line = line
        self.col = col
        self.context = context
        self.suggestion = suggestion
        self.code = code
        self.error_type = error_type
        self.stack = stack or traceback.format_stack()

    def __str__(self):
        out = f"{self.error_type or 'Error'}: {self.message}"
        if self.code:
            out += f"\n[Code: {self.code}]"
        if self.line is not None and self.col is not None:
            out += f"\n --> line {self.line}, col {self.col}"
        if self.context:
            out += f"\n {self.context}"
            if self.col is not None:
                out += f"\n {' ' * (self.col-1)}^"
        if self.suggestion:
            out += f"\nSuggestion: {self.suggestion}"
        if self.stack:
            out += f"\nStack trace:\n{''.join(self.stack)}"
        return out

VERBOSE = False

def print_debug(msg, stack=False):
    if VERBOSE:
        print(f"[DEBUG] {msg}")
        if stack:
            traceback.print_stack() 
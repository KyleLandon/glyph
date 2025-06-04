class GlyphLSPServer:
    def __init__(self):
        print("Glyph LSP Server initialized (stub)")
        self.diagnostics = []

    def initialize(self, params):
        print("LSP: initialize", params)
        return {"capabilities": {}}

    def textDocument_didOpen(self, params):
        print("LSP: textDocument/didOpen", params)

    def textDocument_completion(self, params):
        print("LSP: textDocument/completion", params)
        return {"items": []}

    def textDocument_publishDiagnostics(self, params):
        print("LSP: textDocument/publishDiagnostics", params)
        self.diagnostics = params.get('diagnostics', [])
        for diag in self.diagnostics:
            print(f"Diagnostic: {diag}")
        return self.diagnostics

    def textDocument_hover(self, params):
        symbol = params.get('symbol', '')
        info = f"Hover info for {symbol} (stub)"
        print(f"LSP: hover {symbol}")
        return {"contents": info} 
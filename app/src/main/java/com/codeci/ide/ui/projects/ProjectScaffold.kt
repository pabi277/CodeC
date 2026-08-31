package com.codeci.ide.ui.projects

/** One starter file written into a freshly created project. */
data class ScaffoldFile(val relativePath: String, val content: String)

/**
 * Phase 14 — starter files for the project types the Files-tab wizard can
 * create. Pure and host-testable: [filesFor] returns the exact bytes to write
 * and ProjectManager writes them through the confined FileTreeRepository.
 *
 * The Python server templates deliberately have a dual path: when the real
 * framework (Flask / FastAPI+uvicorn) is installed via `pip` it runs it;
 * otherwise a stdlib fallback serves the exact same pages, so the project
 * works on every CodeC device out of the box.
 */
object ProjectScaffold {

    /** `"""` — lets the Python templates embed Python triple-quoted strings. */
    private const val Q = "\"\"\""

    private const val PYTHON_PIP_HINT =
        "pkg install -y python-pip && pip install"

    fun filesFor(type: String): List<ScaffoldFile> = when (type.trim().lowercase()) {
        "c" -> listOf(ScaffoldFile("main.c", C_STARTER))
        "web", "static-web" -> listOf(ScaffoldFile("index.html", WEB_INDEX))
        "python" -> listOf(ScaffoldFile("main.py", PYTHON_STARTER))
        "python-flask" -> listOf(
            ScaffoldFile("app.py", FLASK_APP),
            ScaffoldFile("index.html", FLASK_INDEX)
        )
        "python-fastapi" -> listOf(
            ScaffoldFile("main.py", FASTAPI_APP),
            ScaffoldFile("index.html", FASTAPI_INDEX)
        )
        "c-microservice" -> listOf(ScaffoldFile("server.c", C_SERVER))
        else -> emptyList()
    }

    // Byte-identical to the Phase 1 starter ProjectManager used to write.
    private const val C_STARTER =
        "#include <stdio.h>\n\nint main(void) {\n    printf(\"Hello, CodeC!\\n\");\n    return 0;\n}\n"

    private const val WEB_INDEX = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>CodeC Web Page</title>
  <style>
    body { font-family: sans-serif; margin: 3rem auto; max-width: 640px; padding: 0 1rem; }
    h1 { color: #2f6fed; }
  </style>
</head>
<body>
  <h1>Welcome to CodeC Web!</h1>
  <p>Edit this file, then tap Reload in the Web Preview to see the change.</p>
</body>
</html>
"""

    private val PYTHON_STARTER = """#!/usr/bin/env python3
$QCodeC Python starter.$Q
import sys

def greet(name):
    return "Hello, %s!" % name

if __name__ == "__main__":
    print(greet(sys.argv[1] if len(sys.argv) > 1 else "CodeC"))
"""

    private val FLASK_APP = """#!/usr/bin/env python3
$QCodeC Flask starter — Phase 14.

Serves a small web app on http://127.0.0.1:5000. When the Flask package is
installed it runs the real framework; otherwise a stdlib fallback serves the
same pages, so the project runs out of the box.

The page is read from index.html on every request, so edit it and tap
Reload in the Web Preview — no restart needed. To use the real framework:
    pkg install -y python-pip && pip install flask
$Q
import os
import sys

PORT = 5000
PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))


def load_page():
    with open(os.path.join(PROJECT_DIR, "index.html"), encoding="utf-8") as f:
        return f.read()


try:
    from flask import Flask, jsonify

    app = Flask(__name__)

    @app.route("/")
    def index():
        return load_page()

    @app.route("/api/hello")
    def hello():
        return jsonify(message="Hello from CodeC Flask!")

    if __name__ == "__main__":
        print(" * Running on http://127.0.0.1:%d/ (CodeC Flask)" % PORT, flush=True)
        app.run(host="127.0.0.1", port=PORT, debug=False, use_reloader=False)
except ImportError:
    from http.server import BaseHTTPRequestHandler, HTTPServer

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            body = load_page().encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, fmt, *args):
            sys.stderr.write(" * %s\n" % (fmt % args))

    if __name__ == "__main__":
        print(
            " * Running on http://127.0.0.1:%d/ (CodeC stdlib fallback; install flask via: %s flask)"
            % (PORT, "$PYTHON_PIP_HINT"),
            flush=True,
        )
        HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
"""

    private const val FLASK_INDEX = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>CodeC Flask App</title>
  <style>
    body { font-family: sans-serif; margin: 3rem auto; max-width: 640px; padding: 0 1rem; }
    h1 { color: #2f6fed; }
    code { background: #f0f0f0; padding: 2px 6px; border-radius: 4px; }
  </style>
</head>
<body>
  <h1>Welcome to CodeC Flask App!</h1>
  <p>Edit this HTML file, then tap <strong>Reload</strong> in the Web Preview — the running server picks it up.</p>
  <p>API: <code>/api/hello</code></p>
</body>
</html>
"""

    private val FASTAPI_APP = """#!/usr/bin/env python3
$QCodeC FastAPI starter — Phase 14.

Serves a small web API on http://127.0.0.1:8000. When FastAPI + uvicorn are
installed it runs the real framework; otherwise a stdlib fallback serves the
same pages, so the project runs out of the box.

The page is read from index.html on every request, so edit it and tap
Reload in the Web Preview — no restart needed. To use the real framework:
    pkg install -y python-pip && pip install fastapi uvicorn
$Q
import os
import sys

PORT = 8000
PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))


def load_page():
    with open(os.path.join(PROJECT_DIR, "index.html"), encoding="utf-8") as f:
        return f.read()


try:
    from fastapi import FastAPI
    from fastapi.responses import HTMLResponse
    import uvicorn

    app = FastAPI(title="CodeC FastAPI App")

    @app.get("/", response_class=HTMLResponse)
    def index():
        return load_page()

    @app.get("/api/hello")
    def hello():
        return {"message": "Hello from CodeC FastAPI!"}

    if __name__ == "__main__":
        print("Uvicorn running on http://127.0.0.1:%d/ (CodeC FastAPI)" % PORT, flush=True)
        uvicorn.run(app, host="127.0.0.1", port=PORT, log_level="info")
except ImportError:
    from http.server import BaseHTTPRequestHandler, HTTPServer

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            body = load_page().encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, fmt, *args):
            sys.stderr.write(" * %s\n" % (fmt % args))

    if __name__ == "__main__":
        print(
            "Uvicorn running on http://127.0.0.1:%d/ (CodeC stdlib fallback; install fastapi via: %s fastapi uvicorn)"
            % (PORT, "$PYTHON_PIP_HINT"),
            flush=True,
        )
        HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
"""

    private const val FASTAPI_INDEX = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>CodeC FastAPI App</title>
  <style>
    body { font-family: sans-serif; margin: 3rem auto; max-width: 640px; padding: 0 1rem; }
    h1 { color: #2f6fed; }
    code { background: #f0f0f0; padding: 2px 6px; border-radius: 4px; }
  </style>
</head>
<body>
  <h1>Welcome to CodeC FastAPI App!</h1>
  <p>Edit this HTML file, then tap <strong>Reload</strong> in the Web Preview — the running server picks it up.</p>
  <p>API: <code>/api/hello</code> — Swagger UI at <code>/docs</code> when FastAPI is installed.</p>
</body>
</html>
"""

    private const val C_SERVER = """/*
 * CodeC C microservice starter — Phase 14.
 * A tiny single-threaded HTTP server on http://127.0.0.1:8080, written to
 * compile with the embedded TCC (no external libraries):
 *     cc server.c -o bin/server
 */
#include <arpa/inet.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#define PORT 8080

static const char PAGE[] =
    "<!doctype html>\n"
    "<html>\n<head>\n<meta charset=\"utf-8\">\n"
    "<title>CodeC C Microservice</title>\n</head>\n<body>\n"
    "<h1>Welcome to CodeC C Microservice!</h1>\n"
    "<p>This page is served by a C HTTP server compiled with CodeC's TCC.</p>\n"
    "</body>\n</html>\n";

static void handle_client(int client) {
    char request[2048];
    (void)read(client, request, sizeof(request) - 1);
    request[sizeof(request) - 1] = '\0';

    char header[512];
    int header_len = snprintf(
        header, sizeof(header),
        "HTTP/1.1 200 OK\r\n"
        "Content-Type: text/html; charset=utf-8\r\n"
        "Content-Length: %lu\r\n"
        "Connection: close\r\n"
        "\r\n",
        (unsigned long)strlen(PAGE));
    if (header_len > 0 && header_len < (int)sizeof(header)) {
        (void)write(client, header, (size_t)header_len);
        (void)write(client, PAGE, strlen(PAGE));
    }
    close(client);
}

int main(void) {
    int server = socket(AF_INET, SOCK_STREAM, 0);
    if (server < 0) {
        perror("socket");
        return 1;
    }
    int yes = 1;
    (void)setsockopt(server, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = htons(PORT);

    if (bind(server, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        perror("bind");
        close(server);
        return 1;
    }
    if (listen(server, 8) < 0) {
        perror("listen");
        close(server);
        return 1;
    }
    printf("CodeC server listening on http://127.0.0.1:%d\n", PORT);
    fflush(stdout);

    for (;;) {
        int client = accept(server, NULL, NULL);
        if (client < 0) {
            continue;
        }
        handle_client(client);
    }
    return 0;
}
"""
}

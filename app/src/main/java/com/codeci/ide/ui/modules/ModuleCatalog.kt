package com.codeci.ide.ui.modules

enum class PackageCategory(val title: String) {
    ALL("All"),
    COMPILERS("Compilers & Build"),
    EDITORS("Editors & Shells"),
    LANGUAGES("Languages"),
    CLI_TOOLS("CLI & Search"),
    UTILS("Archives & Utils")
}

data class PackageItem(
    val id: String,
    val name: String,
    val binary: String,
    val category: PackageCategory,
    val description: String,
    val version: String = "latest",
    val installCommand: String = "pkg install -y $id",
    val runCommand: String = binary,
    val isBuiltIn: Boolean = false
)

data class QuickAction(
    val title: String,
    val subtitle: String,
    val command: String
)

object PackageCatalog {
    val ALL_PACKAGES: List<PackageItem> = listOf(
        // Compilers & Build Tools
        PackageItem(
            id = "tcc",
            name = "TCC (Tiny C Compiler)",
            binary = "cc",
            category = PackageCategory.COMPILERS,
            description = "Built-in, ultra-fast C99 compiler. Works offline without downloading.",
            installCommand = "cc --help",
            runCommand = "cc",
            isBuiltIn = true
        ),
        PackageItem(
            id = "clang",
            name = "Clang / LLVM",
            binary = "clang",
            category = PackageCategory.COMPILERS,
            description = "Complete LLVM C/C++ compiler toolchain (C11, C17, C20, C++).",
            installCommand = "pkg install -y clang",
            runCommand = "clang --version"
        ),
        PackageItem(
            id = "make",
            name = "GNU Make",
            binary = "make",
            category = PackageCategory.COMPILERS,
            description = "Build automation tool to control the generation of executables from source code.",
            installCommand = "pkg install -y make",
            runCommand = "make --version"
        ),
        PackageItem(
            id = "binutils",
            name = "GNU Binutils",
            binary = "ld",
            category = PackageCategory.COMPILERS,
            description = "Binary tools: linker (ld), assembler (as), objdump, ar, nm, strip.",
            installCommand = "pkg install -y binutils",
            runCommand = "ld --version"
        ),
        PackageItem(
            id = "cmake",
            name = "CMake",
            binary = "cmake",
            category = PackageCategory.COMPILERS,
            description = "Modern cross-platform build system generator.",
            installCommand = "pkg install -y cmake",
            runCommand = "cmake --version"
        ),
        PackageItem(
            id = "gdb",
            name = "GDB Debugger",
            binary = "gdb",
            category = PackageCategory.COMPILERS,
            description = "GNU Project Debugger for inspecting code execution, breakpoints and core dumps.",
            installCommand = "pkg install -y gdb",
            runCommand = "gdb --version"
        ),

        // Editors & Shells
        PackageItem(
            id = "nano",
            name = "GNU nano",
            binary = "nano",
            category = PackageCategory.EDITORS,
            description = "Small, friendly terminal text editor with syntax highlighting and shortcuts.",
            installCommand = "pkg install -y nano",
            runCommand = "nano"
        ),
        PackageItem(
            id = "vim",
            name = "Vim",
            binary = "vim",
            category = PackageCategory.EDITORS,
            description = "Highly configurable, powerful modal text editor for efficient text editing.",
            installCommand = "pkg install -y vim",
            runCommand = "vim"
        ),
        PackageItem(
            id = "tmux",
            name = "tmux",
            binary = "tmux",
            category = PackageCategory.EDITORS,
            description = "Terminal multiplexer enabling split panes, multi-windows, and background sessions.",
            installCommand = "pkg install -y tmux",
            runCommand = "tmux"
        ),
        PackageItem(
            id = "bash",
            name = "GNU Bash",
            binary = "bash",
            category = PackageCategory.EDITORS,
            description = "Bourne-Again Shell (5.2+) with command history, tab completion, and scripting.",
            installCommand = "pkg install -y bash",
            runCommand = "bash"
        ),

        // Programming Languages
        PackageItem(
            id = "python",
            name = "Python 3",
            binary = "python3",
            category = PackageCategory.LANGUAGES,
            description = "Python 3 interpreter with standard library, interactive REPL, and pip.",
            installCommand = "pkg install -y python",
            runCommand = "python3"
        ),
        PackageItem(
            id = "lua",
            name = "Lua 5.4",
            binary = "lua",
            category = PackageCategory.LANGUAGES,
            description = "Powerful, efficient, lightweight, embeddable scripting language.",
            installCommand = "pkg install -y lua",
            runCommand = "lua"
        ),
        PackageItem(
            id = "nodejs",
            name = "Node.js",
            binary = "node",
            category = PackageCategory.LANGUAGES,
            description = "JavaScript runtime built on Chrome's V8 engine with npm package manager.",
            installCommand = "pkg install -y nodejs",
            runCommand = "node -v"
        ),

        // CLI Utilities & Search
        PackageItem(
            id = "git",
            name = "Git",
            binary = "git",
            category = PackageCategory.CLI_TOOLS,
            description = "Fast, scalable, distributed revision control system with full remote repo support.",
            installCommand = "pkg install -y git",
            runCommand = "git status"
        ),
        PackageItem(
            id = "gh",
            name = "GitHub CLI",
            binary = "gh",
            category = PackageCategory.CLI_TOOLS,
            description = "GitHub's official command line tool for pull requests, issues, and releases.",
            installCommand = "pkg install -y gh",
            runCommand = "gh --help"
        ),
        PackageItem(
            id = "ripgrep",
            name = "ripgrep (rg)",
            binary = "rg",
            category = PackageCategory.CLI_TOOLS,
            description = "Line-oriented search tool that recursively searches current directory with regex.",
            installCommand = "pkg install -y ripgrep",
            runCommand = "rg --help"
        ),
        PackageItem(
            id = "bat",
            name = "bat",
            binary = "bat",
            category = PackageCategory.CLI_TOOLS,
            description = "Cat clone with syntax highlighting and automatic Git diff integration.",
            installCommand = "pkg install -y bat",
            runCommand = "bat --help"
        ),
        PackageItem(
            id = "fd",
            name = "fd-find",
            binary = "fd",
            category = PackageCategory.CLI_TOOLS,
            description = "Simple, fast, and user-friendly alternative to the traditional find command.",
            installCommand = "pkg install -y fd",
            runCommand = "fd --help"
        ),
        PackageItem(
            id = "htop",
            name = "htop",
            binary = "htop",
            category = PackageCategory.CLI_TOOLS,
            description = "Interactive process viewer, CPU/Memory meter, and process management tool.",
            installCommand = "pkg install -y htop",
            runCommand = "htop"
        ),
        PackageItem(
            id = "tree",
            name = "tree",
            binary = "tree",
            category = PackageCategory.CLI_TOOLS,
            description = "Recursive directory listing program that produces a depth-indented file tree.",
            installCommand = "pkg install -y tree",
            runCommand = "tree"
        ),
        PackageItem(
            id = "curl",
            name = "cURL",
            binary = "curl",
            category = PackageCategory.CLI_TOOLS,
            description = "Command line tool and library for transferring data with URLs (HTTP, HTTPS, FTP).",
            installCommand = "pkg install -y curl",
            runCommand = "curl --version"
        ),
        PackageItem(
            id = "wget",
            name = "GNU Wget",
            binary = "wget",
            category = PackageCategory.CLI_TOOLS,
            description = "Network utility to retrieve files from the web via HTTP, HTTPS, and FTP.",
            installCommand = "pkg install -y wget",
            runCommand = "wget --version"
        ),

        // Compression & Archives
        PackageItem(
            id = "coreutils",
            name = "GNU Coreutils",
            binary = "cat",
            category = PackageCategory.UTILS,
            description = "Basic file, shell, and text manipulation utilities of the GNU operating system.",
            installCommand = "pkg install -y coreutils",
            runCommand = "cat --version"
        ),
        PackageItem(
            id = "tar",
            name = "GNU Tar",
            binary = "tar",
            category = PackageCategory.UTILS,
            description = "Archive program for creating, maintaining, modifying, and extracting tape archives.",
            installCommand = "pkg install -y tar",
            runCommand = "tar --version"
        ),
        PackageItem(
            id = "gzip",
            name = "GNU Gzip",
            binary = "gzip",
            category = PackageCategory.UTILS,
            description = "Standard compression and decompression utility using Lempel-Ziv coding (LZ77).",
            installCommand = "pkg install -y gzip",
            runCommand = "gzip --version"
        ),
        PackageItem(
            id = "zstd",
            name = "Zstandard (zstd)",
            binary = "zstd",
            category = PackageCategory.UTILS,
            description = "Fast real-time compression algorithm providing high compression ratios.",
            installCommand = "pkg install -y zstd",
            runCommand = "zstd --version"
        ),
        PackageItem(
            id = "diffutils",
            name = "GNU Diffutils",
            binary = "diff",
            category = PackageCategory.UTILS,
            description = "Programs for finding the differences between files (diff, cmp, diff3, sdiff).",
            installCommand = "pkg install -y diffutils",
            runCommand = "diff --version"
        ),
        PackageItem(
            id = "patch",
            name = "GNU Patch",
            binary = "patch",
            category = PackageCategory.UTILS,
            description = "Tool to apply diff/patch files to original files to update source trees.",
            installCommand = "pkg install -y patch",
            runCommand = "patch --version"
        )
    )

    val QUICK_ACTIONS: List<QuickAction> = listOf(
        QuickAction("pkg update", "Refresh package indexes", "pkg update"),
        QuickAction("pkg upgrade", "Upgrade all packages", "pkg upgrade -y"),
        QuickAction("setup-storage", "Connect ~/storage links", "codec-setup-storage"),
        QuickAction("pkg status", "Check trust & keyring", "pkg status"),
        QuickAction("pkg heal", "Repair alternatives DB", "pkg heal"),
        QuickAction("pkg repair", "Fix interrupted state", "pkg repair")
    )
}

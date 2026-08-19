package com.example.ui.services

import com.example.ui.utils.AppLogger
import kotlinx.coroutines.delay
import kotlin.random.Random

data class CompilationResult(val success: Boolean, val errors: List<String>)
data class ExecutionResult(val output: String, val exitCode: Int, val executionTime: Long)

class CompilerService {

    suspend fun compile(code: String): CompilationResult {
        AppLogger.i("CompilerService", "Starting compilation (${code.length} bytes)")
        delay(1000) // Simulate compilation delay
        val errors = mutableListOf<String>()

        // 1. Check for main function
        if (!code.contains("main")) {
            errors.add("Error: undefined reference to 'main'")
        }

        // 2. Check for unmatched braces
        val openBraces = code.count { it == '{' }
        val closeBraces = code.count { it == '}' }
        if (openBraces > closeBraces) {
            errors.add("Error: expected '}' at end of input")
        } else if (closeBraces > openBraces) {
            errors.add("Error: extraneous '}' found")
        }

        // 3. Check for missing semicolons (basic heuristic)
        val statementKeywords = listOf("return", "printf", "int ", "float ", "char ", "double ", "long ")
        code.lines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            // If it looks like a standard statement but lacks a semicolon
            if (statementKeywords.any { trimmed.startsWith(it) } && !trimmed.endsWith(";")) {
                errors.add("Line ${index + 1}: Error: expected ';' at end of declaration or statement")
            }
        }

        val success = errors.isEmpty()
        AppLogger.i("CompilerService", "Compilation finished. Success: $success. Errors: ${errors.size}")
        return CompilationResult(success = success, errors = errors)
    }

    suspend fun run(code: String): ExecutionResult {
        AppLogger.i("CompilerService", "Starting execution")
        delay(500) // Simulate execution delay
        
        val outputBuilder = java.lang.StringBuilder()
        
        // Very basic regex to extract printf contents
        // This simulates actual execution for basic 'Hello World' style apps
        val printfRegex = Regex("""printf\s*\(\s*"([^"]*)"\s*\)""")
        val matches = printfRegex.findAll(code)
        
        for (match in matches) {
            var text = match.groupValues[1]
            // Handle basic escape sequences like \n
            text = text.replace("\\n", "\n").replace("\\t", "\t")
            outputBuilder.append(text)
        }
        
        val executionTime = Random.nextLong(1, 15) // Simulate 1-15ms execution time
        
        AppLogger.i("CompilerService", "Execution finished in ${executionTime}ms")
        
        return ExecutionResult(
            output = outputBuilder.toString(),
            exitCode = 0,
            executionTime = executionTime
        )
    }
}

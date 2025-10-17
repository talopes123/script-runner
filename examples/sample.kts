// Sample Kotlin script demonstrating various features
fun fibonacci(n: Int): Int {
    if (n <= 1) {
        return n
    }
    return fibonacci(n-1) + fibonacci(n-2)
}

fun greetUser(name: String): String {
    return "Hello, $name!"
}

// Main execution
println("Kotlin Script Runner Demo")
println("=========================")

val names = listOf("Alice", "Bob", "Charlie")
for (name in names) {
    println(greetUser(name))
}

println("\nFibonacci sequence:")
for (i in 0..10) {
    println("fib($i) = ${fibonacci(i)}")
}

// Demonstrate long-running operation
println("\nCountdown:")
for (i in 5 downTo 1) {
    println("Count: $i")
    Thread.sleep(1000)
}

println("Done!")
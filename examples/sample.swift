
func fibonacci(n: Int) -> Int {
    if n <= 1 {
        return n
    }
    return fibonacci(n: n-1) + fibonacci(n: n-2)
}

func greetUser(name: String) -> String {
    return "Hello, \(name)!"
}

// Main execution
print("Swift Script Runner Demo")
print("=======================")

let names = ["Alice", "Bob", "Charlie"]
for name in names {
    print(greetUser(name: name))
}

print("\nFibonacci sequence:")
for i in 0...5 {
    print("fib(\(i)) = \(fibonacci(n: i))")
}

print("Done!")
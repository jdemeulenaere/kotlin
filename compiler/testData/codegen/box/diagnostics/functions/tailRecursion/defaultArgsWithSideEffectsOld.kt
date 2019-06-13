// !LANGUAGE: -ProperDefaultInitializationInTailrec
// TARGET_BACKEND: JVM
// IGNORE_BACKEND: JVM_IR

var counter = 0
fun calc(counter: Int) = if (counter % 2 == 0) "K" else "O"

tailrec fun test(x: Int, y: String = calc(counter++), z: String = calc(counter++)): String {
    if (x > 0)
        return y + z

    return test(x + 1)
}

fun box(): String {
    return test(0)
}
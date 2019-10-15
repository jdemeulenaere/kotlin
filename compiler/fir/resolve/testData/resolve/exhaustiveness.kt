enum class Enum {
    A, B, C
}

fun test_1(b: Boolean) {
    val x = when (b) {
        true -> 1
    }
    val y = when (b) {
        true -> 1
        false -> 2
    }
    val z = when (b) {
        true -> 1
        else -> 2
    }
}

fun test_2(e: Enum) {
    val a = when (e) {
        Enum.A -> 1
        Enum.B -> 2
    }

    val b = when (e) {
        Enum.A -> 1
        Enum.B -> 2
        is String -> 3
    }

    val c = when (e) {
        Enum.A -> 1
        Enum.B -> 2
        Enum.C -> 3
    }

    val d = when (e) {
        Enum.A -> 1
        else -> 2
    }
}
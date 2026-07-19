package sample

data class User(val id: Long, val name: String)

fun main() {
    println(User(1, "Kotlin"))
}

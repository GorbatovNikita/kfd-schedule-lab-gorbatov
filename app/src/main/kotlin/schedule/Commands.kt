package schedule

interface CommandHandlers {
    fun period(command: Command.Period): Int
    fun byType(command: Command.ByType): Int
    fun errors(command: Command.Errors): Int
}

object StudentCommandHandlers : CommandHandlers {
    override fun period(command: Command.Period): Int = runPeriod(command)
    override fun byType(command: Command.ByType): Int = runType(command)
    override fun errors(command: Command.Errors): Int = runErrors(command)
}

fun runPeriod(command: Command.Period): Int {
    var rejected = false
    var selected = 0
    forEachScheduleLine(command.file) { number, line ->
        when (val result = parseSessionLine(number, line)) {
            is ParseResult.Accepted -> {
                val session = result.session
                if (session.date >= command.from && session.date <= command.to) {
                    val location = when (session) {
                        is ActiveSession -> session.location
                        is Cancelled -> "-"
                    }
                    println("${session.date} | ${session.typeLabel()} | ${session.topic} | $location")
                    selected++
                }
            }
            is ParseResult.Rejected -> rejected = true
        }
    }
    println("Total: $selected")
    return if (rejected) 1 else 0
}

fun runType(command: Command.ByType): Int {
    var rejected = false
    var selected = 0
    forEachScheduleLine(command.file) { number, line ->
        when (val result = parseSessionLine(number, line)) {
            is ParseResult.Accepted -> {
                val session = result.session
                if (session.typeLabel() == command.type.name) {
                    val location: String
                    val teachers: String
                    when (session) {
                        is ActiveSession -> {
                            location = session.location
                            teachers = session.teachers.joinToString(", ")
                        }
                        is Cancelled -> {
                            location = "-"
                            teachers = "-"
                        }
                    }
                    println("${session.date} | ${session.typeLabel()} | ${session.topic} | $location | $teachers")
                    selected++
                }
            }
            is ParseResult.Rejected -> rejected = true
        }
    }
    println("Total: $selected")
    return if (rejected) 1 else 0
}

fun runErrors(command: Command.Errors): Int {
    var rejected = 0L
    var total = 0L
    forEachScheduleLine(command.file) { number, line ->
        total++
        when (val result = parseSessionLine(number, line)) {
            is ParseResult.Accepted -> Unit
            is ParseResult.Rejected -> {
                rejected++
                println(formatParseError(result))
            }
        }
    }
    println("Rejected: $rejected of $total")
    return if (rejected == 0L) 0 else 1
}

private fun Session.typeLabel(): String = when (this) {
    is Lecture -> "LECTURE"
    is Practice -> "PRACTICE"
    is Exam -> "EXAM"
    is Cancelled -> "CANCELLED"
}

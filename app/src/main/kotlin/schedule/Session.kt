package schedule

import java.time.LocalDate
import java.util.Collections

/** A schedule item. The sealed hierarchy keeps the four supported kinds explicit. */
sealed interface Session {
    val date: LocalDate
    val topic: String
}

class Lecture(
    override val date: LocalDate,
    override val topic: String,
    val location: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = immutableTeachers(teachers)

    init {
        validateCommon(date, topic)
        validateActive(location, this.teachers, minimumTeachers = 1)
    }
}

class Practice(
    override val date: LocalDate,
    override val topic: String,
    val location: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = immutableTeachers(teachers)

    init {
        validateCommon(date, topic)
        validateActive(location, this.teachers, minimumTeachers = 1)
    }
}

class Exam(
    override val date: LocalDate,
    override val topic: String,
    val location: String,
    teachers: List<String>,
) : Session {
    val teachers: List<String> = immutableTeachers(teachers)

    init {
        validateCommon(date, topic)
        validateActive(location, this.teachers, minimumTeachers = 2)
    }
}

class Cancelled(
    override val date: LocalDate,
    override val topic: String,
) : Session {
    init { validateCommon(date, topic) }
}

private fun immutableTeachers(source: List<String>): List<String> =
    Collections.unmodifiableList(ArrayList(source))

private fun validateCommon(date: LocalDate, topic: String) {
    require(date.year in 1..9999) { "date year must be between 1 and 9999" }
    require(topic.isNotBlank()) { "topic must not be empty or blank" }
    require(!topic.any(Char::isISOControl)) { "topic must not contain control characters" }
    require(topic == topic.trim()) { "topic must not have leading or trailing whitespace" }
}

private fun validateActive(location: String, teachers: List<String>, minimumTeachers: Int) {
    require(location.isNotBlank() && location != "-") { "location must not be empty or '-'" }
    require(!location.any(Char::isISOControl)) { "location must not contain control characters" }
    require(location == location.trim()) { "location must not have leading or trailing whitespace" }
    require(teachers.size >= minimumTeachers) { "at least $minimumTeachers teacher(s) required" }
    teachers.forEach { name ->
        require(name.isNotBlank() && name != "-") { "teacher names must not be empty or '-'" }
        require(!name.any(Char::isISOControl)) { "teacher names must not contain control characters" }
        require(name == name.trim()) { "teacher names must not have leading or trailing whitespace" }
    }
    require(teachers.distinct().size == teachers.size) { "teacher names must be distinct" }
}

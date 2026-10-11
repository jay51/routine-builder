package dev.routinebuilder

import java.time.DayOfWeek

private val allDays = DayOfWeek.entries.toSet()

internal data class PredefinedTask(
    val id: String,
    val title: String,
    val icon: String,
    val category: String,
    val kind: String,
    val instructions: String,
    val defaultDuration: Int,
    val defaultDays: Set<DayOfWeek>,
    val isDurationTask: Boolean = true
)

internal val taskLibrary = listOf(
    PredefinedTask("running", "Running", "🏃", "Exercise", "run", "Run a comfortable pace and route.", 25, setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)),
    PredefinedTask("walking", "Walking", "🚶", "Exercise", "walk", "Take an easy-paced walk and notice what's around you.", 15, allDays),
    PredefinedTask("strength", "Strength workout", "💪", "Exercise", "strength", "Do a short, manageable strength session. Rest whenever you need to.", 20, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
    PredefinedTask("stretching", "Stretching", "🧘", "Exercise", "stretch", "Move gently through a few stretches that feel comfortable.", 10, allDays),
    PredefinedTask("yoga", "Yoga", "🌿", "Exercise", "yoga", "Follow a short sequence at a pace that feels right for you.", 20, setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)),
    PredefinedTask("study", "Study session", "📚", "Learning", "study", "Pick one topic and focus on it. Take a short break afterward.", 25, setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
    PredefinedTask("reading", "Read a book", "📖", "Learning", "read", "Read something you enjoy or want to learn about.", 20, allDays),
    PredefinedTask("flashcards", "Review flashcards", "🗂️", "Learning", "review", "Review a small set of cards and pause when your focus fades.", 15, setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
    PredefinedTask("language", "Practice a language", "💬", "Learning", "language", "Practice a few words, listen to a short clip, or have a conversation.", 15, allDays),
    PredefinedTask("meditation", "Meditation", "🪷", "Wellbeing", "meditate", "Sit comfortably and return your attention to your breathing when it wanders.", 10, allDays),
    PredefinedTask("sleep", "Sleep", "😴", "Wellbeing", "sleep", "Set the hours you want to sleep. Choose a bedtime for your reminder and record your sleep when you're ready.", 480, allDays),
    PredefinedTask("journal", "Write in a journal", "✍️", "Wellbeing", "journal", "Write a few thoughts about your day, plans, or anything on your mind.", 5, allDays),
    PredefinedTask("breathing", "Breathing break", "🌬️", "Wellbeing", "breathe", "Take a few slow, comfortable breaths. There is no need to force them.", 5, allDays),
    PredefinedTask("sunlight", "Get outside", "🌤️", "Wellbeing", "outside", "Step outside for a little fresh air when it works for your day.", 10, allDays),
    PredefinedTask("bed", "Make your bed", "🛏️", "Daily Essentials", "bed", "Straighten your bedding in whatever way feels easy.", 5, allDays, false),
    PredefinedTask("bag", "Prepare tomorrow's bag", "🎒", "Daily Essentials", "prepare", "Gather the things you'll want to take with you tomorrow.", 5, setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY), false),
    PredefinedTask("tidy", "Tidy your space", "🧺", "Daily Essentials", "tidy", "Put a few things back where they belong. Stop when your time is up.", 10, allDays),
    PredefinedTask("lunch", "Prepare lunch", "🥪", "Daily Essentials", "lunch", "Put together a simple lunch that fits your day.", 15, setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), false),
)


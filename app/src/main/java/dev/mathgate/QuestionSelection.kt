package dev.mathgate

import java.util.Locale
import kotlin.random.Random

/**
 * Pure, deterministic-by-input question selection.
 *
 * The UI does not reroll an unanswered challenge. When a problem has been
 * solved and the reward CLAIMED, its ID enters persistent per-level history.
 * An ID cannot be presented again in that level until all questions in the
 * current library have been solved. After exhaustion we start a new cycle,
 * still excluding the immediately previous ID whenever possible.
 *
 * To keep the app varied, avoid the previous subject family as well when
 * there are alternative unseen families. The family preference never causes
 * a previously completed question to be shown early.
 */
object QuestionSelection {
    data class Item(val id: String, val family: String)

    fun family(topic: String): String {
        val t = topic.lowercase(Locale.ROOT)
        return when {
            "residue" in t || "complex" in t || "cauchy" in t -> "complex"
            "pde" in t || "heat equation" in t || "wave equation" in t ||
                "laplace equation" in t -> "pde"
            "differential equations" in t || "ode" in t -> "ode"
            "integral" in t || "integration" in t -> "integrals"
            "vector" in t || "green theorem" in t -> "vector"
            "optimization" in t || "minimization" in t -> "optimization"
            "series" in t -> "series"
            else -> t.substringBefore("·").trim().take(70)
        }
    }

    fun choose(
        all: List<Item>,
        completedInCycle: Set<String>,
        lastSolvedId: String?,
        lastSolvedFamily: String?,
        random: Random
    ): Item {
        require(all.isNotEmpty()) { "No questions available at this difficulty" }
        require(all.map { it.id }.distinct().size == all.size) {
            "Question IDs must be unique"
        }

        // Never repeat a completed ID if an unused question still exists.
        val unseen = all.filter { it.id !in completedInCycle }
        val eligible = if (unseen.isEmpty()) all else unseen

        // Exclude the immediately previous question even at a cycle boundary.
        val differentId = eligible.filter { it.id != lastSolvedId }
        val candidates = differentId.ifEmpty { eligible }

        // Prefer variety across subject families, without defeating ID history.
        val differentFamily = candidates.filter { it.family != lastSolvedFamily }
        val diverse = differentFamily.ifEmpty { candidates }
        return diverse[random.nextInt(diverse.size)]
    }
}

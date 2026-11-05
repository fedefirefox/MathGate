package dev.mathgate

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class QuestionSelectionTest {
    private val options = (1..18).map {
        QuestionSelection.Item("S%02d".format(it),
            when (it % 3) { 0 -> "integrals"; 1 -> "complex"; else -> "pde" })
    }

    @Test fun allEighteenSeenBeforeRepetition() {
        val completed = mutableSetOf<String>()
        var previous: QuestionSelection.Item? = null
        repeat(18) {
            val next = QuestionSelection.choose(
                options, completed, previous?.id, previous?.family,
                Random(it + 1241)
            )
            assertTrue(completed.add(next.id))
            previous = next
        }
        assertEquals(18, completed.size)
        assertEquals(options.map { it.id }.toSet(), completed)
    }

    @Test fun nextCycleDoesNotRepeatLastQuestionImmediately() {
        val last = options[5]
        val selected = QuestionSelection.choose(
            options, options.map { it.id }.toSet(),
            last.id, last.family, Random(2)
        )
        assertNotEquals(last.id, selected.id)
        assertNotEquals(last.family, selected.family)
    }

    @Test fun categoryDiversifiesWhenUnseenAlternativesExist() {
        val next = QuestionSelection.choose(
            options, emptySet(), "S03", "integrals", Random(1)
        )
        assertNotEquals("integrals", next.family)
    }

    @Test fun noEarlyRepetitionEvenWhenSubjectsAreSame() {
        val choices = (1..6).map { QuestionSelection.Item("I$it", "integrals") }
        val chosen = QuestionSelection.choose(
            choices, setOf("I1", "I2", "I3", "I4", "I5"), "I5",
            "integrals", Random(3)
        )
        assertEquals("I6", chosen.id)
    }

    @Test fun singletonPoolCanRepeatOnlyWhenNecessary() {
        val only = QuestionSelection.Item("one", "integrals")
        val chosen = QuestionSelection.choose(
            listOf(only), setOf("one"), "one", "integrals", Random(0)
        )
        assertEquals(only, chosen)
    }

    @Test fun groupClassificationSeparatesIntegralAndResidue() {
        assertEquals("integrals", QuestionSelection.family("Standard integrals · Integration by parts"))
        assertEquals("complex", QuestionSelection.family("Residue theorem · Two poles"))
        assertEquals("pde", QuestionSelection.family("PDE · Fourier heat solution"))
        assertEquals("ode", QuestionSelection.family("ODE · Resonance"))
    }

    @Test fun impossibleEmptyOrDuplicateBankRejected() {
        try {
            QuestionSelection.choose(emptyList(), emptySet(), null, null, Random(0))
            fail("Expected invalid bank error")
        } catch (_: IllegalArgumentException) { }
        try {
            QuestionSelection.choose(
                listOf(QuestionSelection.Item("A", "x"), QuestionSelection.Item("A", "y")),
                emptySet(), null, null, Random(0)
            )
            fail("Expected duplicate-ID error")
        } catch (_: IllegalArgumentException) { }
    }
}

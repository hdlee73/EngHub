package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.*
import org.junit.Test

class ExampleSenseTest {
    private fun s(en: String, ko: String) = BilingualSentence(en, ko, "")
    private val tree = s("Tom planted a palm tree in his backyard.", "톰이 뒷마당에 야자 나무를 심었다.")
    private val hand = s("She held the coin in her palm.", "그녀는 손바닥에 동전을 쥐고 있었다.")
    private val verb = s("He palmed the ace.", "그는 에이스를 손에 감췄다.")
    private val meanings = "1. [명사] 손바닥, 손뼉\n2. [명사] 야자나무, 야자\n3. [동사] 손 안에 감추다"

    @Test fun stemsDropVerbEndingsAndSpaces() {
        assertEquals("감추", ExampleSense.stem("감추다"))
        assertEquals("공부", ExampleSense.stem("공부하다"))
        assertEquals("야자나무", ExampleSense.stem("야자 나무"))
        assertEquals("손바닥", ExampleSense.stem("손바닥(신체)"))
    }

    @Test fun senseWordsFollowTheListOrder() {
        val words = ExampleSense.senseWords(meanings)
        assertEquals(3, words.size)
        assertTrue("손바닥" in words[0])
        assertTrue("야자나무" in words[1])
    }

    @Test fun exampleOfTheFirstListedSenseComesFirst() {
        // The tree sentence is the shortest, but the first listed sense is the hand.
        val picked = ExampleSense.pick(listOf(tree, hand, verb), meanings)
        assertEquals(hand, picked[0])
        assertEquals(tree, picked[1])
    }

    @Test fun listedTreeSenseMakesTheTreeExampleEligible() {
        val picked = ExampleSense.pick(listOf(verb, tree), "1. [명사] 야자나무\n2. [명사] 손바닥")
        assertEquals(tree, picked[0])
    }

    @Test fun withoutAnyMatchTheOriginalOrderIsKept() {
        val picked = ExampleSense.pick(listOf(tree, hand, verb), "1. [명사] 자동차")
        assertEquals(listOf(tree, hand), picked)
    }

    @Test fun smallInputsPassThrough() {
        assertTrue(ExampleSense.pick(emptyList(), meanings).isEmpty())
        assertEquals(listOf(tree), ExampleSense.pick(listOf(tree), meanings))
    }

    @Test fun compoundInTheExampleSuppliesTheSenseItUses() {
        val dict = mapOf("palm tree" to "1. [명사] (식물) 야자나무.")
        val got = ExampleSense.compoundSenses(listOf(tree), "palm", "1. [명사] 손바닥, 손뼉") { dict[it] }
        assertEquals(listOf("[명사] 야자나무"), got)
    }

    @Test fun compoundIsIgnoredWhenKnownOrNotInTheTranslation() {
        val dict = mapOf("palm tree" to "1. [명사] 야자나무.", "palm reading" to "1. [명사] 손금")
        assertTrue(ExampleSense.compoundSenses(listOf(tree), "palm", "1. [명사] 야자나무", { dict[it] }).isEmpty())
        val other = s("She likes palm reading.", "그녀는 점을 좋아한다.")
        assertTrue(ExampleSense.compoundSenses(listOf(other), "palm", "", { dict[it] }).isEmpty())
        assertTrue(ExampleSense.compoundSenses(listOf(tree), "palm tree", "", { dict[it] }).isEmpty())
    }
}

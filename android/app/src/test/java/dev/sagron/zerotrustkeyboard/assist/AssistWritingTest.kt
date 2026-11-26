// SPDX-License-Identifier: AGPL-3.0-only
package dev.sagron.zerotrustkeyboard.assist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistWritingTest {
    private val proofread = AssistWriting.Task.PROOFREAD
    private val rewrite = AssistWriting.Task.REWRITE

    @Test fun theNamesAreTheOnesZtAssistKnows() {
        // The contract with AssistService.java in Zero-Trust-Assist.
        assertEquals("dev.sagron.zerotrustassist", AssistWriting.PACKAGE)
        assertEquals("dev.sagron.zerotrustassist.AssistService", AssistWriting.SERVICE)
        assertEquals(listOf("proofread", "rewrite"), AssistWriting.Task.entries.map { it.wire })
        assertEquals(listOf(2, 3), listOf(AssistWriting.MSG_ASK, AssistWriting.MSG_CANCEL))
        assertEquals(listOf(102, 103, 104), listOf(AssistWriting.MSG_CHUNK, AssistWriting.MSG_DONE, AssistWriting.MSG_FAILED))
    }

    @Test fun theCodesOfZtAssistAreToldApart() {
        assertEquals(AssistWriting.Failure.NOT_INSTALLED, AssistWriting.failure("not_installed"))
        // Another app under ZT Assist's name is as good as none.
        assertEquals(AssistWriting.Failure.NOT_INSTALLED, AssistWriting.failure("untrusted"))
        assertEquals(AssistWriting.Failure.NO_MODEL, AssistWriting.failure("no_model"))
        assertEquals(AssistWriting.Failure.LOAD_FAILED, AssistWriting.failure("load_failed"))
        assertEquals(AssistWriting.Failure.NEEDS_UPDATE, AssistWriting.failure("unsupported"))
        assertEquals(AssistWriting.Failure.STOPPED, AssistWriting.failure("stopped"))
        assertEquals(AssistWriting.Failure.FAILED, AssistWriting.failure("failed"))
        assertEquals(AssistWriting.Failure.FAILED, AssistWriting.failure("something new"))
        assertEquals(AssistWriting.Failure.FAILED, AssistWriting.failure(null))
    }

    @Test fun theKeyboardsOwnModelTriesOnlyWhenZtAssistCannot() {
        assertTrue(AssistWriting.Failure.NOT_INSTALLED.ownModelCanTry)
        assertTrue(AssistWriting.Failure.NO_MODEL.ownModelCanTry)
        assertTrue(AssistWriting.Failure.LOAD_FAILED.ownModelCanTry)
        assertTrue(AssistWriting.Failure.NEEDS_UPDATE.ownModelCanTry)
        // It was on it and something went wrong: no second wait.
        assertFalse(AssistWriting.Failure.STOPPED.ownModelCanTry)
        assertFalse(AssistWriting.Failure.FAILED.ownModelCanTry)
    }

    @Test fun aWholeAnswerIsAnAnswer() {
        val result = AssistWriting.result("I have a dog.", stopped = false, truncated = false)
        assertEquals("I have a dog.", (result as AssistWriting.Result.Answer).text)
    }

    @Test fun aStoppedAnswerDoesNotReplaceTheText() {
        val result = AssistWriting.result("I have", stopped = true, truncated = false)
        assertEquals(AssistWriting.Failure.STOPPED, (result as AssistWriting.Result.Failed).failure)
    }

    @Test fun anAnswerForAPartOfTheTextDoesNotReplaceTheWholeText() {
        // ZT Assist cut the text to what its model can take: the rest would be lost.
        val result = AssistWriting.result("I have a dog.", stopped = false, truncated = true)
        assertEquals(AssistWriting.Failure.FAILED, (result as AssistWriting.Result.Failed).failure)
    }

    @Test fun anEmptyAnswerIsAFailure() {
        assertTrue(AssistWriting.result("  \n", stopped = false, truncated = false) is AssistWriting.Result.Failed)
    }

    @Test fun proofreadIsCleanedLikeTheKeyboardsOwnModel() {
        assertEquals("I have a dog.", AssistWriting.clean(proofread, "i has a dog", "I have a dog."))
        assertEquals("I have a dog.", AssistWriting.clean(proofread, "i has a dog", "\"I have a dog.\""))
        // Spaces and line breaks around the text stay as they were.
        assertEquals("  I have a dog.\n", AssistWriting.clean(proofread, "  i has a dog\n", "I have a dog.\n"))
        assertEquals("אני הולך הביתה", AssistWriting.clean(proofread, "אני הולח הביתה", " אני הולך הביתה "))
        // The model answered the text instead of fixing it.
        assertNull(AssistWriting.clean(proofread, "how are you", "I am fine, thank you for asking! How can I help you today?"))
        assertNull(AssistWriting.clean(proofread, "i has a dog", ""))
        assertNull(AssistWriting.clean(proofread, "i has a dog", null))
    }

    @Test fun anAnswerInACodeBlockIsTakenOutOfIt() {
        assertEquals("I have a dog.", AssistWriting.clean(proofread, "i has a dog", "```\nI have a dog.\n```"))
        assertEquals("I have a dog.", AssistWriting.clean(proofread, "i has a dog", "```text\nI have a dog.\n```"))
        assertEquals("I have a dog.", AssistWriting.clean(rewrite, "i has a dog", "```I have a dog.```"))
        // A first line that is part of the text is not taken for the block's label.
        assertEquals("Hello\nworld", AssistWriting.clean(rewrite, "hello\nworld", "```Hello\nworld```"))
        // Marks that are part of the text stay.
        assertEquals("Use ``` for code", AssistWriting.clean(proofread, "use ``` for code", "Use ``` for code"))
    }

    @Test fun aRewriteMayBeShorterOrLonger() {
        val long = "so basically what i wanted to say is that i think we should maybe meet tomorrow if that works"
        assertEquals(
            "Can we meet tomorrow, if that works?",
            AssistWriting.clean(rewrite, long, "Can we meet tomorrow, if that works?"),
        )
        assertEquals("That works for me.", AssistWriting.clean(rewrite, "ok", "That works for me."))
        assertEquals(" נתראה מחר בבוקר.\n", AssistWriting.clean(rewrite, " נתראה מחר בבוקר\n", "«נתראה מחר בבוקר.»"))
    }

    @Test fun aRewriteThatIsAnotherTextIsDropped() {
        val long = "so basically what i wanted to say is that i think we should maybe meet tomorrow if that works"
        // Almost nothing left of it.
        assertNull(AssistWriting.clean(rewrite, long, "Ok."))
        // An essay about a line.
        assertNull(AssistWriting.clean(rewrite, "see you tomorrow", "Sure! Here is a rewritten version of your text: ".repeat(3)))
        assertNull(AssistWriting.clean(rewrite, "see you tomorrow", "\"\""))
        assertNull(AssistWriting.clean(rewrite, "see you tomorrow", "   "))
    }

    @Test fun theLongestTextIsTheSameAsForTheKeyboardsOwnModel() {
        assertEquals(1500, AssistWriting.MAX_CHARS)
    }
}

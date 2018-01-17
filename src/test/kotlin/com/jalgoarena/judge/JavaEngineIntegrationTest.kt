package com.jalgoarena.judge

import com.google.common.io.Resources
import com.jalgoarena.config.TestApplicationConfiguration
import com.jalgoarena.data.ProblemsRepository
import com.jalgoarena.domain.JudgeRequest
import com.jalgoarena.domain.StatusCode
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.rules.SpringClassRule
import org.springframework.test.context.junit4.rules.SpringMethodRule
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@RunWith(JUnitParamsRunner::class)
@ContextConfiguration(classes = arrayOf(TestApplicationConfiguration::class))
open class JavaEngineIntegrationTest {

    companion object {

        private val client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.MINUTES)
                .readTimeout(2, TimeUnit.MINUTES)
                .build()

        @ClassRule
        @JvmField val SCR = SpringClassRule()

        @BeforeClass
        @JvmStatic fun setUp() {

            fun ping(url: String): Response {
                val apiServiceRequest = Request.Builder()
                        .url(url)
                        .build()
                return client.newCall(apiServiceRequest).execute()
            }

            val response = ping("https://jalgo-arena-api-pg.herokuapp.com/health")
            assertThat(response.isSuccessful).isTrue()
            val response2 = ping("https://jalgo-arena-problems-pg.herokuapp.com/health")
            assertThat(response2.isSuccessful).isTrue()
        }
    }

    @Rule
    @JvmField val springMethodRule = SpringMethodRule()

    @Inject
    lateinit var repository: ProblemsRepository

    @Inject
    lateinit var judgeEngine: JvmJudgeEngine

    private fun judgeSolution(problemId: String, solutionId: String, statusCode: StatusCode) {
        try {
            val problem = repository.find(problemId)
            val sourceCode = Resources.toString(Resources.getResource("$solutionId.java"), Charsets.UTF_8)

            val result = judgeEngine.judge(problem, JudgeRequest(sourceCode, "0-0", "java"))

            assertThat(result.statusCode).isEqualTo(statusCode.toString())
        } catch (e: Exception) {
            fail(e.message)
        }
    }

    @Test
    @Parameters("2-sum, TwoSum", "fib, FibFast", "stoi, MyStoi", "word-ladder, WordLadder")
    fun acceptsCorrectSolution(problemId: String, solutionId: String) {
        judgeSolution(problemId, solutionId, StatusCode.ACCEPTED)
    }

    @Test
    fun failsWithCompilationErrorWhenSourceCodeDoesNotCompile() {
        judgeSolution("fib", "FibBroken", StatusCode.COMPILE_ERROR)
    }

    @Test
    fun returnsWrongAnswerForIncorrectSolution() {
        judgeSolution("fib", "FibWrongAnswer", StatusCode.WRONG_ANSWER)
    }
}

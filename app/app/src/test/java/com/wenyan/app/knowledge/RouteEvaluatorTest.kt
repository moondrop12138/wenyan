package com.wenyan.app.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O7: 路由评测工具测试（决策门数学正确性） */
class RouteEvaluatorTest {

    @Test
    fun `precision and recall are macro averaged`() {
        val queries = listOf(
            RouteEvaluator.EvalQuery("q1", listOf("a")),
            RouteEvaluator.EvalQuery("q2", listOf("b", "c")),
        )
        val router: (String) -> List<String> = {
            when (it) {
                "q1" -> listOf("a", "x", "y")
                else -> listOf("b", "z", "w")
            }
        }
        val result = RouteEvaluator.evaluate(queries, router, k = 3)
        assertEquals(1.0 / 3.0, result.precisionAtK, 1e-9) // q1: 1/3, q2: 1/3
        assertEquals(0.75, result.recallAtK, 1e-9) // q1: 1/1, q2: 1/2
    }

    @Test
    fun `empty expected docs gives zero recall on that query`() {
        val result = RouteEvaluator.evaluate(
            listOf(RouteEvaluator.EvalQuery("q", emptyList())),
            { listOf("a") },
        )
        assertEquals(0.0, result.recallAtK, 1e-9)
        assertEquals(0.0, result.precisionAtK, 1e-9)
    }

    @Test
    fun `recall lift decision gate`() {
        val baseline = RouteEvaluator.EvalResult(0.0, 0.50, 1, emptyList())
        val candidate = RouteEvaluator.EvalResult(0.0, 0.65, 1, emptyList())
        assertTrue(RouteEvaluator.recallLiftPasses(baseline, candidate, 0.15))
        assertFalse(RouteEvaluator.recallLiftPasses(baseline, candidate.copy(recallAtK = 0.64), 0.15))
    }
}

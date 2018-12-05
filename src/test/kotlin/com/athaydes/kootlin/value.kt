import com.athaydes.kootlin.*
import com.athaydes.kootlin.io.BytesFile
import com.athaydes.kootlin.io.Print
import com.athaydes.kootlin.text.Join
import com.athaydes.kootlin.text.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExpressionsTest {

    @Test
    fun lazyVal() {
        assertEquals(10, Val { 10 }.lift)
        assertEquals("hello", Val { "hello" }.lift)

        val list = mutableListOf<Any>()
        val lazyVal = Val { list.add(1); list.toString() }

        // result is not evaluated yet
        assertEquals(emptyList<Any>(), list)

        // force result to be evaluated
        assertEquals(listOf(1).toString(), lazyVal.lift)

        // ensure result is evaluated only once
        assertEquals(listOf(1).toString(), lazyVal.lift)
        assertEquals(listOf(1).toString(), lazyVal.lift)

        // and the lazy function only ran once
        assertEquals(listOf(1), list)
    }

    @Test
    fun eagerVal() {
        val list = mutableListOf<Any>()
        val lazy = Val {
            list.add(1)
            5
        }

        // result is not evaluated yet
        assertEquals(emptyList<Any>(), list)

        val eager = Eager(lazy)

        // result is evaluated now
        assertEquals(listOf(1), list)

        // when we lift, the result is not evaluated again
        assertEquals(5, eager.lift)
        assertEquals(listOf(1), list)
    }

    @Test
    fun map() {
        val list = mutableListOf<Any>()

        val map = Mapping(Vals("hi")) { v -> list.add(1); "$v!!" }

        assertEquals(listOf("hi!!"), map.lift)
        assertEquals(listOf("hi!!"), map.lift)

        // evaluation occurred only once
        assertEquals(listOf(1), list)
    }

    @Test
    fun filter() {
        val list = EagerVal(1..10)

        assertEquals((1..5).toList(), Filter(list, { it < 6 }).lift)
        assertEquals((5..7).toList(), Filter(list, { it < 8 }, { it > 4 }).lift)
    }

    @Test
    fun ifExpressions() {
        assertEquals("hi", Trans(Val { true }) { if (it) "hi" else "bye" }.lift)
        assertEquals("bye", Trans(Val { false }) { if (it) "hi" else "bye" }.lift)
    }

    @Test
    fun `trans is lazy`() {
        val list = mutableListOf<Int>()
        val ifExpr = Trans(Val { true }) { if (it) list.add(2) else list.add(3) }
        assertTrue(list.isEmpty())

        // force result to be evaluated
        ifExpr.lift
        assertEquals(listOf(2), list)

        // evaluating result again does not re-run the expression
        ifExpr.lift
        assertEquals(listOf(2), list)
    }

    @Test
    fun types() {

        @Suppress("MoveLambdaOutsideParentheses")
        class Max<V : Comparable<V>>(first: Value<V>, others: MultiValue<V> = Empty) : Value<V>
        by Reduction(first, others, { a, b -> if (a > b) a else b })

        AssertEquals(Max(Val { 10 }, Vals(20)) to Val { 20 })

        data class Person(val name: String, val age: Int)

        val john = Val { Person("John", 25) }

        val johnsAge = Trans(john) { it.age }

        AssertEquals(johnsAge to Val { 25 })
    }

    @Test
    fun demo() {
        class Factorial(n: Val<Int>) : Value<Int>
        by Reduction(Val { 1 }, Val { 1..n.lift }, Int::times)

        val f = Filter(Vals(1, 5, 10, 25), { it > 0 })
        val g = Filter(f, { it < 10 })

        println("G is ${g.lift}")

        val x: MultiValue<Int> = Empty
        val y: MultiValue<Float> = Empty

        val ten = Val { 10 }
        val doubleTen = Trans(ten) { 2 * it }

        val oneToTen = Val { 1..10 }
        val twoToTwenty = Mapping(oneToTen) { 2 * it }
        val fiveToTen = Filter(oneToTen, { it >= 5 })
        val oneToTenSum: Value<Int> = Reduction(Val { 0 }, oneToTen, Int::plus)
        val text: Value<String> = Trans(oneToTen) {
            if (it.last == 10) "ten is the largest number"
            else "Something is wrong!"
        }

        @Suppress("DIVISION_BY_ZERO")
        val nAn = Try { 1 / 0 }

        val res: Result<Int, Throwable> = nAn.result

        val resultVal = when (res) {
            is Result.Success<Int> -> Val { res.lift }
            is Result.Failure<Throwable> -> Val { res.lift }
        }

        println("Dividing by zero gives " + resultVal.lift)

        AssertEquals(x to y,
                Val { 20 } to doubleTen,
                Filter(Val { 2..20 }, { it % 2 == 0 }) to twoToTwenty,
                Vals(5, 6, 7, 8, 9, 10) to fiveToTen,
                Val { 55 } to oneToTenSum,
                text to Val { "ten is the largest number" },
                nAn to Val { null })

        AssertEquals(
                Val { 1 } to Factorial(Val { 1 }),
                Val { 2 } to Factorial(Val { 2 }),
                Val { 6 } to Factorial(Val { 3 }),
                Val { 24 } to Factorial(Val { 4 }))

        val fileReader = BytesFile(File("build.gradle"))
        val fileContents = fileReader.run()

        val fileLength: Value<Int> = when (fileContents) {
            is Result.Success<ByteArray> -> Trans(fileContents) { it.size }
            is Result.Failure<Throwable> -> Val { -1 }
        }

        Print(Join(" ", Val { "build.gradle has length" }, fileLength, Val { "bytes" })).run()
    }

}

class AssertEquals(vararg pairs: Pair<Value<*>, Value<*>>) {
    init {
        val results = Mapping(Indexed(Vals(*pairs))) { (i, pair) ->
            Try { assertEquals("Iteration [$i] failed", pair.first.lift, pair.second.lift) }.result
        }
        val failures = FilterIs(Result.Failure::class.java, results)
        val failPrints = Mapping(failures) { Print(Line(EagerVal(it.lift))).run() }

        // lift the prints to materialize the whole thing and print the results
        failPrints.lift
    }
}

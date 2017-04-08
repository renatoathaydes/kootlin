package com.athaydes.kootlin

import kotlin.Boolean
import kotlin.Function
import kotlin.Lazy
import kotlin.LazyThreadSafetyMode
import kotlin.Nothing
import kotlin.Throwable
import kotlin.getValue
import kotlin.lazy as klazy

internal fun <T> lazy(initializer: () -> T): Lazy<T> = klazy(LazyThreadSafetyMode.NONE, initializer)

////////////////// abstract definitions //////////////////

/**
 * Container for a value.
 *
 * The [lift] property enables access to the value and is usually computed lazily.
 */
interface Value<out V> {
    val lift: V
}

/**
 * A [Value] containing 0..n values.
 */
typealias MultiValue<V> = Value<Iterable<V>>

/**
 * A [Value] that may or may not contain a value.
 */
typealias Maybe<V> = Value<V?>

/**
 * A [Boolean] [Value].
 */
typealias Predicate = Value<Boolean>

////////////////// concrete types //////////////////

// pure value wrappers

class Val<out V>(eval: () -> V) : Function<V>, Value<V> {
    override val lift by lazy { eval() }
}

class EagerVal<out V>(override val lift: V) : Value<V>

class Eager<out V>(value: Value<V>) : Value<V> {
    override val lift = value.lift
}

object Empty : MultiValue<Nothing> {
    override val lift = emptyList<Nothing>()
}

class Vals<out V>(vararg values: V) : MultiValue<V> {
    override val lift: Iterable<V> = object : Iterable<V> {
        override fun iterator() = values.iterator()
    }
}

// single value transformers

sealed class Result<out V, out E> : Maybe<V> {

    abstract val error: E?

    data class Success<out V>(override val lift: V) : Result<V, Nothing>() {
        override val error = null
    }

    data class Failure<out E : Throwable>(override val error: E) : Result<Nothing, E>() {
        override val lift: Nothing? = null
    }
}

class Try<out V>(action: () -> V) : Maybe<V> {
    val result: Result<V, Throwable> by lazy {
        try {
            Result.Success(action())
        } catch (e: Throwable) {
            Result.Failure(e)
        }
    }
    override val lift: V? by lazy { result.lift }
}

class Trans<F, T>(value: Value<F>, transform: (F) -> T) : Value<T> {
    override val lift by lazy { transform(value.lift) }
}

class Cond<V>(value: Value<V>, check: (V) -> Boolean) : Value<Boolean>
by Trans(value, check)

class If<out V>(predicate: Predicate, onTrue: () -> V, onFalse: () -> V) : Value<V> {
    override val lift by lazy { if (predicate.lift) onTrue() else onFalse() }
}

class IfIs<out V>(type: Class<V>, value: Value<*>) : Maybe<V>
by If(Val { type.isInstance(value.lift) }, { type.cast(value) }, { null })

class MultiVal<T>(vararg values: Value<T>) : MultiValue<T> {
    override val lift by lazy { values.map { it.lift } }
}

// multi-value transformers

class Map<in F, out T>(value: MultiValue<F>, transform: (F) -> T) : MultiValue<T> {
    override val lift by lazy { value.lift.map(transform) }
}

class Filter<out V>(value: MultiValue<V>, vararg predicates: (V) -> Boolean) : MultiValue<V>
by Reduction(value, Vals(*predicates), { v, p -> v.filter(p) })

class FilterIs<out V>(type: Class<V>, value: MultiValue<*>) : MultiValue<V>
by Map(Filter(value, type::isInstance), type::cast)

class Reduction<out V, out T>(neutral: Value<V>, values: MultiValue<T>, operation: (V, T) -> V) : Value<V> {
    override val lift by lazy {
        var current: V = neutral.lift
        val items = values.lift.iterator()
        while (items.hasNext()) {
            current = operation(current, items.next())
        }
        current
    }
}

class Indexed<out V>(values: MultiValue<V>) : MultiValue<IndexedValue<V>>
by Map(values, { var i = 0; { item: V -> IndexedValue(i++, item) } }())

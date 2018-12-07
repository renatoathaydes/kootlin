package com.athaydes.kootlin2

sealed class Val<out V> {

    abstract val items: Iterable<V>

    abstract fun <T> map(transform: (V) -> T): Val<T>

    abstract fun <T> reduce(initial: T, operator: (T, V) -> T): One<T>

    object None : Val<Nothing>() {
        override val items = emptyList<Nothing>()

        override fun <T> map(transform: (Nothing) -> T) = None

        override fun <T> reduce(initial: T, operator: (T, Nothing) -> T) = One { initial }
    }

    class One<V>(value: () -> V) : Val<V>() {
        val lift: V by lazy(value)

        override val items by lazy { listOf(lift) }

        override fun <T> map(transform: (V) -> T) = One { transform(lift) }

        override fun <T> reduce(initial: T, operator: (T, V) -> T) = One {
            operator(initial, lift)
        }
    }

    class Many<V>(values: () -> Iterable<V>) : Val<V>() {
        override val items by lazy(values)

        override fun <T> map(transform: (V) -> T) = Many { items.map(transform) }

        override fun <T> reduce(initial: T, operator: (T, V) -> T) = One {
            items.fold(initial, operator)
        }
    }

    class OneToMany<V>(first: () -> V, tail: () -> Iterable<V>) : Val<V>() {
        val first: V by lazy(first)
        val tail: Iterable<V> by lazy(tail)

        override val items by lazy { listOf(first()) + tail() }

        override fun <T> map(transform: (V) -> T) =
                OneToMany({ transform(first) }) { tail.map(transform) }

        override fun <T> reduce(initial: T, operator: (T, V) -> T) = One {
            items.fold(initial, operator)
        }
    }

}

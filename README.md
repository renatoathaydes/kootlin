# Kootlin

> Fundamentalist OOP

Kootlin is a pure Object-Oriented language based on Kotlin.
 
Kootlin does not have functions except for one-line lambdas used to modify raw, *lifted* values within the safety
of a value container such as `Map` (an object that maps items from a multi-value to another) or 
`Trans` (an object that contains a single, transformed `Value`).

You must not use raw values anywhere else. They are dangerous because they are full of inconveniences such as requiring
actual memory in the heap which you cannot lift only when required (as opposed to lazy, economical `Value`s).

Almost everything in Kootlin is a `Value<T>` (the only exception is the `IO` type).

For convenience, Kootlin defines the following `typealias` that are used throughout the standard library:

* `typealias MultiValue<V> = Value<Iterable<V>>`
* `typealias MultiValue<V> = Value<Iterable<V>>`
* `typealias Maybe<V> = Value<V?>`

And that forms the basis of everything else.

## Using Values

A `Value` in Kootlin can be created with the `Val` object:

```kotlin
val ten = Val { 10 }
```

A `MultiValue` is just a `Val` with many elements:

```kotlin
val oneToTen = Val { 1..10 }
```

The `Empty` object is a `MultiValue` of anything:

```kotlin
val x : MultiValue<Int> = Empty
val y : MultiValue<Float> = Empty
```

Kotlin `Array`s need to use `Vals` (and the spread operator `*`),
otherwise they get wrapped into `Value<Array<T>>` instead of `MultiValue<T>`:

```kotlin
val kotlinArray = arrayOf(1, 2, 3)
val values = Vals(*kotlinArray)
```

## Eagerness VS laziness

Kootlin is lazy by default, meaning it does not allocate memory pointlessly.
  
However, if for whatever reason you need to force eager evaluation, you can use the `EagerVal` object:

```kotlin
val eagerTen = EagerVal(10)
```

If you already have a `Value` and you want to force it to become eager, just wrap it into an `Eager` object:

```kotlin
val lazy = Val { 1..100 } // no allocation other than a single pointer
val eager = Eager(lazy) // now, there's a chunk of memory allocated
```

Of course, you could just `lift` the raw value, but then you wouldn't be able to use it anymore 
within the Kootlin framework.

```kotlin
// explicit type shown for clarity
val raw: Iterable<Int> = lazy.lift
```

## Transforming values

A transformed single value is created with the `Trans` object:

```kotlin
val ten = Val { 10 }
val doubleTen = Trans(ten, { 2 * it })
```

> Notice that in the example above, both `ten` and `doubleTen` are instances of `Value<Int>`.

`MultiValue`s are transformed with `Map` instead:

```kotlin
val oneToTen = Val { 1..10 }
val twoToTwenty = Map(oneToTen, { 2 * it })
```

> The fact that we need both `Trans` and `Map` instead of a single Object for both `Value` and `MultiValue`,
  like `Val`, is a current limitation of the language.

A selection of only certain items from a `MultiValue` can be described with the `Filter` object:

```kotlin
val fiveToTen = Filter(oneToTen, { it >= 5 })
```

A reduction from a `MultiValue` into another `MultiValue`, potentially with a different number of items, or even
into a single `Value`, is obtained with the `Reduce` object.

For example, the sum of all items of a `MultiValue` can be defined as:

```kotlin
// explicit type shown for clarity
val oneToTenSum: Value<Int> = Reduction(Val { 0 }, oneToTen, Int::plus)
```

Conditional transformation can be obtained with the `If` and `Cond` objects:

```kotlin
val text = If(Cond(oneToTen, { it.last == 10 }),
        { "ten is the largest number" },
        { "Something is wrong!" })
```

> `Cond` is just a `Trans<Boolean>` provided for convenience.

If the `Cond` evaluates to a `Value` of `true`, then the value of `text` will be `"ten is the largest number"`.
Otherwise, the second lambda will provide its value when lifted (notice that neither branch is evaluated until you
actually lift the value, unlike most languages where you always evaluate one or the other branch immediately).

## Error handling

Kootlin uses values to represent errors (as we said before, basically everything is a `Value`!).

If you have a value that, upon evaluation, may cause an error, you need a `Try` object:

```kotlin
val nAn = Try { 1 / 0 }
```

Lifting this value will not cause an `Exception`, it will just evaluate to `null`. That's why a `Try<V>` is an instance
of `Maybe<V>`.

If you need to inspect the error, you can obtain a `Result<V, Throwable>` from it, then check if it was a
`Success<V>` or `Failure<E>`:

```kotlin
val res: Result<Int, Throwable> = nAn.result

// explicit type shown for clarity
val resultVal = when (res) {
    is Result.Success<Int> -> Val<Int> { res.lift }
    is Result.Failure<*> -> Val<Throwable> { res.error }
}
```


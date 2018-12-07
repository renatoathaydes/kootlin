# Kootlin

> Fundamentalist OOP

Kootlin is a pure Object-Oriented language based on Kotlin.
 
Kootlin does not have functions except for one-line lambdas used to modify raw, *lifted* values within the safety
of a value container such as `Mapping` (an object that maps items from a multi-value to another) or 
`Trans` (an object that contains a single, transformed `Value`).

You must not use raw values anywhere else. They are dangerous because they are full of inconveniences such as requiring
actual memory in the heap which you cannot lift only when required (as opposed to lazy, economical `Value`s).

Almost everything in Kootlin is a `Value<T>` (the only exception is the `IO` type).

For convenience, Kootlin defines the following `typealias` that are used throughout the standard library:

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

`MultiValue`s are transformed with `Mapping` instead:

```kotlin
val oneToTen = Val { 1..10 }
val twoToTwenty = Mapping(oneToTen) { 2 * it }
```

> The fact that we need both `Trans` and `Mapping` instead of a single Object for both `Value` and `MultiValue`
  (like `Val` can be used for both), is a current limitation of the language.

A selection of only certain items from a `MultiValue` can be described with the `Filter` object:

```kotlin
val fiveToTen = Filter(oneToTen) { it >= 5 }
```

A reduction from a `MultiValue` into another `MultiValue`, potentially with a different number of items, or even
into a single `Value`, is obtained with the `Reduction` object.

For example, the sum of all items of a `MultiValue` can be defined as:

```kotlin
// explicit type shown for clarity
val oneToTenSum: Value<Int> = Reduction(Val { 0 }, oneToTen, Int::plus)
```

Conditional transformation can be obtained with the `Trans` object and an `if` expression:

```kotlin
val text: Value<String> = Trans(oneToTen) {
    if (it.last == 10) "ten is the largest number" 
    else "Something is wrong!" 
}
```

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

## Creating our own Values

### class by

In Kootlin, you can create your own `Value` using the `class by` construct:

```kotlin
class Max(first: Value<Int>, others: MultiValue<Int>) : Value<Int>
by Reduction(first, others, { a, b -> if (a > b) a else b })
```

> Notice that you always need to extend one of the fundamental Kootlin types in your definitions 
  (in this case, `Reduction`, so `Max` is just a special case of `Reduction`) for them to be of any use.

Normally, you would want your values to be generic, though, so you could write the above definition using generics
so your value could be used for any `Comparable` type:

```kotlin
class Max<V : Comparable<V>>(first: Value<V>, others: MultiValue<V> = Empty) : Value<V>
by Reduction(first, others, { a, b -> if (a > b) a else b })
```

The above example demonstrates that you can give a default value for type arguments, so that they do not need to be
provided when an instance of this type is being created (if not provided, `others` will be `Empty` in the example).

### data class

Groups of raw values can be defined with the `data class` construct:

```kotlin
data class Person(val name: String, val age: Int)
```

Notice that `data class` is used to define a fundamental Object in Kootlin! You don't want to have raw values like
this hanging around, so you should always wrap instances of them into safe `Value` instances:

```kotlin
val john = Val { Person("John", 25) }
```

Now, you can use all of Kootlin Objects with your data class! For example, to access its properties, use the
`Trans` Object:

```kotlin
val johnsAge = Trans(john) { it.age }
```

## IO type and side-effects

Kootlin `Value`s are supposed to be side-effect free and lazy. They only take memory once they are evaluated via `lift`,
and they are only evaluated once (subsequent calls to lift return the exact same value).

Therefore, things that change in the real world cannot be represented as `Value`. For these, we need the `IO` type.
 
The `IO` type has only one visible method that returns an `IOResult<V>`, plus an `action` that must be implemented by
concrete IO implementations:

```kotlin
typealias IOResult<V> = Result<V, Throwable>

abstract class IO<out V> {
    internal abstract val action: () -> V
    fun run(): IOResult<V>
}
```

Every time the `run` method is called, a new `IOResult` instance is created which may contain a `Value<V>` or a 
`Throwable`, depending on whether the IO operation succeeded or not. 

For example, the `IO` type can be used to read a binary file
(this is the actual definition of `kootlin.io.BytesFile`):

```kotlin
class BytesFile(file: File) : IO<ByteArray>() {
    override val action = file::readBytes
}
```

> Notice that, unlike calling `file.readBytes()` directly, `BytesFile` is safe to call and will not crash the program.

The following example illustrates how `IO` types can be used:

```kotlin
val fileReader = BytesFile(File("build.gradle"))
val fileContents = fileReader.run()

val fileLength: Value<Int> = when (fileContents) {
    is Result.Success<ByteArray> -> Trans(fileContents) { it.size }
    is Result.Failure<Throwable> -> Val { -1 }
}

Print(Join(" ", Val { "build.gradle has length" }, fileLength, Val { "bytes" })).run()
```

As you can see, `Print` is used to print out the results safely. `Print` is also an `IO` type.


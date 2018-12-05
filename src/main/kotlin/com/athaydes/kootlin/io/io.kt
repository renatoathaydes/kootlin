package com.athaydes.kootlin.io

import com.athaydes.kootlin.Result
import com.athaydes.kootlin.Try
import com.athaydes.kootlin.Value
import java.io.File
import java.nio.charset.Charset

typealias IOResult<V> = Result<V, Throwable>

abstract class IO<out V> {
    internal abstract val action: () -> V
    private val trier = { Try { action() } }

    fun run(): IOResult<V> = trier().result
}

class Print<out V>(private val value: Value<V>) : IO<V>() {
    override val action = { print(value.lift); value.lift }
}

class BytesFile(file: File) : IO<ByteArray>() {
    override val action = file::readBytes
}

class TextFile(private val file: File, val charset: Charset) : IO<String>() {
    override val action = { file.readText(charset) }
}

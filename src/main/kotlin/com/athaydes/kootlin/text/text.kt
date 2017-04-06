package com.athaydes.kootlin.text

import com.athaydes.kootlin.MultiValue
import com.athaydes.kootlin.Trans
import com.athaydes.kootlin.Value
import com.athaydes.kootlin.lazy

class Text(value: Value<*>) : Value<String>
by Trans(value, Any?::toString)

class Join(separator: String, vararg values: Value<*>) : Value<String> {
    override val lift by lazy { values.map { it.lift }.joinToString(separator) }
}

class Lines(value: Value<String>) : MultiValue<String>
by Trans(value, String::lines)

class Line(value: Value<*>) : Value<String>
by Trans(value, { v -> v.toString() + "\n" })
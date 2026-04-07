package com.beemovil.llm.local

import com.google.ai.edge.litertlm.Contents
import java.lang.reflect.Modifier

fun main() {
    val methods = Contents::class.java.declaredMethods
    for (m in methods) {
        if (Modifier.isStatic(m.modifiers) && Modifier.isPublic(m.modifiers)) {
            println("STATIC: \${m.name} returns \${m.returnType}")
        }
    }
}

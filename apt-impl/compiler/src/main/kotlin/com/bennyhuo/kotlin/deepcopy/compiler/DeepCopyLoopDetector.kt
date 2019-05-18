package com.bennyhuo.kotlin.deepcopy.compiler

import com.bennyhuo.aptutils.logger.Logger
import java.util.*

class DeepCopyLoopDetector(private val kTypeElement: KTypeElement) {

    private val typeStack = Stack<KTypeElement>()

    fun detect() {
        push(kTypeElement)
        dumpStack()
        kTypeElement.components
            // Only nullable types should be checked.
            .filter { it.type.nullable }
            .mapNotNull { it.typeElement }
            .filter { it.canDeepCopy }
            .forEach {
                push(it)
                detectNext(it)
                pop()
            }
        pop()
    }

    private fun detectNext(kTypeElement: KTypeElement) {
        dumpStack()
        kTypeElement.components.mapNotNull { it.typeElement }
            .filter { it.canDeepCopy }
            .forEach {
                push(it)
                detectNext(it)
                pop()
            }
    }

    private fun push(kTypeElement: KTypeElement) {
        kTypeElement.mark()
        typeStack.push(kTypeElement)
    }

    private fun pop() {
        typeStack.pop()
        kTypeElement.unmark()
    }

    private fun dumpStack() {
        Logger.warn("${kTypeElement.qualifiedName}: [${typeStack.joinToString { it.simpleName }}]")
    }
}
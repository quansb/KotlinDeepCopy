package com.bennyhuo.kotlin.deepcopy.compiler

class CopyLoopException(kTypeElement: KTypeElement)
    : Exception("Detect infinite copy loop. It will cause stack overflow to call ${kTypeElement.kotlinClassName}.deepCopy() in the runtime.") {
}
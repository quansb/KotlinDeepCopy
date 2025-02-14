package com.bennyhuo.kotlin.deepcopy.compiler.apt.meta

import com.bennyhuo.aptutils.logger.Logger
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.metadata.ClassName
import kotlinx.metadata.Flag
import kotlinx.metadata.Flags
import kotlinx.metadata.KmTypeVisitor
import kotlinx.metadata.KmVariance

open class KType(val flags: Flags, val typeParametersInContainer: List<KTypeParameter> = emptyList(), val variance: KmVariance = KmVariance.INVARIANT, val typeFlexibilityId: String? = null, val parent: KType? = null) :
    KmTypeVisitor() {

    private var name: ClassName = ""

    private var isReified = true

    val rawType: TypeName by lazy {
        when(isReified){
            true -> {
                val splits = name.split("/")
                assert(splits.size > 1)
                val packageName = splits.subList(0, splits.size - 1).joinToString(".")
                val simpleNames = splits.last().split("\\.").toTypedArray()
                val simpleName = simpleNames[0]
                val otherSimpleNames = simpleNames.sliceArray(1 until simpleNames.size)

                com.squareup.kotlinpoet.ClassName(packageName, simpleName, *otherSimpleNames).let {
                    if(Flag.Type.IS_NULLABLE(flags)){ it.copy(nullable = true) } else { it }
                }
            }
            false -> TypeVariableName(name)
        }
    }

    val type: TypeName by lazy {
        val rawType = this.rawType
        when {
            /*
             * For typealias, it will be expanded so that we can easily determine its original type
             * If this line is uncommented, we should handle its original type correctly when to
             * check Collection/Map type.
             */
            //abbreviatedTypeVisitor != null -> abbreviatedTypeVisitor!!.type
            rawType !is com.squareup.kotlinpoet.ClassName -> rawType
            typeParameters.isEmpty() -> rawType
            else -> rawType.parameterizedBy(*(typeParameters.map { it.wildcardTypeName }.toTypedArray())).let {
                if(Flag.Type.IS_NULLABLE(flags)){ it.copy(nullable = true) } else { it }
            }
        }
    }

    val wildcardTypeName by lazy {
        if (isReified) when(this.variance){
            KmVariance.INVARIANT -> type
            KmVariance.IN -> WildcardTypeName.consumerOf(type)
            KmVariance.OUT -> WildcardTypeName.producerOf(type)
        } else {
            if(name == "*") STAR
            else when (this.variance) {
                KmVariance.INVARIANT -> TypeVariableName(this.name)
                KmVariance.IN -> TypeVariableName(this.name, KModifier.IN)
                KmVariance.OUT -> TypeVariableName(this.name, KModifier.OUT)
            }
        }
    }

    private val typeParameters = ArrayList<KType>()

    private val upperBounds = ArrayList<KType>()

    private var abbreviatedTypeVisitor: KType? = null

    override fun visitAbbreviatedType(flags: Flags): KmTypeVisitor? {
        return KType(flags, typeParametersInContainer, parent = this).also {
            abbreviatedTypeVisitor = it
        }
    }

    override fun visitArgument(flags: Flags, variance: KmVariance): KmTypeVisitor? {
        return KType(flags, typeParametersInContainer, variance, parent = this).also {
            typeParameters += it
        }
    }

    override fun visitClass(name: ClassName) {
        super.visitClass(name)
        this.name = name
    }

    override fun visitFlexibleTypeUpperBound(flags: Flags, typeFlexibilityId: String?): KmTypeVisitor? {
        return KType(flags, typeParametersInContainer, variance, typeFlexibilityId, parent = this).also {
            upperBounds  += it
        }
    }

    override fun visitStarProjection() {
        super.visitStarProjection()
        typeParameters += KType(0, typeParametersInContainer, parent = this).also {
            it.visitClass("*")
            it.isReified = false
        }
    }

    override fun visitTypeAlias(name: ClassName) {
        super.visitTypeAlias(name)
        this.name = name
    }

    /**
     * Called if this is
     */
    override fun visitTypeParameter(id: Int) {
        super.visitTypeParameter(id)
        this.name = typeParametersInContainer[id].name
        this.isReified = false
    }

    override fun visitEnd() {
        super.visitEnd()
        //dump()
    }

    fun dump(){
        val parentInfo = sequence<KType> {
            var parent = this@KType.parent
            while (parent != null){
                yield(parent)
                parent = parent.parent
            }
        }.asIterable().reversed().joinToString(separator = ">") { "${it.name}@${it.hashCode()}" }
        Logger.warn("[$parentInfo=${this.name}@${this.hashCode()}] isReified =${this.isReified}")
    }
}
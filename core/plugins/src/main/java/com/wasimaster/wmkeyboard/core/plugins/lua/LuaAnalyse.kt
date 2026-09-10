package com.wasimaster.wmkeyboard.core.plugins.lua

import org.luaj.vm2.ast.Block
import org.luaj.vm2.ast.Chunk
import org.luaj.vm2.ast.Exp
import org.luaj.vm2.ast.FuncArgs
import org.luaj.vm2.ast.FuncBody
import org.luaj.vm2.ast.Name
import org.luaj.vm2.ast.Stat
import org.luaj.vm2.ast.SyntaxElement
import org.luaj.vm2.ast.TableConstructor
import org.luaj.vm2.ast.TableField

enum class LuaSymbolKind { LOCAL, LOCAL_FUNCTION, PARAMETER, FOR_VARIABLE, SELF }

/**
 * One local name: where it is declared, where it can be seen, and every place it
 * is read or assigned. Two locals that share a name are two symbols.
 */
class LuaSymbol internal constructor(
    val id: Int,
    val name: String,
    val kind: LuaSymbolKind,
    /** The name where it is declared, or null for the `self` a method has without writing it. */
    val declaration: LuaSpan?,
    /** Where the name means this symbol: from just after its declaration to the end of its block. */
    val scope: LuaSpan,
    /** The [LuaFunction.id] of the function it belongs to; 0 is the file itself. */
    val function: Int,
) {
    internal val readSpans = ArrayList<LuaSpan>()
    internal val writeSpans = ArrayList<LuaSpan>()

    /** Every place the value is read, in document order. */
    val reads: List<LuaSpan>
        get() = readSpans

    /** Every place it is assigned after its declaration, in document order. */
    val writes: List<LuaSpan>
        get() = writeSpans

    /** Whether the name means this symbol at [offset]. */
    fun isVisibleAt(offset: Int): Boolean = offset >= scope.start && offset <= scope.end

    override fun toString(): String = "LuaSymbol($name, $kind, $declaration)"
}

/** One place a global name is read or assigned. */
data class LuaGlobalUse(
    val name: String,
    val span: LuaSpan,
    val write: Boolean,
    /** The [LuaFunction.id] it is written in; 0 is the top of the file. */
    val function: Int,
)

enum class LuaFunctionKind {
    /** `function name()` for a name that is not local. */
    GLOBAL,

    /** `local function name()`, `local name = function`, or a function assigned to a name already local. */
    LOCAL,

    /** `function t.name()`, `t.name = function`, or `name = function` inside a table. */
    FIELD,

    /** `function t:name()`, which has `self`. */
    METHOD,

    /** A function with no name, passed or returned as a value. */
    ANONYMOUS,
}

class LuaFunction internal constructor(
    /** Numbered from 1 in the order the file is read. */
    val id: Int,
    /** The function this one is written inside; 0 is the file. */
    val parent: Int,
    /** The name as the file writes it, `render` or `M.sub:method`, or null for one with no name. */
    val name: String?,
    val kind: LuaFunctionKind,
    /** The name, or the `function` keyword of a function with no name. */
    val nameSpan: LuaSpan,
    /** From the name or the `function` keyword, whichever comes first, to the end of `end`. */
    val span: LuaSpan,
    val parameters: List<String>,
    val vararg: Boolean,
) {
    /** Whether a `return` directly inside this function gives back at least one value. */
    var returnsValue: Boolean = false
        internal set

    override fun toString(): String = "LuaFunction(${name ?: "<anonymous>"}, $kind, $parameters)"
}

/**
 * What the names in a script mean: every local with its scope and its uses,
 * every use of a global, and every function. There is one only for a script
 * that parses.
 */
class LuaAnalysis internal constructor(
    val symbols: List<LuaSymbol>,
    /** In document order. */
    val globals: List<LuaGlobalUse>,
    val functions: List<LuaFunction>,
    private val symbolByToken: IntArray,
    private val globalByToken: IntArray,
) {
    /** The local that [token] declares or uses, or null. */
    fun symbolAt(token: Int): LuaSymbol? = symbolByToken.getOrElse(token) { -1 }.takeIf { it >= 0 }?.let(symbols::get)

    /** The global use at [token], or null. */
    fun globalAt(token: Int): LuaGlobalUse? = globalByToken.getOrElse(token) { -1 }.takeIf { it >= 0 }?.let(globals::get)

    /** The function numbered [id], or null for 0, the file. */
    fun function(id: Int): LuaFunction? = functions.getOrNull(id - 1)

    /** The locals visible at [offset], only the innermost of each name. */
    fun visibleAt(offset: Int): List<LuaSymbol> {
        val byName = LinkedHashMap<String, LuaSymbol>()
        for (symbol in symbols) {
            if (!symbol.isVisibleAt(offset)) continue
            val held = byName[symbol.name]
            if (held == null || symbol.scope.start >= held.scope.start) byName[symbol.name] = symbol
        }
        return byName.values.toList()
    }

    /** The innermost function [offset] is inside, or null at the top of the file. */
    fun functionAt(offset: Int): LuaFunction? =
        functions.filter { offset >= it.span.start && offset < it.span.end }.maxByOrNull { it.span.start }

    /** Every global name the file reads or assigns. */
    val globalNames: Set<String> by lazy { globals.mapTo(LinkedHashSet()) { it.name } }
}

/**
 * Builds a [LuaAnalysis] from luaj's tree.
 *
 * The tree says what each statement is but not where most names are: a [Name]
 * has no position at all, and an expression's start is a token early as often as
 * not. Ends are exact, and so is the start of a statement. So a declared name is
 * found by walking forward from the keyword that begins its statement, a use by
 * the token its expression ends on, and the end of a block by [LuaBlocks]. Every
 * place is checked against the token's own text, and one that does not match is
 * left out rather than guessed.
 *
 * Scope follows Lua, not luaj's own `NameResolver`, which gets two things wrong
 * for an editor: `until` cannot see the locals of its `repeat`, and `self` in a
 * method is taken for a global.
 */
internal object LuaAnalyse {

    /**
     * Null when the tree is too deep to walk. An expression chained thousands of
     * operators long is valid Lua, and running out of stack on it must cost the
     * editor its analysis, not the app.
     */
    fun analyse(source: String, tokens: LuaTokens, blocks: LuaBlocks, chunk: Chunk): LuaAnalysis? = try {
        Resolver(source, tokens, blocks).run(chunk)
    } catch (overflow: StackOverflowError) {
        null
    }
}

private const val MAX_HEADER_TOKENS = 64
private const val MAX_PARAMETER_TOKENS = 512

private class Resolver(
    private val source: String,
    private val tokens: LuaTokens,
    private val blocks: LuaBlocks,
) {
    private val lines = LuaLines(source)
    private val symbols = ArrayList<LuaSymbol>()
    private val globals = ArrayList<LuaGlobalUse>()
    private val functions = ArrayList<LuaFunction>()
    private val symbolByToken = IntArray(tokens.size) { -1 }
    private val scopes = ArrayList<HashMap<String, Int>>()

    /** Where each `repeat` being walked ends: its locals are seen by its `until`. */
    private val repeatEnds = ArrayList<Int>()
    private var function = 0

    fun run(chunk: Chunk): LuaAnalysis {
        scopes += HashMap()
        block(chunk.block)
        for (symbol in symbols) {
            symbol.readSpans.sortBy { it.start }
            symbol.writeSpans.sortBy { it.start }
        }
        globals.sortBy { it.span.start }
        val globalByToken = IntArray(tokens.size) { -1 }
        globals.forEachIndexed { index, use ->
            val token = tokens.indexAt(use.span.start)
            if (token >= 0) globalByToken[token] = index
        }
        return LuaAnalysis(symbols, globals, functions, symbolByToken, globalByToken)
    }

    // ---- statements ------------------------------------------------------

    private fun block(block: Block?) {
        for (stat in block?.stats.orEmpty()) statement(stat as? Stat ?: continue)
    }

    private inline fun scoped(body: () -> Unit) {
        scopes += HashMap()
        body()
        scopes.removeAt(scopes.size - 1)
    }

    private fun statement(stat: Stat) {
        when (stat) {
            is Block -> scoped { block(stat) }
            is Stat.LocalAssign -> localAssign(stat)
            is Stat.LocalFuncDef -> localFunction(stat)
            is Stat.FuncDef -> functionStatement(stat)
            is Stat.Assign -> assign(stat)
            is Stat.FuncCallStat -> expression(stat.funccall)
            is Stat.NumericFor -> {
                expression(stat.initial)
                expression(stat.limit)
                expression(stat.step)
                loop(stat, listOf(stat.name), stat.block)
            }
            is Stat.GenericFor -> {
                expressions(stat.exps)
                loop(stat, stat.names.orEmpty().filterIsInstance<Name>(), stat.block)
            }
            is Stat.WhileDo -> {
                expression(stat.exp)
                scoped { block(stat.block) }
            }
            is Stat.RepeatUntil -> scoped {
                repeatEnds += endOffset(stat)
                block(stat.block)
                expression(stat.exp)
                repeatEnds.removeAt(repeatEnds.size - 1)
            }
            is Stat.IfThenElse -> {
                expression(stat.ifexp)
                scoped { block(stat.ifblock) }
                val conditions = stat.elseifexps.orEmpty()
                val branches = stat.elseifblocks.orEmpty()
                for (index in conditions.indices) {
                    expression(conditions[index] as? Exp)
                    scoped { block(branches.getOrNull(index) as? Block) }
                }
                stat.elseblock?.let { scoped { block(it) } }
            }
            is Stat.Return -> {
                val values = stat.values.orEmpty()
                if (values.isNotEmpty()) functions.getOrNull(function - 1)?.returnsValue = true
                expressions(values)
            }
            else -> Unit // break, goto and labels name nothing
        }
    }

    private fun localAssign(stat: Stat.LocalAssign) {
        val names = stat.names.orEmpty().filterIsInstance<Name>()
        val keyword = startToken(stat)?.takeIf { tokens.matches(it, "local") }
        val nameTokens = keyword?.let { nameList(it, names.size) }.orEmpty()
        // The values first: `local x = x` reads the x outside.
        stat.values.orEmpty().forEachIndexed { index, value ->
            val name = names.getOrNull(index)?.name
            val nameToken = nameTokens.getOrNull(index)?.takeIf { name != null && tokens.matches(it, name) }
            if (value is Exp.AnonFuncDef && name != null && nameToken != null) {
                functionBody(value.body, name, LuaFunctionKind.LOCAL, tokens.span(nameToken), method = false)
            } else {
                expression(value as? Exp)
            }
        }
        val scope = LuaSpan(endOffset(stat), blockEnd(keyword))
        names.forEachIndexed { index, name -> declare(name.name, LuaSymbolKind.LOCAL, nameTokens.getOrNull(index), scope) }
    }

    private fun localFunction(stat: Stat.LocalFuncDef) {
        val name = stat.name.name
        val keyword = startToken(stat)?.takeIf { tokens.matches(it, "local") }
        val nameToken = nextIs(nextIs(keyword, "function"), name)
        // Declared before its body, so it can call itself.
        val scopeStart = nameToken?.let(tokens::end) ?: endOffset(stat)
        declare(name, LuaSymbolKind.LOCAL_FUNCTION, nameToken, LuaSpan(scopeStart, blockEnd(keyword)))
        functionBody(stat.body, name, LuaFunctionKind.LOCAL, nameToken?.let(tokens::span), method = false)
    }

    private fun functionStatement(stat: Stat.FuncDef) {
        val funcName = stat.name
        val base = funcName.name.name
        val dots = funcName.dots.orEmpty().filterIsInstance<String>()
        val method = funcName.method
        val keyword = startToken(stat)?.takeIf { tokens.matches(it, "function") }
        val baseToken = nextIs(keyword, base)
        var last = baseToken
        for (part in dots) last = nextIs(nextIs(last, "."), part)
        if (method != null) last = nextIs(nextIs(last, ":"), method)
        val path = dots.isNotEmpty() || method != null
        val kind = when {
            method != null -> LuaFunctionKind.METHOD
            path -> LuaFunctionKind.FIELD
            lookup(base) >= 0 -> LuaFunctionKind.LOCAL
            else -> LuaFunctionKind.GLOBAL
        }
        // `function f()` assigns f; `function t.f()` only reads t.
        reference(base, baseToken, write = !path)
        val display = buildString {
            append(base)
            for (part in dots) append('.').append(part)
            if (method != null) append(':').append(method)
        }
        val nameSpan = if (baseToken != null && last != null) LuaSpan(tokens.start(baseToken), tokens.end(last)) else null
        functionBody(stat.body, display, kind, nameSpan, method = method != null)
    }

    private fun assign(stat: Stat.Assign) {
        val targets = stat.vars.orEmpty()
        stat.exps.orEmpty().forEachIndexed { index, value ->
            val target = targets.getOrNull(index) as? Exp
            val name = target?.let(::chainName) ?: (target as? Exp.FieldExp)?.name?.name
            val last = target?.let(::endToken)
            if (value is Exp.AnonFuncDef && name != null && last != null) {
                val first = chainStart(target) ?: tokens.start(last)
                val kind = when {
                    target is Exp.FieldExp -> LuaFunctionKind.FIELD
                    lookup(name) >= 0 -> LuaFunctionKind.LOCAL
                    else -> LuaFunctionKind.GLOBAL
                }
                functionBody(value.body, name, kind, LuaSpan(first, tokens.end(last)), method = false)
            } else {
                expression(value as? Exp)
            }
        }
        for (target in targets) {
            when (target) {
                is Exp.NameExp -> reference(target.name.name, endToken(target), write = true)
                is Exp.FieldExp -> expression(target.lhs)
                is Exp.IndexExp -> {
                    expression(target.lhs)
                    expression(target.exp)
                }
            }
        }
    }

    private fun loop(stat: Stat, names: List<Name>, body: Block?) {
        val keyword = startToken(stat)?.takeIf { tokens.matches(it, "for") }
        val nameTokens = keyword?.let { nameList(it, names.size) }.orEmpty()
        val end = endToken(stat)?.takeIf { tokens.matches(it, "end") }
        val opener = end?.let(blocks::openerOf)?.takeIf { it >= 0 }
        val scope = LuaSpan(opener?.let(tokens::end) ?: endOffset(stat), end?.let(tokens::start) ?: endOffset(stat))
        scoped {
            names.forEachIndexed { index, name ->
                declare(name.name, LuaSymbolKind.FOR_VARIABLE, nameTokens.getOrNull(index), scope)
            }
            block(body)
        }
    }

    // ---- expressions -----------------------------------------------------

    private fun expressions(list: List<Any?>?) {
        for (item in list.orEmpty()) expression(item as? Exp)
    }

    private fun expression(exp: Exp?) {
        when (exp) {
            null -> Unit
            is Exp.NameExp -> reference(exp.name.name, endToken(exp), write = false)
            is Exp.FieldExp -> expression(exp.lhs)
            is Exp.IndexExp -> {
                expression(exp.lhs)
                expression(exp.exp)
            }
            is Exp.FuncCall -> {
                expression(exp.lhs)
                arguments(exp.args)
            }
            is Exp.ParensExp -> expression(exp.exp)
            is Exp.BinopExp -> {
                expression(exp.lhs)
                expression(exp.rhs)
            }
            is Exp.UnopExp -> expression(exp.rhs)
            is Exp.AnonFuncDef -> functionBody(exp.body, null, LuaFunctionKind.ANONYMOUS, null, method = false)
            is TableConstructor -> table(exp)
            else -> Unit // constants and `...`
        }
    }

    private fun arguments(args: FuncArgs?) = expressions(args?.exps)

    private fun table(table: TableConstructor) {
        for (item in table.fields.orEmpty()) {
            val field = item as? TableField ?: continue
            expression(field.index)
            val key = field.name
            val value = field.rhs
            if (value is Exp.AnonFuncDef && key != null) {
                val keyToken = startToken(field)?.takeIf { tokens.matches(it, key) }
                functionBody(value.body, key, LuaFunctionKind.FIELD, keyToken?.let(tokens::span), method = false)
            } else {
                expression(value)
            }
        }
    }

    private fun functionBody(body: FuncBody?, name: String?, kind: LuaFunctionKind, nameSpan: LuaSpan?, method: Boolean) {
        body ?: return
        val end = endToken(body)?.takeIf { tokens.matches(it, "end") }
        val keyword = end?.let(blocks::openerOf)?.takeIf { it >= 0 && tokens.matches(it, "function") }
        val parameters = body.parlist?.names.orEmpty().filterIsInstance<Name>().map { it.name }
        val open = keyword?.let(::openParen)
        val parameterTokens = open?.let { nameList(it, parameters.size) }.orEmpty()
        val close = open?.let(::closeParen)
        val keywordSpan = keyword?.let(tokens::span)
        val start = listOfNotNull(keywordSpan?.start, nameSpan?.start).minOrNull() ?: 0
        val id = functions.size + 1
        functions += LuaFunction(
            id = id,
            parent = function,
            name = name,
            kind = kind,
            nameSpan = nameSpan ?: keywordSpan ?: LuaSpan(start, start),
            span = LuaSpan(start, end?.let(tokens::end) ?: start),
            parameters = parameters,
            vararg = body.parlist?.isvararg == true,
        )
        val outer = function
        function = id
        scoped {
            val scope = LuaSpan(close?.let(tokens::end) ?: start, end?.let(tokens::start) ?: start)
            if (method) declare("self", LuaSymbolKind.SELF, null, scope)
            parameters.forEachIndexed { index, parameter ->
                declare(parameter, LuaSymbolKind.PARAMETER, parameterTokens.getOrNull(index), scope)
            }
            block(body.block)
        }
        function = outer
    }

    // ---- names -----------------------------------------------------------

    private fun declare(name: String, kind: LuaSymbolKind, token: Int?, scope: LuaSpan): Int {
        val id = symbols.size
        val placed = token?.takeIf { isName(it, name) }
        symbols += LuaSymbol(id, name, kind, placed?.let(tokens::span), scope, function)
        if (placed != null) symbolByToken[placed] = id
        scopes[scopes.size - 1][name] = id
        return id
    }

    private fun lookup(name: String): Int {
        for (index in scopes.indices.reversed()) scopes[index][name]?.let { return it }
        return -1
    }

    private fun reference(name: String, token: Int?, write: Boolean) {
        val placed = token?.takeIf { isName(it, name) } ?: return
        val span = tokens.span(placed)
        val id = lookup(name)
        if (id >= 0) {
            val symbol = symbols[id]
            if (write) symbol.writeSpans += span else symbol.readSpans += span
            symbolByToken[placed] = id
        } else {
            globals += LuaGlobalUse(name, span, write, function)
        }
    }

    private fun isName(token: Int, name: String): Boolean =
        token >= 0 && tokens.kind(token) == LuaTokenKind.NAME && tokens.matches(token, name)

    /** `M.sub.run` as text, or null when the chain starts with something other than a name. */
    private fun chainName(exp: Exp): String? = when (exp) {
        is Exp.NameExp -> exp.name.name
        is Exp.FieldExp -> chainName(exp.lhs)?.let { "$it.${exp.name.name}" }
        else -> null
    }

    /** Where a chain like `M.sub.run` begins, or null when it starts with something other than a name. */
    private fun chainStart(exp: Exp): Int? = when (exp) {
        is Exp.NameExp -> endToken(exp)?.let(tokens::start)
        is Exp.FieldExp -> chainStart(exp.lhs)
        else -> null
    }

    // ---- places ----------------------------------------------------------

    /** The token a statement or a table field begins on, which luaj places exactly. */
    private fun startToken(element: SyntaxElement): Int? {
        if (element.beginLine < 1) return null
        val index = tokens.indexAt(lines.offset(element.beginLine, element.beginColumn.toInt() and 0xFFFF))
        return index.takeIf { it >= 0 && tokens.kind(it).isCode }
    }

    /** The token an element ends on. luaj places every end exactly. */
    private fun endToken(element: SyntaxElement): Int? {
        if (element.endLine < 1) return null
        val index = tokens.indexAt(lines.offset(element.endLine, element.endColumn.toInt() and 0xFFFF))
        return index.takeIf { it >= 0 && tokens.kind(it).isCode }
    }

    /** Just past an element, or the end of the source when it cannot be placed. */
    private fun endOffset(element: SyntaxElement): Int = endToken(element)?.let(tokens::end) ?: source.length

    /** Where the block holding [token] ends: at its closing keyword, after `until`'s condition, or at the end of the file. */
    private fun blockEnd(token: Int?): Int {
        token ?: return source.length
        val opener = blocks.enclosing(token)
        if (opener < 0) return source.length
        val closer = blocks.closerOf(opener)
        return when {
            closer < 0 -> source.length
            tokens.matches(closer, "until") -> repeatEnds.lastOrNull() ?: tokens.end(closer)
            else -> tokens.start(closer)
        }
    }

    /** The code token after [token] when it is exactly [word], else null. */
    private fun nextIs(token: Int?, word: String): Int? =
        token?.let(tokens::nextCode)?.takeIf { it >= 0 && tokens.matches(it, word) }

    /** Up to [count] names after [from], separated by commas: `local a, b`, `for k, v`, `(x, y`. */
    private fun nameList(from: Int, count: Int): List<Int> {
        val found = ArrayList<Int>(count)
        var at = tokens.nextCode(from)
        while (found.size < count && at >= 0 && tokens.kind(at) == LuaTokenKind.NAME) {
            found += at
            val comma = tokens.nextCode(at)
            if (comma < 0 || !tokens.matches(comma, ",")) break
            at = tokens.nextCode(comma)
        }
        return found
    }

    /** The `(` that opens a function's parameters, found by walking past its name from `function`. */
    private fun openParen(keyword: Int): Int? {
        var at = tokens.nextCode(keyword)
        var steps = 0
        while (at >= 0 && steps++ < MAX_HEADER_TOKENS) {
            if (tokens.matches(at, "(")) return at
            if (tokens.kind(at) != LuaTokenKind.NAME && !tokens.matches(at, ".") && !tokens.matches(at, ":")) return null
            at = tokens.nextCode(at)
        }
        return null
    }

    /** The `)` that closes the parameters opened at [open]. */
    private fun closeParen(open: Int): Int? {
        var at = tokens.nextCode(open)
        var steps = 0
        while (at >= 0 && steps++ < MAX_PARAMETER_TOKENS) {
            if (tokens.matches(at, ")")) return at
            at = tokens.nextCode(at)
        }
        return null
    }
}

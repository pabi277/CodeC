package com.codeci.ide.ui.services

/**
 * Phase 72.1 (2026-09-28) — the **Elements** tab, as a pure policy.
 *
 * The owner's shots show SPCK's five-tab strip with Elements among them, and he
 * chose the whole strip (*“the whole thing like the shots”*). Elements needs the
 * page's DOM — and CodeC has one honest way to reach it: the WebView itself runs
 * a small script and the answer is parsed back in Kotlin. No `addJavascriptInterface`
 * (nothing a previewed page can see or call), no DevTools socket, no new
 * dependency: `evaluateJavascript` carries each script out, and everything the
 * user reads is parsed here, on the host, where it is testable.
 *
 * The wire format is deliberately dull — `U+0001` inside a row, `\n` between
 * rows — because the alternative (a JSON DOM) would need a parser this repo does
 * not have a shareable one of, and because a DOM is text.
 *
 * Bounds: [MAX_NODES] nodes, [MAX_DEPTH] depth, [MAX_TEXT] characters per text
 * snippet, [MAX_ATTRIBUTES] attributes per node. A 10,000-element page must not
 * build a 10,000-row list in a phone panel.
 */
data class PreviewDomNode(
    val index: Int,
    val depth: Int,
    val tag: String,
    val id: String,
    val classes: String,
    val text: String,
)

data class PreviewDomAttribute(val name: String, val value: String)

data class PreviewNodeDetails(
    val tag: String,
    val box: String,
    val text: String,
    val attributes: List<PreviewDomAttribute>,
)

object PreviewInspectorPolicy {

    const val MAX_NODES = 300
    const val MAX_DEPTH = 12
    const val MAX_TEXT = 80
    const val MAX_ATTRIBUTES = 40
    const val MAX_HTML = 4000

    private const val UNIT = '\u0001'
    private const val FIELD = '\u0002'

    /**
     * Walk the document, number every element, keep the numbers in
     * `__codecDom` so a later script can point at one again, and return one
     * line per element.
     *
     * The index is what selection, highlighting and Copy HTML all travel by —
     * a node handle, not a CSS path, so a page that re-renders between two
     * scripts cannot make the panel act on the wrong element.
     */
    fun treeScript(maxNodes: Int = MAX_NODES, maxDepth: Int = MAX_DEPTH): String = script(
        "(function(){var out=[],all=[];",
        "function walk(el,d){if(all.length>=$maxNodes||d>$maxDepth)return;",
        "var i=all.length;all.push(el);",
        "var t=(el.tagName||'').toLowerCase();",
        "var id=(el.id||'')+'';",
        "var c=(typeof el.className==='string')?el.className:'';",
        "var txt='';",
        "try{if(el.children.length===0)txt=(el.textContent||'');}catch(x){}",
        "function clean(s){return (s+'').replace(/[\\u0000-\\u001f]/g,' ').substring(0,$MAX_TEXT);}",
        "out.push([i,d,clean(t),clean(id),clean(c),clean(txt)].join('\\u0001'));",
        "var kids=el.children;for(var k=0;k<kids.length&&all.length<$maxNodes;k++)walk(kids[k],d+1);}",
        "walk(document.documentElement,0);",
        "window.__codecDom=all;",
        "return out.join('\\n');})()",
    )

    /** Parse [treeScript]'s answer; malformed rows are skipped, never fatal. */
    fun parseTree(raw: String?): List<PreviewDomNode> =
        PreviewConsolePolicy.unquote(raw)
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split(UNIT)
                if (parts.size < 6) return@mapNotNull null
                val index = parts[0].toIntOrNull() ?: return@mapNotNull null
                val depth = parts[1].toIntOrNull() ?: return@mapNotNull null
                PreviewDomNode(
                    index = index,
                    depth = depth.coerceIn(0, MAX_DEPTH),
                    tag = parts[2].take(32),
                    id = parts[3].take(64),
                    classes = parts[4].take(128),
                    text = parts[5].take(MAX_TEXT),
                )
            }
            .take(MAX_NODES)
            .toList()

    /**
     * A CSS selector that finds [node] again from the page's own API — the
     * copy button's value. An `id` wins (it is unique by contract), then the
     * tag, then the tag with its classes.
     */
    fun selector(node: PreviewDomNode): String = when {
        node.id.isNotBlank() -> "#${node.id}"
        node.classes.isBlank() -> node.tag
        else -> node.tag + node.classes.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .joinToString("") { ".$it" }
    }

    /** Everything the details card shows about one selected node. */
    fun detailScript(index: Int): String = script(
        "(function(){var el=(window.__codecDom||[])[$index];",
        "if(!el)return '';",
        "function clean(s){return (s+'').replace(/[\\u0000-\\u001f]/g,' ');}",
        "var rows=[];var a=el.attributes||[];",
        "for(var i=0;i<a.length&&i<$MAX_ATTRIBUTES;i++){",
        "rows.push([clean(a[i].name).substring(0,32),clean(a[i].value).substring(0,120)].join('\\u0001'));}",
        "var box='';",
        "try{var r=el.getBoundingClientRect();",
        "box=Math.round(r.width)+'\\u00d7'+Math.round(r.height);}catch(x){}",
        "var txt='';",
        "try{txt=clean(el.innerText||'').substring(0,$MAX_TEXT);}catch(x){}",
        "return [clean((el.tagName||'').toLowerCase()),box,txt].join('\\u0002')+'\\n'+rows.join('\\n');})()",
    )

    fun parseDetails(raw: String?): PreviewNodeDetails? {
        val value = PreviewConsolePolicy.unquote(raw)
        if (value.isBlank()) return null
        val lines = value.split('\n')
        val head = lines.first().split(FIELD)
        val attributes = lines.drop(1).mapNotNull { line ->
            val parts = line.split(UNIT)
            if (parts.size < 2) return@mapNotNull null
            PreviewDomAttribute(parts[0].take(32), parts[1].take(120))
        }
        return PreviewNodeDetails(
            tag = head.getOrNull(0).orEmpty().take(32),
            box = head.getOrNull(1).orEmpty().take(32),
            text = head.getOrNull(2).orEmpty().take(MAX_TEXT),
            attributes = attributes.take(MAX_ATTRIBUTES),
        )
    }

    /**
     * Outline one node in the page and bring it into view; [index] null (or a
     * node the page no longer has) clears the previous outline. The outline is
     * inline style on the element itself, removed again by the next call —
     * nothing is added to the page's own stylesheets.
     */
    fun highlightScript(index: Int?): String = script(
        "(function(){var h=window.__codecHighlighted;",
        "if(h&&h.style){h.style.outline='';h.style.outlineOffset='';}",
        "window.__codecHighlighted=null;",
        "if($index<0)return 'off';",
        "var el=(window.__codecDom||[])[$index];",
        "if(!el)return 'off';",
        "el.style.outline='2px solid #4FC3F7';el.style.outlineOffset='1px';",
        "window.__codecHighlighted=el;",
        "try{el.scrollIntoView({block:'center'});}catch(x){}",
        "return 'on';})()",
    )

    /** One node's `outerHTML`, for the details card's Copy HTML button. */
    fun htmlScript(index: Int): String = script(
        "(function(){var el=(window.__codecDom||[])[$index];",
        "if(!el)return '';",
        "try{return String(el.outerHTML).substring(0,$MAX_HTML);}catch(x){return '';}})()",
    )

    /** One-line scripts: evaluated as-is, so no stray newlines. */
    private fun script(vararg lines: String): String = lines.joinToString(" ")
}

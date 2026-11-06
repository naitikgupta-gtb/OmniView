/**
 * Lightweight regex-based syntax highlighter (keywords/strings/comments/numbers).
 * A JS port of the same approach used in the Android app's CodeHighlighter.kt —
 * not a full tokenizer, but covers the common languages this viewer supports.
 */
const CodeHighlighter = (() => {
  const keywordsByLang = {
    py: new Set(["def","class","import","from","return","if","elif","else","for","while","try","except","with","as","lambda","None","True","False","self","in","not","and","or","yield","pass","break","continue"]),
    js: new Set(["function","const","let","var","return","if","else","for","while","class","extends","import","export","from","default","new","this","typeof","async","await","try","catch","null","undefined","true","false"]),
    ts: new Set(["function","const","let","var","return","if","else","for","while","class","interface","type","extends","implements","import","export","from","async","await","public","private","readonly"]),
    java: new Set(["public","private","protected","class","interface","extends","implements","static","final","void","return","if","else","for","while","new","import","package","try","catch","this","null","true","false"]),
    kt: new Set(["fun","val","var","class","object","interface","return","if","else","for","while","when","import","package","try","catch","this","null","true","false","companion","override","private","public"]),
    c: new Set(["int","float","double","char","void","return","if","else","for","while","struct","include","define"]),
    cpp: new Set(["int","float","double","char","void","return","if","else","for","while","class","struct","include","namespace","public","private","protected","new","delete","this"]),
  };
  const aliasExt = { jsx: "js", tsx: "ts", kts: "kt", h: "c", hpp: "cpp", cs: "java" };

  const colors = {
    keyword: "#c678dd",
    string: "#98c379",
    comment: "#7f848e",
    number: "#d19a66",
    text: "#abb2bf",
  };

  function esc(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function highlight(code, extension) {
    const ext = (aliasExt[extension] || extension || "").toLowerCase();
    const keywords = keywordsByLang[ext] || new Set();
    let out = "";
    let i = 0;
    const n = code.length;
    while (i < n) {
      const c = code[i];
      if (c === "/" && code[i + 1] === "/") {
        out += `<span style="color:${colors.comment}">${esc(code.slice(i))}</span>`;
        break;
      }
      if (c === "#" && ext === "py") {
        out += `<span style="color:${colors.comment}">${esc(code.slice(i))}</span>`;
        break;
      }
      if (c === '"' || c === "'") {
        const quote = c;
        let end = i + 1;
        while (end < n && code[end] !== quote) end++;
        end = Math.min(end + 1, n);
        out += `<span style="color:${colors.string}">${esc(code.slice(i, end))}</span>`;
        i = end;
        continue;
      }
      if (/[0-9]/.test(c)) {
        let end = i;
        while (end < n && /[0-9.]/.test(code[end])) end++;
        out += `<span style="color:${colors.number}">${esc(code.slice(i, end))}</span>`;
        i = end;
        continue;
      }
      if (/[A-Za-z_]/.test(c)) {
        let end = i;
        while (end < n && /[A-Za-z0-9_]/.test(code[end])) end++;
        const word = code.slice(i, end);
        if (keywords.has(word)) {
          out += `<span style="color:${colors.keyword}">${esc(word)}</span>`;
        } else {
          out += esc(word);
        }
        i = end;
        continue;
      }
      out += esc(c);
      i++;
    }
    return out;
  }

  return { highlight };
})();

(() => {
  "use strict";

  const homeScreen = document.getElementById("homeScreen");
  const viewerScreen = document.getElementById("viewerScreen");
  const viewerTitle = document.getElementById("viewerTitle");
  const viewerBody = document.getElementById("viewerBody");
  const backBtn = document.getElementById("backBtn");
  const chooseBtn = document.getElementById("chooseBtn");
  const pickerBox = document.getElementById("pickerBox");
  const fileInput = document.getElementById("fileInput");

  let activeObjectUrls = [];

  // ---------------- File type classification (mirrors the Android app's rules) ----------------
  const EXT = {
    image: new Set(["jpg","jpeg","png","webp","gif","bmp","svg","heic"]),
    video: new Set(["mp4","mkv","webm","3gp","mov","avi","m4v"]),
    audio: new Set(["mp3","wav","ogg","m4a","flac","aac"]),
    doc: new Set(["doc"]),
    docx: new Set(["docx"]),
    sheetLegacy: new Set(["xls"]),
    sheet: new Set(["xlsx"]),
    slideLegacy: new Set(["ppt"]),
    slide: new Set(["pptx"]),
    code: new Set(["py","js","ts","jsx","tsx","html","htm","css","scss","java","kt","kts","c","cpp","h","hpp","cs","go","rs","php","rb","swift","sh","sql","json","xml","yaml","yml","gradle","properties","dart","lua","r"]),
    text: new Set(["txt","md","log","ini","cfg"]),
    csv: new Set(["csv"]),
    pdf: new Set(["pdf"]),
  };

  function extOf(name) {
    const i = name.lastIndexOf(".");
    return i === -1 ? "" : name.slice(i + 1).toLowerCase();
  }

  function categoryOf(ext) {
    if (EXT.pdf.has(ext)) return "pdf";
    if (EXT.image.has(ext)) return "image";
    if (EXT.video.has(ext)) return "video";
    if (EXT.audio.has(ext)) return "audio";
    if (EXT.docx.has(ext)) return "docx";
    if (EXT.doc.has(ext)) return "doc-legacy";
    if (EXT.sheet.has(ext)) return "xlsx";
    if (EXT.sheetLegacy.has(ext)) return "xls-legacy";
    if (EXT.slide.has(ext)) return "pptx";
    if (EXT.slideLegacy.has(ext)) return "ppt-legacy";
    if (EXT.csv.has(ext)) return "csv";
    if (EXT.code.has(ext)) return "code";
    if (EXT.text.has(ext)) return "text";
    return "unknown";
  }

  // ---------------- Navigation ----------------
  function cleanupObjectUrls() {
    activeObjectUrls.forEach((u) => URL.revokeObjectURL(u));
    activeObjectUrls = [];
  }

  function showHome() {
    cleanupObjectUrls();
    viewerBody.innerHTML = "";
    viewerBody.className = "viewer-body";
    viewerScreen.classList.remove("active");
    homeScreen.style.display = "flex";
    fileInput.value = "";
  }

  function showViewer(file, category) {
    homeScreen.style.display = "none";
    viewerScreen.classList.add("active");
    viewerTitle.textContent = file.name;
    viewerBody.innerHTML = "";
    viewerBody.className = "viewer-body";
    renderers[category](file);
  }

  backBtn.addEventListener("click", showHome);
  chooseBtn.addEventListener("click", () => fileInput.click());
  pickerBox.addEventListener("click", (e) => {
    if (e.target === pickerBox) fileInput.click();
  });

  fileInput.addEventListener("change", () => {
    const file = fileInput.files && fileInput.files[0];
    if (!file) return;
    const ext = extOf(file.name);
    const category = categoryOf(ext);
    showViewer(file, category);
  });

  // ---------------- Shared helpers ----------------
  function centerMessage(text, withSpinner) {
    const div = document.createElement("div");
    div.className = "center-msg";
    if (withSpinner) {
      const sp = document.createElement("div");
      sp.className = "spinner";
      div.appendChild(sp);
    }
    const p = document.createElement("div");
    p.textContent = text;
    div.appendChild(p);
    return div;
  }

  function objectUrlFor(file) {
    const url = URL.createObjectURL(file);
    activeObjectUrls.push(url);
    return url;
  }

  /** Two-finger pinch zoom. setScale receives the new scale value on every move. */
  function attachPinchZoom(el, { min = 0.5, max = 4, getScale, setScale }) {
    let startDist = null, startScale = 1;
    const dist = (a, b) => Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY);
    el.addEventListener("touchstart", (e) => {
      if (e.touches.length === 2) { startDist = dist(e.touches[0], e.touches[1]); startScale = getScale(); }
    }, { passive: true });
    el.addEventListener("touchmove", (e) => {
      if (e.touches.length === 2 && startDist) {
        e.preventDefault();
        const d = dist(e.touches[0], e.touches[1]);
        setScale(Math.min(max, Math.max(min, startScale * (d / startDist))));
      }
    }, { passive: false });
    el.addEventListener("touchend", (e) => { if (e.touches.length < 2) startDist = null; }, { passive: true });
  }

  function zoomBar(onIn, onOut) {
    const bar = document.createElement("div");
    bar.style.cssText = "position:absolute;bottom:16px;right:16px;z-index:5;display:flex;flex-direction:column;background:rgba(0,0,0,0.65);border-radius:24px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.3);";
    const mk = (label, fn) => {
      const b = document.createElement("div");
      b.textContent = label;
      b.style.cssText = "width:44px;height:44px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:22px;";
      b.addEventListener("click", fn);
      return b;
    };
    bar.appendChild(mk("+", onIn));
    bar.appendChild(mk("\u2013", onOut));
    return bar;
  }

  // ---------------- Renderers ----------------
  const renderers = {
    pdf(file) {
      viewerBody.classList.add("on-grey");
      viewerBody.appendChild(centerMessage("Loading PDF\u2026", true));
      const reader = new FileReader();
      reader.onload = async () => {
        try {
          pdfjsLib.GlobalWorkerOptions.workerSrc = "lib/pdf.worker.min.js";
          const pdf = await pdfjsLib.getDocument({ data: new Uint8Array(reader.result) }).promise;
          viewerBody.innerHTML = "";
          const content = document.createElement("div");
          content.style.cssText = "padding:12px;";
          viewerBody.appendChild(content);

          const availW = Math.max(200, viewerBody.clientWidth - 24);
          const outputScale = Math.min(3, Math.min(window.devicePixelRatio || 1, 2) * 1.5);
          const holders = [];

          // Placeholders with the right aspect ratio first (cheap), pages draw lazily as they scroll near.
          for (let i = 1; i <= pdf.numPages; i++) {
            const page = await pdf.getPage(i);
            const vp = page.getViewport({ scale: 1 });
            const cssScale = availW / vp.width;
            const holder = document.createElement("div");
            holder.className = "pdf-page";
            holder.style.width = availW + "px";
            holder.style.height = Math.round(vp.height * cssScale) + "px";
            content.appendChild(holder);
            holders.push({ holder, page, cssScale, drawn: false });
          }

          async function draw(item) {
            if (item.drawn) return;
            item.drawn = true;
            const vp = item.page.getViewport({ scale: item.cssScale * outputScale });
            const canvas = document.createElement("canvas");
            canvas.width = Math.floor(vp.width);
            canvas.height = Math.floor(vp.height);
            item.holder.appendChild(canvas);
            await item.page.render({ canvasContext: canvas.getContext("2d"), viewport: vp }).promise;
          }

          const io = new IntersectionObserver((entries) => {
            entries.forEach((en) => {
              if (en.isIntersecting) draw(holders.find((h) => h.holder === en.target));
            });
          }, { root: viewerBody, rootMargin: "800px 0px" });
          holders.forEach((h) => io.observe(h.holder));

          let scale = 1;
          content.style.zoom = scale;
          viewerBody.appendChild(zoomBar(
            () => { scale = Math.min(4, scale * 1.2); content.style.zoom = scale; },
            () => { scale = Math.max(0.5, scale / 1.2); content.style.zoom = scale; }
          ));
          attachPinchZoom(viewerBody, {
            getScale: () => scale,
            setScale: (v) => { scale = v; content.style.zoom = scale; },
            min: 0.5, max: 4,
          });
        } catch (err) {
          viewerBody.innerHTML = "";
          viewerBody.appendChild(centerMessage("Could not open this PDF: " + (err && err.message ? err.message : err)));
        }
      };
      reader.readAsArrayBuffer(file);
    },

    image(file) {
      viewerBody.classList.add("on-grey");
      const wrap = document.createElement("div");
      wrap.className = "img-view";
      const img = document.createElement("img");
      img.src = objectUrlFor(file);
      wrap.appendChild(img);
      viewerBody.appendChild(wrap);

      let scale = 1;
      attachPinchZoom(wrap, {
        getScale: () => scale,
        setScale: (s) => { scale = s; img.style.transform = `scale(${scale})`; },
      });
    },

    video(file) {
      const wrap = document.createElement("div");
      wrap.className = "media-view";
      const v = document.createElement("video");
      v.src = objectUrlFor(file);
      v.controls = true;
      v.playsInline = true;
      v.addEventListener("error", () => {
        wrap.appendChild(centerMessage("This video format can't be played on this device."));
      });
      wrap.appendChild(v);
      viewerBody.appendChild(wrap);
    },

    audio(file) {
      const wrap = document.createElement("div");
      wrap.className = "media-view";
      const badge = document.createElement("div");
      badge.className = "audio-badge";
      badge.innerHTML = '<svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="#ffb224" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>';
      wrap.appendChild(badge);
      const a = document.createElement("audio");
      a.src = objectUrlFor(file);
      a.controls = true;
      a.addEventListener("error", () => {
        wrap.appendChild(centerMessage("This audio format can't be played on this device."));
      });
      wrap.appendChild(a);
      viewerBody.appendChild(wrap);
    },

    text(file) {
      const pre = document.createElement("div");
      pre.className = "text-view";
      viewerBody.appendChild(pre);
      viewerBody.appendChild(zoomBar(
        () => { fontSize = Math.min(32, fontSize + 1); pre.style.fontSize = fontSize + "px"; },
        () => { fontSize = Math.max(10, fontSize - 1); pre.style.fontSize = fontSize + "px"; }
      ));
      let fontSize = 14;
      const reader = new FileReader();
      reader.onload = () => { pre.textContent = reader.result; };
      reader.readAsText(file);
      attachPinchZoom(viewerBody, {
        getScale: () => fontSize,
        setScale: (s) => { fontSize = Math.round(s); pre.style.fontSize = fontSize + "px"; },
        min: 10, max: 32,
      });
    },

    code(file) {
      viewerBody.style.background = "#282c34";
      const container = document.createElement("div");
      container.className = "code-view";
      viewerBody.appendChild(container);
      viewerBody.appendChild(zoomBar(
        () => { fontSize = Math.min(32, fontSize + 1); container.style.fontSize = fontSize + "px"; },
        () => { fontSize = Math.max(10, fontSize - 1); container.style.fontSize = fontSize + "px"; }
      ));
      let fontSize = 14;
      container.style.fontSize = fontSize + "px";
      const ext = extOf(file.name);
      const reader = new FileReader();
      reader.onload = () => {
        const lines = reader.result.split(/\r\n|\r|\n/);
        const frag = document.createDocumentFragment();
        lines.forEach((line, i) => {
          const row = document.createElement("div");
          row.className = "code-line";
          const ln = document.createElement("span");
          ln.className = "ln";
          ln.textContent = String(i + 1).padStart(4, " ");
          const code = document.createElement("span");
          code.innerHTML = CodeHighlighter.highlight(line, ext) || "\u200b";
          row.appendChild(ln);
          row.appendChild(code);
          frag.appendChild(row);
        });
        container.appendChild(frag);
      };
      reader.readAsText(file);
      attachPinchZoom(viewerBody, {
        getScale: () => fontSize,
        setScale: (s) => { fontSize = Math.round(s); container.style.fontSize = fontSize + "px"; },
        min: 10, max: 32,
      });
    },

    csv(file) {
      const wrap = document.createElement("div");
      wrap.className = "table-wrap";
      viewerBody.appendChild(wrap);
      viewerBody.appendChild(zoomBar(
        () => { scale = Math.min(3, scale * 1.2); wrap.style.zoom = scale; },
        () => { scale = Math.max(0.5, scale / 1.2); wrap.style.zoom = scale; }
      ));
      let scale = 1;
      const reader = new FileReader();
      reader.onload = () => {
        const lines = reader.result.split(/\r\n|\r|\n/).filter((l) => l.length > 0);
        const table = document.createElement("table");
        table.className = "simple-table";
        lines.forEach((line, i) => {
          const tr = document.createElement("tr");
          line.split(",").forEach((cell) => {
            const td = document.createElement(i === 0 ? "th" : "td");
            td.textContent = cell.trim();
            tr.appendChild(td);
          });
          table.appendChild(tr);
        });
        wrap.appendChild(table);
      };
      reader.readAsText(file);
      attachPinchZoom(wrap, {
        getScale: () => scale,
        setScale: (s) => { scale = s; wrap.style.zoom = scale; },
      });
    },

    docx(file) {
      viewerBody.classList.add("on-grey");
      viewerBody.appendChild(centerMessage("Loading document\u2026", true));
      const reader = new FileReader();
      reader.onload = () => {
        const options = {
          styleMap: [
            "p[style-name='Title'] => h1.doc-title:fresh",
            "p[style-name='Subtitle'] => p.doc-subtitle:fresh",
          ],
        };
        mammoth.convertToHtml({ arrayBuffer: reader.result }, options)
          .then((result) => renderDocxPages(result.value || ""))
          .catch((err) => {
            viewerBody.innerHTML = "";
            viewerBody.appendChild(centerMessage("Could not open this document: " + err.message));
          });
      };
      reader.readAsArrayBuffer(file);
    },

    xlsx(file) {
      viewerBody.appendChild(centerMessage("Loading spreadsheet\u2026", true));
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const workbook = XLSX.read(new Uint8Array(reader.result), { type: "array" });
          renderXlsxSheets(workbook);
        } catch (err) {
          viewerBody.innerHTML = "";
          viewerBody.appendChild(centerMessage("Could not open this spreadsheet: " + err.message));
        }
      };
      reader.readAsArrayBuffer(file);
    },

    pptx(file) {
      viewerBody.classList.add("on-grey");
      viewerBody.appendChild(centerMessage("Loading slides\u2026", true));
      const reader = new FileReader();
      reader.onload = () => {
        viewerBody.innerHTML = "";
        const wrap = document.createElement("div");
        wrap.id = "pptxWrap";
        wrap.style.margin = "12px auto";
        viewerBody.appendChild(wrap);
        try {
          const w = Math.min(viewerBody.clientWidth - 24, 960);
          const previewer = PptxPreviewLib.init(wrap, { width: w });
          previewer.preview(reader.result).then(() => {
            let scale = 1;
            viewerBody.appendChild(zoomBar(
              () => { scale = Math.min(3, scale * 1.2); wrap.style.zoom = scale; },
              () => { scale = Math.max(0.5, scale / 1.2); wrap.style.zoom = scale; }
            ));
            attachPinchZoom(viewerBody, {
              getScale: () => scale,
              setScale: (s) => { scale = s; wrap.style.zoom = scale; },
              min: 0.5, max: 3,
            });
          }).catch((err) => {
            viewerBody.appendChild(centerMessage("Could not render this presentation: " + (err && err.message ? err.message : err)));
          });
        } catch (err) {
          viewerBody.appendChild(centerMessage("Could not render this presentation: " + err.message));
        }
      };
      reader.readAsArrayBuffer(file);
    },

    "doc-legacy": () => unsupportedLegacy("doc"),
    "xls-legacy": () => unsupportedLegacy("xls"),
    "ppt-legacy": () => unsupportedLegacy("ppt"),
    unknown(file) {
      viewerBody.appendChild(centerMessage(`Can't preview .${extOf(file.name) || "this"} files yet.`));
    },
  };

  function unsupportedLegacy(ext) {
    const box = document.createElement("div");
    box.className = "unsupported-box";
    box.textContent = `This is an old Office 97-2003 format (.${ext}). Please re-save it as .docx / .xlsx / .pptx (File, then Save As, and choose the newer format) and open it again.`;
    viewerBody.appendChild(box);
  }

  // ---- DOCX: lay the content out as fixed page cards, same look as the Android app ----
  function renderDocxPages(html) {
    viewerBody.innerHTML = "";
    const content = document.createElement("div");
    content.style.cssText = "padding:12px 0 40px;margin:0 auto;";
    viewerBody.appendChild(content);

    const staging = document.createElement("div");
    staging.innerHTML = html;
    const nodes = Array.from(staging.childNodes);
    if (nodes.length === 0) {
      content.appendChild(centerMessage("No readable text found in this file."));
      return;
    }

    const pageWidth = Math.min(viewerBody.clientWidth - 24, 800);
    const pageHeight = Math.round(pageWidth * 1.414);
    const pad = Math.round(pageWidth * 0.09);
    let scale = Math.min(1, (viewerBody.clientWidth - 12) / pageWidth);

    function stylePage(page) {
      page.className = "doc-page";
      page.style.cssText += `background:#fff;box-shadow:0 1px 6px rgba(0,0,0,0.3);box-sizing:border-box;margin:0 auto 12px auto;position:relative;overflow:hidden;font-size:14.67px;line-height:1.4;font-family:Calibri,Carlito,Roboto,"Segoe UI",Arial,sans-serif;color:#000;width:${pageWidth}px;height:${pageHeight}px;max-height:${pageHeight}px;padding:${pad}px;`;
    }

    let page = document.createElement("div");
    stylePage(page);
    content.appendChild(page);

    function fits() { return page.scrollHeight <= page.clientHeight + 1; }
    nodes.forEach((node) => {
      page.appendChild(node);
      if (!fits()) {
        if (page.childNodes.length === 1) {
          page.style.maxHeight = "none";
          page.style.height = "auto";
        } else {
          page.removeChild(node);
          page = document.createElement("div");
          stylePage(page);
          content.appendChild(page);
          page.appendChild(node);
        }
      }
    });

    content.style.zoom = scale;
    viewerBody.appendChild(zoomBar(
      () => { scale = Math.min(4, scale * 1.2); content.style.zoom = scale; },
      () => { scale = Math.max(0.4, scale / 1.2); content.style.zoom = scale; }
    ));
    attachPinchZoom(viewerBody, {
      getScale: () => scale,
      setScale: (s) => { scale = s; content.style.zoom = scale; },
      min: 0.4, max: 4,
    });
  }

  // ---- XLSX: sheet tabs + table, same as the Android app ----
  function renderXlsxSheets(workbook) {
    viewerBody.innerHTML = "";
    const tabs = document.createElement("div");
    tabs.style.cssText = "display:flex;overflow-x:auto;background:#f0f4f2;position:sticky;top:0;border-bottom:3px solid var(--sheet, #30a46c);z-index:5;";
    const sheetsEl = document.createElement("div");
    viewerBody.appendChild(tabs);
    viewerBody.appendChild(sheetsEl);

    let scale = 1;

    workbook.SheetNames.forEach((name, i) => {
      const tab = document.createElement("div");
      tab.textContent = name;
      tab.style.cssText = "padding:14px 20px;cursor:pointer;white-space:nowrap;font-size:15px;color:#557a68;font-weight:500;";
      tab.addEventListener("click", () => showSheet(i));
      tabs.appendChild(tab);

      const div = document.createElement("div");
      div.style.cssText = "display:none;padding:4px;";
      div.innerHTML = XLSX.utils.sheet_to_html(workbook.Sheets[name]);
      div.querySelectorAll("table").forEach((t) => { t.className = "simple-table"; });
      sheetsEl.appendChild(div);
    });

    function showSheet(i) {
      Array.from(tabs.children).forEach((t, idx) => {
        t.style.color = idx === i ? "#1b7a4c" : "#557a68";
        t.style.fontWeight = idx === i ? "700" : "500";
        t.style.borderBottom = idx === i ? "4px solid #30a46c" : "none";
      });
      Array.from(sheetsEl.children).forEach((s, idx) => { s.style.display = idx === i ? "block" : "none"; });
    }
    showSheet(0);

    viewerBody.appendChild(zoomBar(
      () => { scale = Math.min(3, scale * 1.2); sheetsEl.style.zoom = scale; },
      () => { scale = Math.max(0.5, scale / 1.2); sheetsEl.style.zoom = scale; }
    ));
    attachPinchZoom(sheetsEl, {
      getScale: () => scale,
      setScale: (s) => { scale = s; sheetsEl.style.zoom = scale; },
    });
  }

  // ---------------- Service worker (offline support) ----------------
  if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("sw.js").catch(() => {});
    });
  }
})();

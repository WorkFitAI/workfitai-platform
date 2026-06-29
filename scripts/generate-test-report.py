#!/usr/bin/env python3
"""
Generate a rich self-contained HTML dashboard from Maven Surefire + JaCoCo reports.
Replaces both the surefire text output and the JaCoCo HTML site.

Usage:
    python scripts/generate-test-report.py [--root <project-root>] [--output <path>] [--duration <seconds>]

Reads:
    services/*/target/surefire-reports/TEST-*.xml   (test results)
    services/*/target/site/jacoco/jacoco.xml         (coverage)
Writes:
    test-report.html  (default)
"""

import sys
import os
import glob
import argparse
from xml.etree import ElementTree as ET
from datetime import datetime
from pathlib import Path


# ─── Surefire parsing ─────────────────────────────────────────────────────────

def _parse_testsuite_el(el) -> dict:
    """Parse a single <testsuite> element into a suite dict."""
    suite: dict = {
        "name": el.get("name", "unknown"),
        "tests": int(el.get("tests", 0)),
        "failures": int(el.get("failures", 0)),
        "errors": int(el.get("errors", 0)),
        "skipped": int(el.get("skipped", 0)),
        "time": float(el.get("time", 0)),
        "cases": [],
    }
    for tc in el.findall("testcase"):
        case: dict = {
            "name": tc.get("name", ""),
            "classname": tc.get("classname", ""),
            "time": float(tc.get("time", 0)),
            "status": "passed",
            "message": "",
            "detail": "",
        }
        failure = tc.find("failure")
        error = tc.find("error")
        skipped = tc.find("skipped")
        if failure is not None:
            case["status"] = "failed"
            case["message"] = failure.get("message", "")
            case["detail"] = (failure.text or "").strip()
        elif error is not None:
            case["status"] = "error"
            case["message"] = error.get("message", "")
            case["detail"] = (error.text or "").strip()
        elif skipped is not None:
            case["status"] = "skipped"
            case["message"] = skipped.get("message", "")
        suite["cases"].append(case)
    return suite


def parse_surefire_xml(xml_path: str) -> dict | None:
    try:
        root = ET.parse(xml_path).getroot()
    except ET.ParseError:
        return None

    # pytest wraps results in <testsuites> (plural); Maven Surefire uses <testsuite> as root.
    # Normalize both formats to a single suite dict so the rest of the pipeline is unaffected.
    if root.tag == "testsuites":
        suite_els = root.findall("testsuite")
        if not suite_els:
            return None
        if len(suite_els) == 1:
            return _parse_testsuite_el(suite_els[0])
        # Multiple inner suites: merge counts and cases into one
        merged: dict = {
            "name": root.get("name") or suite_els[0].get("name", "unknown"),
            "tests": 0, "failures": 0, "errors": 0, "skipped": 0, "time": 0.0,
            "cases": [],
        }
        for el in suite_els:
            s = _parse_testsuite_el(el)
            merged["tests"] += s["tests"]
            merged["failures"] += s["failures"]
            merged["errors"] += s["errors"]
            merged["skipped"] += s["skipped"]
            merged["time"] += s["time"]
            merged["cases"].extend(s["cases"])
        return merged

    return _parse_testsuite_el(root)


# ─── JaCoCo parsing ───────────────────────────────────────────────────────────

def _counters(el) -> dict:
    out = {}
    for c in el.findall("counter"):
        t = c.get("type", "")
        missed = int(c.get("missed", 0))
        covered = int(c.get("covered", 0))
        total = missed + covered
        out[t] = {
            "missed": missed,
            "covered": covered,
            "total": total,
            "pct": round(covered / total * 100, 1) if total else None,
        }
    return out


def parse_jacoco_xml(xml_path: str) -> dict | None:
    try:
        root = ET.parse(xml_path).getroot()
    except ET.ParseError:
        return None

    report = {
        "counters": _counters(root),
        "packages": [],
    }
    for pkg in root.findall("package"):
        pkg_name = pkg.get("name", "").replace("/", ".")
        pkg_data = {
            "name": pkg_name,
            "counters": _counters(pkg),
            "classes": [],
        }
        for sf in pkg.findall("sourcefile"):
            pkg_data["classes"].append({
                "name": sf.get("name", ""),
                "counters": _counters(sf),
            })
        pkg_data["classes"].sort(key=lambda c: (
            c["counters"].get("LINE", {}).get("pct") or 100
        ))
        report["packages"].append(pkg_data)

    report["packages"].sort(key=lambda p: (
        p["counters"].get("LINE", {}).get("pct") or 100
    ))
    return report


# ─── Cobertura parsing (pytest-cov XML) ──────────────────────────────────────

def parse_cobertura_xml(xml_path: str) -> dict | None:
    """Parse pytest-cov Cobertura XML into the same shape as parse_jacoco_xml."""
    try:
        root = ET.parse(xml_path).getroot()
    except ET.ParseError:
        return None

    def _line_counters(line_els):
        total = len(line_els)
        covered = sum(1 for l in line_els if int(l.get("hits", 0)) > 0)
        missed = total - covered
        pct = round(covered / total * 100, 1) if total else None
        return {"LINE": {"missed": missed, "covered": covered, "total": total, "pct": pct}}

    all_lines = root.findall(".//line")
    counters = _line_counters(all_lines)

    branch_rate = float(root.get("branch-rate") or 0)
    if branch_rate:
        counters["BRANCH"] = {"missed": 0, "covered": 0, "total": 0,
                               "pct": round(branch_rate * 100, 1)}

    report: dict = {"counters": counters, "packages": []}

    for pkg_el in root.findall(".//package"):
        pkg_name = pkg_el.get("name", "").replace("/", ".")
        pkg_lines = pkg_el.findall(".//line")
        pkg_data = {
            "name": pkg_name,
            "counters": _line_counters(pkg_lines),
            "classes": [],
        }
        for cls_el in pkg_el.findall(".//class"):
            cls_name = cls_el.get("filename") or cls_el.get("name", "")
            cls_lines = cls_el.findall(".//line")
            pkg_data["classes"].append({
                "name": cls_name,
                "counters": _line_counters(cls_lines),
            })
        pkg_data["classes"].sort(
            key=lambda c: (c["counters"].get("LINE", {}).get("pct") or 100)
        )
        report["packages"].append(pkg_data)

    report["packages"].sort(
        key=lambda p: (p["counters"].get("LINE", {}).get("pct") or 100)
    )
    return report


# ─── Data collection ──────────────────────────────────────────────────────────

def collect_results(root_dir: str) -> dict:
    surefire_pattern = os.path.join(
        root_dir, "services", "*", "target", "surefire-reports", "TEST-*.xml"
    )
    services: dict[str, dict] = {}

    for xml_path in sorted(glob.glob(surefire_pattern)):
        parts = Path(xml_path).parts
        try:
            svc_idx = next(i for i, p in enumerate(parts) if p == "services") + 1
            svc_name = parts[svc_idx]
        except StopIteration:
            continue
        suite = parse_surefire_xml(xml_path)
        if suite is None:
            continue
        if svc_name not in services:
            services[svc_name] = {
                "name": svc_name,
                "tests": 0, "failures": 0, "errors": 0, "skipped": 0, "time": 0.0,
                "suites": [],
                "coverage": None,
            }
        svc = services[svc_name]
        svc["tests"] += suite["tests"]
        svc["failures"] += suite["failures"]
        svc["errors"] += suite["errors"]
        svc["skipped"] += suite["skipped"]
        svc["time"] += suite["time"]
        svc["suites"].append(suite)

    jacoco_pattern = os.path.join(
        root_dir, "services", "*", "target", "site", "jacoco", "jacoco.xml"
    )
    for jxml in glob.glob(jacoco_pattern):
        parts = Path(jxml).parts
        try:
            svc_idx = next(i for i, p in enumerate(parts) if p == "services") + 1
            svc_name = parts[svc_idx]
        except StopIteration:
            continue
        if svc_name in services:
            services[svc_name]["coverage"] = parse_jacoco_xml(jxml)

    # Cobertura XML fallback for Python services (e.g. recommendation-engine)
    cobertura_pattern = os.path.join(
        root_dir, "services", "*", "target", "site", "coverage.xml"
    )
    for cxml in glob.glob(cobertura_pattern):
        parts = Path(cxml).parts
        try:
            svc_idx = next(i for i, p in enumerate(parts) if p == "services") + 1
            svc_name = parts[svc_idx]
        except StopIteration:
            continue
        if svc_name in services and services[svc_name]["coverage"] is None:
            services[svc_name]["coverage"] = parse_cobertura_xml(cxml)

    for svc in services.values():
        svc["suites"].sort(
            key=lambda s: (0 if s["failures"] + s["errors"] > 0 else 1, s["name"])
        )
    return services


# ─── HTML helpers ─────────────────────────────────────────────────────────────

def esc(s: str) -> str:
    return (s or "").replace("&", "&amp;").replace("<", "&lt;") \
                    .replace(">", "&gt;").replace('"', "&quot;")


def fmt_time(seconds: float) -> str:
    if seconds >= 60:
        return f"{seconds / 60:.1f}m"
    if seconds >= 1:
        return f"{seconds:.2f}s"
    return f"{seconds * 1000:.0f}ms"


# Light-mode threshold colors: red / amber / green
def _cov_color(pct) -> str:
    if pct is None:
        return "#94a3b8"
    return "#dc2626" if pct < 50 else "#d97706" if pct < 80 else "#16a34a"


def big_cov_bar(pct, label="") -> str:
    if pct is None:
        return ""
    color = _cov_color(pct)
    return f"""<div class="cov-stat">
  <div class="cov-label">{esc(label)}</div>
  <div class="cov-main">
    <div class="cov-pct-big" style="color:{color}">{pct}%</div>
    <div class="cov-bar-track">
      <div class="cov-bar-fill" style="width:{pct}%;background:{color}"></div>
    </div>
  </div>
</div>"""


def cov_cell(ct: dict) -> str:
    pct = ct.get("pct")
    if pct is None:
        return '<td class="cov-no">&#8212;</td>'
    color = _cov_color(pct)
    frac = f'{ct["covered"]}/{ct["total"]}'
    fill = round(80 * pct / 100)
    return (
        f'<td>'
        f'<span class="cov-wrap">'
        f'<svg width="80" height="6" style="vertical-align:middle;border-radius:3px;overflow:hidden">'
        f'<rect width="80" height="6" fill="#e2e8f0"/>'   # light track
        f'<rect width="{fill}" height="6" fill="{color}"/>'
        f'</svg>'
        f' <span style="color:{color};font-weight:600;font-size:0.78rem">{pct}%</span>'
        f'</span>'
        f' <span class="cov-fraction">{frac}</span>'
        f'</td>'
    )


# ─── CSS (light mode) ─────────────────────────────────────────────────────────

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
:root{--bg:#f0f4f8;--surface:#ffffff;--border:#e2e8f0;--text:#1e293b;--muted:#64748b;--dim:#94a3b8;
  --green:#16a34a;--red:#dc2626;--yellow:#d97706;--blue:#2563eb;--orange:#c2410c}
html{scroll-behavior:smooth}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:var(--bg);color:var(--text);min-height:100vh;font-size:14px}

/* sticky header */
.topbar{position:sticky;top:0;z-index:100;background:#fffffff0;backdrop-filter:blur(12px);
  border-bottom:1px solid var(--border);padding:10px 24px;display:flex;align-items:center;gap:14px;flex-wrap:wrap;
  box-shadow:0 1px 4px #0000000f}
.topbar h1{font-size:0.95rem;font-weight:700;color:#0f172a;white-space:nowrap}
.topbar .ts{font-size:0.72rem;color:var(--muted);white-space:nowrap}
.search-wrap{position:relative;min-width:200px}
.search-wrap input{width:100%;background:#f8fafc;border:1px solid var(--border);border-radius:8px;
  padding:6px 28px 6px 30px;color:var(--text);font-size:0.78rem;outline:none}
.search-wrap input:focus{border-color:#3b82f6;box-shadow:0 0 0 3px #3b82f620}
.search-wrap .ico{position:absolute;left:9px;top:50%;transform:translateY(-50%);opacity:0.45;pointer-events:none}
#clear-search{display:none;position:absolute;right:8px;top:50%;transform:translateY(-50%);
  background:none;border:none;color:var(--muted);cursor:pointer;font-size:1rem;line-height:1}
.nav-pills{display:flex;gap:5px;flex-wrap:wrap}
.nav-pill{padding:3px 9px;border-radius:99px;font-size:0.7rem;font-weight:600;
  border:1px solid var(--border);color:var(--muted);text-decoration:none;transition:all 0.15s;white-space:nowrap}
.nav-pill.fail{border-color:#fca5a5;color:#dc2626;background:#fff0f0}
.nav-pill.pass{border-color:#86efac;color:#15803d;background:#f0fdf4}
.topbar-right{display:flex;gap:6px;margin-left:auto}
.btn-sm{padding:4px 11px;border-radius:7px;font-size:0.72rem;font-weight:600;cursor:pointer;
  border:1px solid var(--border);background:var(--surface);color:var(--muted);transition:all 0.15s}
.btn-sm:hover{border-color:#3b82f6;color:var(--blue);background:#eff6ff}

/* summary cards */
.summary{padding:18px 24px;display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:10px}
.stat{background:var(--surface);border-radius:10px;padding:14px 16px;border:1px solid var(--border);
  text-align:center;box-shadow:0 1px 3px #00000010}
.stat .v{font-size:1.9rem;font-weight:800;line-height:1}
.stat .l{font-size:0.65rem;color:var(--muted);margin-top:4px;text-transform:uppercase;letter-spacing:0.05em}
.green{color:var(--green)}.red{color:var(--red)}.yellow{color:var(--yellow)}.blue{color:var(--blue)}.gray{color:var(--dim)}

/* coverage summary bars */
.cov-summary{padding:0 24px 18px;display:flex;gap:12px;flex-wrap:wrap}
.cov-stat{background:var(--surface);border-radius:10px;padding:12px 16px;border:1px solid var(--border);
  flex:1;min-width:170px;box-shadow:0 1px 3px #00000010}
.cov-label{font-size:0.65rem;color:var(--muted);text-transform:uppercase;letter-spacing:0.05em;margin-bottom:7px}
.cov-main{display:flex;align-items:center;gap:10px}
.cov-pct-big{font-size:1.5rem;font-weight:800;min-width:52px}
.cov-bar-track{flex:1;height:9px;border-radius:99px;background:#e2e8f0;overflow:hidden;border:1px solid #cbd5e1}
.cov-bar-fill{height:100%;border-radius:99px}

/* progress bar */
.progress-wrap{padding:0 24px 18px}
.progress-bar{height:7px;border-radius:99px;background:#e2e8f0;overflow:hidden;border:1px solid #cbd5e1}
.progress-fill{height:100%;border-radius:99px}
.progress-label{font-size:0.72rem;color:var(--muted);margin-top:4px;display:flex;justify-content:space-between}

/* service cards */
.services{padding:0 24px 40px;display:flex;flex-direction:column;gap:9px}
.svc-card{background:var(--surface);border-radius:12px;border:1px solid var(--border);overflow:hidden;
  scroll-margin-top:62px;box-shadow:0 1px 4px #00000010}
.svc-card.fail-card{border-color:#fca5a5;box-shadow:0 1px 6px #dc262618}
.svc-card.pass-card{border-color:#86efac}
.svc-header{padding:12px 16px;display:flex;align-items:center;cursor:pointer;gap:9px;
  user-select:none;transition:background 0.15s}
.svc-header:hover{background:#f8fafc}
.svc-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0}
.dot-pass{background:#16a34a;box-shadow:0 0 5px #16a34a66}
.dot-fail{background:#dc2626;box-shadow:0 0 5px #dc262666}
.svc-name{font-weight:700;font-size:0.92rem;flex:1;color:#0f172a}
.svc-meta{display:flex;gap:10px;font-size:0.76rem;align-items:center;flex-wrap:wrap}
.svc-meta .mp{color:var(--green)}.svc-meta .mf{color:var(--red)}.svc-meta .ms{color:var(--yellow)}.svc-meta .mt{color:var(--dim)}
.tag{display:inline-block;padding:1px 8px;border-radius:99px;font-size:0.68rem;font-weight:700}
.tag-pass{background:#dcfce7;color:#15803d;border:1px solid #86efac}
.tag-fail{background:#fee2e2;color:#dc2626;border:1px solid #fca5a5}
.chev{color:#94a3b8;font-size:0.65rem;margin-left:4px;transition:transform 0.2s;display:inline-block}
.chev.open{transform:rotate(90deg)}

/* tabs */
.svc-body{display:none;border-top:1px solid var(--border)}
.svc-body.open{display:block}
.tab-bar{display:flex;border-bottom:1px solid var(--border);background:#f8fafc}
.tab-btn{padding:8px 16px;font-size:0.75rem;font-weight:600;color:var(--muted);cursor:pointer;
  border:none;background:none;border-bottom:2px solid transparent;transition:all 0.15s;position:relative;top:1px}
.tab-btn:hover{color:var(--text)}
.tab-btn.active{color:var(--blue);border-bottom-color:var(--blue)}
.tab-pane{display:none}.tab-pane.active{display:block}

/* test table */
.suite-head{padding:8px 16px 5px;font-size:0.68rem;color:var(--muted);font-weight:600;
  background:#f1f5f9;border-top:1px solid var(--border);display:flex;align-items:center;gap:7px}
.suite-head:first-child{border-top:none}
.suite-fail-badge{background:#fee2e2;color:#dc2626;font-size:0.62rem;padding:1px 6px;border-radius:99px;border:1px solid #fca5a5}
table{width:100%;border-collapse:collapse;font-size:0.78rem}
th{padding:6px 16px;text-align:left;font-size:0.65rem;color:var(--muted);text-transform:uppercase;
  letter-spacing:0.05em;background:#f1f5f9;white-space:nowrap;border-bottom:1px solid var(--border)}
td{padding:6px 16px;border-top:1px solid var(--border);vertical-align:top}
tr.tc-row:hover td{background:#f8fafc}
tr.tc-row.hidden-row{display:none}
.tc-name{color:#1e293b;word-break:break-word;max-width:480px}
.tc-time{color:var(--dim);text-align:right;white-space:nowrap;min-width:52px}
.badge{font-size:0.65rem;font-weight:700;padding:2px 7px;border-radius:99px}
.b-pass{background:#dcfce7;color:#15803d;border:1px solid #86efac}
.b-fail{background:#fee2e2;color:#dc2626;border:1px solid #fca5a5}
.b-err{background:#ffedd5;color:#c2410c;border:1px solid #fdba74}
.b-skip{background:#fef3c7;color:#b45309;border:1px solid #fde68a}
.tc-msg{color:#dc2626;font-size:0.7rem;margin-top:2px;word-break:break-word}
.tc-detail-wrap{margin-top:4px}
.tc-detail-toggle{font-size:0.68rem;color:var(--blue);cursor:pointer;border:none;background:none;padding:0;text-decoration:underline}
.tc-detail{display:none;background:#f8fafc;border:1px solid var(--border);border-radius:6px;
  padding:10px 14px;margin-top:4px;font-family:'Fira Code','Consolas',monospace;font-size:0.68rem;
  color:#374151;white-space:pre-wrap;word-break:break-word;max-height:260px;overflow-y:auto;line-height:1.5}
.tc-detail.open{display:block}
.tc-slow{color:var(--yellow);font-size:0.68rem;margin-left:4px}

/* slow tests panel */
.slow-panel{padding:12px 16px;border-bottom:1px solid var(--border);background:#fffbeb}
.slow-panel h4{font-size:0.7rem;color:#b45309;margin-bottom:8px;text-transform:uppercase;letter-spacing:0.05em}
.slow-row{display:flex;justify-content:space-between;align-items:flex-start;padding:4px 0;
  border-top:1px solid #fde68a;font-size:0.76rem}
.slow-row:first-of-type{border-top:none}
.slow-test-name{color:#374151;flex:1;padding-right:12px;word-break:break-word}
.slow-test-time{color:#d97706;font-weight:700;white-space:nowrap}

/* coverage tables */
.cov-table-wrap{padding:14px 16px}
.cov-table-wrap h4{font-size:0.7rem;color:var(--muted);margin-bottom:8px;text-transform:uppercase;letter-spacing:0.05em}
.cov-table{width:100%;border-collapse:collapse;font-size:0.76rem}
.cov-table th{padding:6px 10px;text-align:left;font-size:0.64rem;color:var(--muted);
  text-transform:uppercase;letter-spacing:0.05em;background:#f1f5f9;cursor:pointer;white-space:nowrap;
  user-select:none;border-bottom:1px solid var(--border)}
.cov-table th:hover{color:var(--text)}
.cov-table td{padding:5px 10px;border-top:1px solid var(--border);vertical-align:middle}
.cov-table tr:hover td{background:#f8fafc}
.pkg-name{color:#1d4ed8;font-family:monospace;font-size:0.73rem;font-weight:600}
.cls-name{color:#374151;font-family:monospace;font-size:0.7rem;padding-left:22px!important}
.cov-wrap{display:inline-flex;align-items:center;gap:5px}
.cov-no{color:var(--dim);font-size:0.7rem}
.cov-fraction{color:#94a3b8;font-size:0.68rem;white-space:nowrap}
.pkg-toggle{cursor:pointer;color:var(--blue);font-size:0.65rem;border:none;background:none;margin-right:4px;line-height:1}
.no-cov{padding:20px;text-align:center;color:var(--muted);font-size:0.8rem}
.no-results{padding:20px;text-align:center;color:var(--muted);display:none}

/* overall coverage mini stats */
.cov-overall-row{display:flex;gap:20px;flex-wrap:wrap;margin-bottom:14px;
  background:#f1f5f9;padding:12px 14px;border-radius:8px;border:1px solid var(--border)}
.cov-overall-item{text-align:center;min-width:80px}
.cov-overall-item .cov-big{font-size:1.3rem;font-weight:800;line-height:1}
.cov-overall-item .cov-sub{font-size:0.62rem;color:var(--muted);text-transform:uppercase;letter-spacing:0.04em;margin-top:2px}
.cov-overall-item .cov-frac{font-size:0.64rem;color:var(--dim)}

/* footer */
.footer{text-align:center;padding:18px;color:var(--dim);font-size:0.7rem;border-top:1px solid var(--border);margin-top:8px}

/* print */
@media print{
  .topbar,.btn-sm,#clear-search,.tab-bar,.pkg-toggle{display:none!important}
  .svc-body{display:block!important}
  .tab-pane{display:block!important}
  body{background:#fff;color:#000;font-size:11px}
  .svc-card{break-inside:avoid;border:1px solid #ddd;margin-bottom:8px;box-shadow:none}
  .tc-detail{max-height:none}
}
@media(max-width:640px){
  .summary{grid-template-columns:repeat(2,1fr)}
  .cov-summary{flex-direction:column}
  .nav-pills{display:none}
}
"""


# ─── JS ───────────────────────────────────────────────────────────────────────

JS = r"""
(function() {
// card expand/collapse
document.querySelectorAll('.svc-header').forEach(h => {
  h.addEventListener('click', () => {
    const body = h.nextElementSibling;
    const chev = h.querySelector('.chev');
    const open = body.classList.toggle('open');
    chev.classList.toggle('open', open);
  });
});
// auto-open failed services
document.querySelectorAll('.fail-card .svc-body').forEach(b => {
  b.classList.add('open');
  b.previousElementSibling.querySelector('.chev').classList.add('open');
});

// tab switching
document.querySelectorAll('.tab-btn').forEach(btn => {
  btn.addEventListener('click', e => {
    e.stopPropagation();
    const card = btn.closest('.svc-card');
    card.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    card.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById(btn.dataset.tab).classList.add('active');
  });
});

// stack trace toggle
document.querySelectorAll('.tc-detail-toggle').forEach(btn => {
  btn.addEventListener('click', e => {
    e.stopPropagation();
    const det = btn.nextElementSibling;
    const open = det.classList.toggle('open');
    btn.textContent = open ? '▲ hide trace' : '▼ show trace';
  });
});

// coverage class rows toggle
document.querySelectorAll('.pkg-toggle').forEach(btn => {
  btn.addEventListener('click', e => {
    e.stopPropagation();
    const pkgId = btn.dataset.pkg;
    const rows = document.querySelectorAll('.cls-rows-' + pkgId);
    const open = btn.dataset.open === 'true';
    rows.forEach(r => r.style.display = open ? 'none' : '');
    btn.dataset.open = open ? 'false' : 'true';
    btn.textContent = open ? '▶' : '▼';
  });
});

// expand / collapse all
document.getElementById('expand-all').addEventListener('click', () => {
  document.querySelectorAll('.svc-body').forEach(b => {
    b.classList.add('open');
    b.previousElementSibling.querySelector('.chev').classList.add('open');
  });
});
document.getElementById('collapse-all').addEventListener('click', () => {
  document.querySelectorAll('.svc-body').forEach(b => {
    b.classList.remove('open');
    b.previousElementSibling.querySelector('.chev').classList.remove('open');
  });
});

// search
const searchInput = document.getElementById('search');
const clearBtn = document.getElementById('clear-search');
const noResults = document.getElementById('no-results');
searchInput.addEventListener('input', runSearch);
clearBtn.addEventListener('click', () => { searchInput.value = ''; runSearch(); });
function runSearch() {
  const q = searchInput.value.trim().toLowerCase();
  clearBtn.style.display = q ? 'block' : 'none';
  let anyVisible = false;
  document.querySelectorAll('.svc-card').forEach(card => {
    if (!q) {
      card.querySelectorAll('.tc-row').forEach(r => r.classList.remove('hidden-row'));
      card.style.display = '';
      return;
    }
    let has = false;
    card.querySelectorAll('.tc-row').forEach(row => {
      const match = (row.dataset.name || '').includes(q);
      row.classList.toggle('hidden-row', !match);
      if (match) has = true;
    });
    card.style.display = has ? '' : 'none';
    if (has) {
      anyVisible = true;
      card.querySelector('.svc-body').classList.add('open');
      card.querySelector('.chev').classList.add('open');
    }
  });
  noResults.style.display = q && !anyVisible ? 'block' : 'none';
}

// coverage table sort
document.querySelectorAll('.cov-table').forEach(table => {
  table.querySelectorAll('th[data-sort]').forEach(th => {
    th.addEventListener('click', () => {
      const col = th.dataset.sort;
      const body = table.querySelector('tbody');
      const rows = Array.from(body.querySelectorAll('tr.sortable'));
      const asc = th.dataset.asc !== 'true';
      th.dataset.asc = asc;
      const pkgRows = rows.filter(r => !r.classList.contains('cls-row'));
      pkgRows.sort((a, b) => {
        const av = parseFloat(a.dataset[col] ?? 200);
        const bv = parseFloat(b.dataset[col] ?? 200);
        return asc ? av - bv : bv - av;
      });
      pkgRows.forEach(pr => {
        body.appendChild(pr);
        const pkgId = pr.dataset.pkgid;
        if (pkgId) {
          body.querySelectorAll('.cls-rows-' + pkgId).forEach(cr => body.appendChild(cr));
        }
      });
    });
  });
});

// print
document.getElementById('btn-print').addEventListener('click', () => window.print());
})();
"""


# ─── Test tab rendering ───────────────────────────────────────────────────────

def render_test_tab(svc: dict) -> str:
    all_cases = [(c, s["name"]) for s in svc["suites"] for c in s["cases"]]
    slow = sorted(
        [c for c, _ in all_cases if c["time"] >= 1.0],
        key=lambda c: -c["time"]
    )[:10]

    html = ""
    if slow:
        html += '<div class="slow-panel"><h4>&#9201; Slow tests (&ge;1 s)</h4>'
        for c in slow:
            cls_short = c["classname"].split(".")[-1] if "." in c["classname"] else c["classname"]
            html += (f'<div class="slow-row">'
                     f'<span class="slow-test-name">{esc(cls_short)}'
                     f'<span style="color:#94a3b8"> &rarr; {esc(c["name"])}</span>'
                     f'</span>'
                     f'<span class="slow-test-time">{fmt_time(c["time"])}</span>'
                     f'</div>')
        html += '</div>'

    html += '<table><thead><tr><th>Test method</th><th>Status</th><th>Time</th></tr></thead><tbody>'
    for suite in svc["suites"]:
        fail_count = suite["failures"] + suite["errors"]
        badge = f'<span class="suite-fail-badge">{fail_count} failed</span>' if fail_count else ""
        short = suite["name"].split(".")[-1] if "." in suite["name"] else suite["name"]
        full = suite["name"]
        html += (f'<tr><td colspan="3" style="padding:0">'
                 f'<div class="suite-head" title="{esc(full)}">{esc(short)}{badge}'
                 f'<span style="color:#94a3b8;font-size:0.63rem;margin-left:6px">{esc(full)}</span>'
                 f'</div></td></tr>')

        cases = sorted(suite["cases"], key=lambda c: (
            0 if c["status"] in ("failed", "error") else 1, c["name"]
        ))
        for c in cases:
            sm = {"passed": ("b-pass", "PASS"), "failed": ("b-fail", "FAIL"),
                  "error": ("b-err", "ERR"), "skipped": ("b-skip", "SKIP")}
            cls, label = sm.get(c["status"], ("b-pass", "?"))
            slow_tip = '<span class="tc-slow" title="Slow test (&ge;1s)">&#9201;</span>' if c["time"] >= 1.0 else ""
            search_key = esc(f"{c['classname']} {c['name']}".lower())
            html += f'<tr class="tc-row" data-name="{search_key}">'
            html += f'<td class="tc-name">{esc(c["name"])}{slow_tip}'
            if c["message"]:
                html += f'<div class="tc-msg">{esc(c["message"][:500])}</div>'
            if c["detail"]:
                html += (f'<div class="tc-detail-wrap">'
                         f'<button class="tc-detail-toggle">&#9660; show trace</button>'
                         f'<div class="tc-detail">{esc(c["detail"][:3000])}</div>'
                         f'</div>')
            html += '</td>'
            html += f'<td><span class="badge {cls}">{label}</span></td>'
            html += f'<td class="tc-time">{fmt_time(c["time"])}</td>'
            html += '</tr>\n'
    html += '</tbody></table>'
    return html


# ─── Coverage tab rendering ───────────────────────────────────────────────────

def render_coverage_tab(coverage: dict | None) -> str:
    if coverage is None:
        return '<div class="no-cov">No coverage report found for this service.</div>'

    ct = coverage["counters"]

    html = '<div class="cov-table-wrap"><h4>Coverage Summary</h4>'
    html += '<div class="cov-overall-row">'
    for key, label in [("LINE", "Lines"), ("BRANCH", "Branches"),
                        ("METHOD", "Methods"), ("CLASS", "Classes"),
                        ("INSTRUCTION", "Instructions"), ("COMPLEXITY", "Complexity")]:
        c = ct.get(key, {})
        pct = c.get("pct")
        total = c.get("total", 0)
        covered = c.get("covered", 0)
        if total == 0:
            continue
        color = _cov_color(pct)
        pct_str = f"{pct}%" if pct is not None else "n/a"
        html += (f'<div class="cov-overall-item">'
                 f'<div class="cov-big" style="color:{color}">{pct_str}</div>'
                 f'<div class="cov-sub">{label}</div>'
                 f'<div class="cov-frac">{covered}/{total}</div>'
                 f'</div>')
    html += '</div>'

    if not coverage["packages"]:
        html += '<div style="color:#94a3b8;font-size:0.78rem;padding:8px 0">No package data.</div></div>'
        return html

    html += '<h4 style="margin-bottom:8px;margin-top:4px">Package &amp; Class Breakdown</h4>'
    html += ('<table class="cov-table"><thead><tr>'
             '<th>Package / Class</th>'
             '<th data-sort="line">Lines &#8597;</th>'
             '<th data-sort="branch">Branches &#8597;</th>'
             '<th data-sort="method">Methods &#8597;</th>'
             '<th data-sort="instr">Instructions &#8597;</th>'
             '</tr></thead><tbody>')

    for i, pkg in enumerate(coverage["packages"]):
        pkg_ct = pkg["counters"]
        pkg_id = f"pkg{i}"
        lp = pkg_ct.get("LINE", {}).get("pct") or 0
        bp = pkg_ct.get("BRANCH", {}).get("pct") or 0
        mp = pkg_ct.get("METHOD", {}).get("pct") or 0
        ip = pkg_ct.get("INSTRUCTION", {}).get("pct") or 0
        short_pkg = pkg["name"].split(".")[-1] if "." in pkg["name"] else pkg["name"]

        html += (f'<tr class="sortable" data-pkgid="{pkg_id}" '
                 f'data-line="{lp}" data-branch="{bp}" data-method="{mp}" data-instr="{ip}">')
        html += (f'<td class="pkg-name">'
                 f'<button class="pkg-toggle" data-pkg="{pkg_id}" data-open="false">&#9654;</button>'
                 f'<strong>{esc(short_pkg)}</strong>'
                 f'<span style="color:#94a3b8;font-size:0.66rem;margin-left:6px">{esc(pkg["name"])}</span>'
                 f'</td>')
        html += cov_cell(pkg_ct.get("LINE", {}))
        html += cov_cell(pkg_ct.get("BRANCH", {}))
        html += cov_cell(pkg_ct.get("METHOD", {}))
        html += cov_cell(pkg_ct.get("INSTRUCTION", {}))
        html += '</tr>\n'

        for cls in pkg.get("classes", []):
            cls_ct = cls["counters"]
            cl = cls_ct.get("LINE", {}).get("pct") or 0
            cb = cls_ct.get("BRANCH", {}).get("pct") or 0
            cm = cls_ct.get("METHOD", {}).get("pct") or 0
            ci = cls_ct.get("INSTRUCTION", {}).get("pct") or 0
            cname = cls["name"].replace(".java", "")
            html += (f'<tr class="sortable cls-row cls-rows-{pkg_id}" style="display:none" '
                     f'data-line="{cl}" data-branch="{cb}" data-method="{cm}" data-instr="{ci}">')
            html += f'<td class="cls-name">{esc(cname)}</td>'
            html += cov_cell(cls_ct.get("LINE", {}))
            html += cov_cell(cls_ct.get("BRANCH", {}))
            html += cov_cell(cls_ct.get("METHOD", {}))
            html += cov_cell(cls_ct.get("INSTRUCTION", {}))
            html += '</tr>\n'

    html += '</tbody></table></div>'
    return html


# ─── Service card rendering ───────────────────────────────────────────────────

def render_service(svc: dict, idx: int) -> str:
    has_fail = svc["failures"] + svc["errors"] > 0
    card_cls = "fail-card" if has_fail else "pass-card"
    dot_cls = "dot-fail" if has_fail else "dot-pass"
    tag = '<span class="tag tag-fail">FAIL</span>' if has_fail else '<span class="tag tag-pass">PASS</span>'
    passed = svc["tests"] - svc["failures"] - svc["errors"] - svc["skipped"]

    cov_badge = ""
    if svc.get("coverage"):
        lp = svc["coverage"]["counters"].get("LINE", {}).get("pct")
        bp = svc["coverage"]["counters"].get("BRANCH", {}).get("pct")
        if lp is not None:
            cov_badge += f'<span style="color:{_cov_color(lp)};font-size:0.7rem;font-weight:600">L:{lp}%</span>'
        if bp is not None:
            cov_badge += f' <span style="color:{_cov_color(bp)};font-size:0.7rem;font-weight:600">B:{bp}%</span>'

    svc_id = f"svc-{svc['name']}"
    tid_tests = f"tt-{idx}"
    tid_cov   = f"tc-{idx}"

    html = f'<div class="svc-card {card_cls}" id="{svc_id}">'
    html += f'<div class="svc-header">'
    html += f'  <span class="svc-dot {dot_cls}"></span>'
    html += f'  <span class="svc-name">{esc(svc["name"])}</span>'
    html += f'  <div class="svc-meta">'
    html += f'    {tag}'
    html += f'    <span class="mp">&#10003; {passed}</span>'
    if svc["failures"] > 0:
        html += f'    <span class="mf">&#10007; {svc["failures"]}</span>'
    if svc["errors"] > 0:
        html += f'    <span class="mf">&#9889; {svc["errors"]}</span>'
    if svc["skipped"] > 0:
        html += f'    <span class="ms">&#8960; {svc["skipped"]}</span>'
    html += f'    <span class="mt">{fmt_time(svc["time"])}</span>'
    if cov_badge:
        html += f'    {cov_badge}'
    html += f'  </div>'
    html += f'  <span class="chev">&#9654;</span>'
    html += f'</div>'

    html += f'<div class="svc-body">'
    html += f'  <div class="tab-bar">'
    html += f'    <button class="tab-btn active" data-tab="{tid_tests}">Tests ({svc["tests"]})</button>'
    html += f'    <button class="tab-btn" data-tab="{tid_cov}">Coverage</button>'
    html += f'  </div>'
    html += f'  <div class="tab-pane active" id="{tid_tests}">{render_test_tab(svc)}</div>'
    html += f'  <div class="tab-pane" id="{tid_cov}">{render_coverage_tab(svc.get("coverage"))}</div>'
    html += f'</div></div>\n'
    return html


# ─── Aggregate coverage across all services ───────────────────────────────────

def aggregate_coverage(services: dict) -> dict:
    totals: dict[str, dict] = {}
    for svc in services.values():
        if not svc.get("coverage"):
            continue
        for ctype, cdata in svc["coverage"]["counters"].items():
            if ctype not in totals:
                totals[ctype] = {"missed": 0, "covered": 0}
            totals[ctype]["missed"] += cdata["missed"]
            totals[ctype]["covered"] += cdata["covered"]
    out = {}
    for ctype, d in totals.items():
        total = d["missed"] + d["covered"]
        out[ctype] = {**d, "total": total,
                      "pct": round(d["covered"] / total * 100, 1) if total else None}
    return out


# ─── Full HTML assembly ───────────────────────────────────────────────────────

def generate_html(services: dict, generated_at: str, duration: float) -> str:
    total_tests = sum(s["tests"]                  for s in services.values())
    total_fail  = sum(s["failures"] + s["errors"] for s in services.values())
    total_skip  = sum(s["skipped"]                for s in services.values())
    total_pass  = total_tests - total_fail - total_skip
    total_svc   = len(services)
    fail_svc    = sum(1 for s in services.values() if s["failures"] + s["errors"] > 0)
    pass_pct    = round(total_pass / total_tests * 100, 1) if total_tests > 0 else 0
    fill_color  = "#16a34a" if total_fail == 0 else "#dc2626"
    overall_lbl = "ALL PASS" if total_fail == 0 else f"{total_fail} FAILED"
    overall_col = "#15803d" if total_fail == 0 else "#dc2626"

    cov_totals = aggregate_coverage(services)

    sorted_svc = sorted(
        services.values(),
        key=lambda s: (0 if s["failures"] + s["errors"] > 0 else 1, s["name"])
    )

    nav = "".join(
        f'<a class="nav-pill {"fail" if s["failures"]+s["errors"]>0 else "pass"}" '
        f'href="#svc-{esc(s["name"])}">{esc(s["name"])}</a>'
        for s in sorted_svc
    )

    cov_row = ""
    for key, label in [("LINE", "Line Coverage"), ("BRANCH", "Branch Coverage"),
                        ("METHOD", "Method Coverage"), ("INSTRUCTION", "Instruction Coverage")]:
        c = cov_totals.get(key, {})
        if c.get("total", 0) > 0:
            cov_row += big_cov_bar(c.get("pct"), label)

    service_html = "".join(render_service(svc, i) for i, svc in enumerate(sorted_svc))

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Test Report &mdash; WorkFit AI</title>
<style>{CSS}</style>
</head>
<body>

<div class="topbar">
  <h1>WorkFit AI &nbsp;<span style="color:{overall_col};font-size:0.82rem;font-weight:600">{esc(overall_lbl)}</span></h1>
  <span class="ts">{esc(generated_at)} &middot; {fmt_time(duration)} &middot; {total_svc - fail_svc}/{total_svc} services ok</span>
  <div class="search-wrap">
    <svg class="ico" width="13" height="13" viewBox="0 0 20 20" fill="none" stroke="#94a3b8" stroke-width="2">
      <circle cx="8" cy="8" r="5"/><path d="M15 15l-3.5-3.5"/>
    </svg>
    <input id="search" type="text" placeholder="Filter tests&hellip;" autocomplete="off">
    <button id="clear-search">&times;</button>
  </div>
  <div class="nav-pills">{nav}</div>
  <div class="topbar-right">
    <button class="btn-sm" id="expand-all">Expand all</button>
    <button class="btn-sm" id="collapse-all">Collapse all</button>
    <button class="btn-sm" id="btn-print">Print</button>
  </div>
</div>

<div class="summary">
  <div class="stat"><div class="v green">{total_pass}</div><div class="l">Passed</div></div>
  <div class="stat"><div class="v {'red' if total_fail > 0 else 'gray'}">{total_fail}</div><div class="l">Failed / Error</div></div>
  <div class="stat"><div class="v yellow">{total_skip}</div><div class="l">Skipped</div></div>
  <div class="stat"><div class="v blue">{total_tests}</div><div class="l">Total Tests</div></div>
  <div class="stat"><div class="v {'green' if pass_pct==100 else 'red'}">{pass_pct}%</div><div class="l">Pass Rate</div></div>
</div>

<div class="progress-wrap">
  <div class="progress-bar">
    <div class="progress-fill" style="width:{pass_pct}%;background:{fill_color}"></div>
  </div>
  <div class="progress-label"><span>{total_pass} passed of {total_tests} total</span><span>{pass_pct}%</span></div>
</div>

{f'<div class="cov-summary">{cov_row}</div>' if cov_row else ''}

<div id="no-results" class="no-results">No tests match your search.</div>
<div class="services">{service_html}</div>
<div class="footer">WorkFit AI Platform &mdash; Test Report &mdash; {esc(generated_at)}</div>
<script>{JS}</script>
</body>
</html>
"""


# ─── Entry point ──────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Generate HTML test+coverage report from Surefire + JaCoCo XML"
    )
    parser.add_argument("--root",     default=".",                help="Project root directory")
    parser.add_argument("--output",   default="test-report.html", help="Output HTML file path")
    parser.add_argument("--duration", type=float, default=0.0,    help="Total test duration in seconds")
    args = parser.parse_args()

    root_dir    = os.path.abspath(args.root)
    output_path = args.output if os.path.isabs(args.output) else os.path.join(root_dir, args.output)

    services = collect_results(root_dir)
    if not services:
        print(f"[WARN] No Surefire XML reports under {root_dir}/services/*/target/surefire-reports/")
        sys.exit(0)

    generated_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    duration     = args.duration or sum(s["time"] for s in services.values())
    html         = generate_html(services, generated_at, duration)

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(html)

    total   = sum(s["tests"]                  for s in services.values())
    fails   = sum(s["failures"] + s["errors"] for s in services.values())
    has_cov = sum(1 for s in services.values() if s.get("coverage"))
    print(f"[REPORT] Generated: {output_path}")
    print(f"[REPORT] Services: {len(services)}  Tests: {total}  Failures: {fails}  "
          f"Coverage data: {has_cov}/{len(services)} services")


if __name__ == "__main__":
    main()

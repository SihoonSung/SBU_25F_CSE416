import os, re, sys

ROOT = "/mnt/c/Users/minse/CSE416_project/sbu_cse416"

JAVA_METHOD = re.compile(r"^(\s*)(public|protected|private)\s+[\w<>\[\],\.?&\s]+\s+(\w+)\s*\([^;]*\)\s*\{\s*$")
TS_FN_DECL = re.compile(r"^(\s*)function\s+(\w+)\s*\([^)]*\)\s*\{\s*$")
TS_CONST_ARROW = re.compile(r"^(\s*)(?:const|let|var)\s+(\w+)\s*(?::\s*[^=]+)?=\s*(?:async\s*)?\([^)]*\)\s*=>\s*\{\s*$")

EXTS = (".java", ".tsx")


def list_files(root):
    for base, _dirs, files in os.walk(root):
        for f in files:
            if f.startswith(.):
                continue
            if f.endswith(EXTS):
                yield os.path.join(base, f)


def to_description(name: str) -> str:
    s = re.sub(r"[_$]+", " ", name)
    s = re.sub(r"([a-z])([A-Z])", r"\1 \2", s)
    return s.strip().lower() or "function"


def process_file(path: str) -> bool:
    try:
        with open(path, r, encoding=utf-8) as fh:
            lines = fh.readlines()
    except Exception:
        return False

    changed = False
    out = []

    def has_tp_comment(prev_nonempty: str) -> bool:
        return prev_nonempty.strip().startswith("// TP:")

    i = 0
    while i < len(lines):
        line = lines[i]
        out.append(line)
        m = None
        if path.endswith(.java):
            m = JAVA_METHOD.match(line)
        elif path.endswith(..tsx):
            m = TS_FN_DECL.match(line) or TS_CONST_ARROW.match(line)

        if m:
            indent = m.group(1) or ""
            fn_name = m.group(m.lastindex)  # last capturing group is the function name
            # find previous non-empty line in current output
            j = len(out) - 2
            prev = ""
            while j >= 0:
                if out[j].strip() != "":
                    prev = out[j]
                    break
                j -= 1
            if not has_tp_comment(prev):
                desc = to_description(fn_name)
                out.append(f"{indent}// TP: Used cursor to {desc}\n")
                changed = True
        i += 1

    if changed:
        try:
            backup = path + ".bak"
            if not os.path.exists(backup):
                with open(backup, w, encoding=utf-8) as bk:
                    bk.writelines(lines)
            with open(path, w, encoding=utf-8) as fh:
                fh.writelines(out)
        except Exception:
            return False
    return changed


def main():
    changed_files = 0
    for f in list_files(ROOT):
        if process_file(f):
            changed_files += 1
    print(f"TP comments inserted/verified in {changed_files} files")

if __name__ == "__main__":
    main()

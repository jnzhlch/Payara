"""静态 wiring 闭包求解 — feature-resolution M1
用法: wireclosure.py <modules_dir> <closure_txt> [--write]
读候选 jar 集的 Import-Package, 对每个非 optional 且无提供者的包,
按 Export-Package 反查提供者 jar 并加入候选集, 迭代至不动点。
--write: 把新增 jar 追加回 closure_txt。JDK/框架假设提供的包前缀见 PROVIDED_PREFIXES。
"""
import os, re, sys, zipfile

# javax.*/org.omg.* 不能默认算 JDK 提供: javax.rmi 系由 glassfish-corba-omgapi 提供 (JDK 11+ 移除);
# org.osgi.service/util 是 OSGi 组合包, 发行版里有提供者 jar (org.osgi.service.component.jar 等) — 不当框架提供
PROVIDED_PREFIXES = (
    'java.', 'org.osgi.framework', 'org.osgi.resource', 'org.osgi.namespace',
    'org.ietf.', 'org.w3c.', 'org.xml.',
    'sun.', 'com.sun.source', 'com.sun.security.auth', 'com.sun.jmx',
    'com.sun.jdi', 'com.sun.management', 'com.sun.nio', 'com.sun.tools',
)

def manifest_raw(path):
    with zipfile.ZipFile(path) as z:
        return z.read('META-INF/MANIFEST.MF').decode('utf-8', 'ignore')

def unfold(raw):
    out = []
    for ln in raw.splitlines():
        if ln.startswith(' ') and out:
            out[-1] += ln[1:]
        else:
            out.append(ln)
    return '\n'.join(out)

def header(mf, name):
    m = re.search(r'^' + name + r':[ \t]*(.*?)(?=^[A-Za-z][A-Za-z0-9-]*:[ \t]|\Z)', mf, re.M | re.S)
    return m.group(1).strip() if m else ''

def split_pkgs(value):
    pkgs, depth, buf = [], 0, []
    for ch in value:
        if ch == '"':
            depth = 1 - depth
        if ch == ',' and not depth:
            pkgs.append(''.join(buf)); buf = []
        else:
            buf.append(ch)
    if buf:
        pkgs.append(''.join(buf))
    return [p.split(';')[0].strip() for p in pkgs if p.split(';')[0].strip()]

def imports_of(mf):
    raw = header(mf, 'Import-Package')
    out = []
    # & 多子句: (&(pkg=A)(x=y)) 形式
    for m in re.finditer(r'\(&\(package=([^)]+)\)[^)]*\)', raw):
        out.append((m.group(1), False))
    cleaned = re.sub(r'\(&\(package=[^)]+\)[^)]*\)', '', raw)
    for seg in cleaned.split(','):
        seg = seg.strip()
        if not seg:
            continue
        name = seg.split(';')[0].strip()
        optional = 'resolution:=optional' in seg or 'resolution:=dynamic' in seg
        if name:
            out.append((name, optional))
    return out

def main():
    mods, cl_txt = sys.argv[1], sys.argv[2]
    write = '--write' in sys.argv
    jars = [l.strip() for l in open(cl_txt, encoding='utf-8')
            if l.strip() and not l.strip().startswith('#')]
    sel = list(dict.fromkeys(jars))

    exp_map = {}   # jar -> set(packages)
    imp_map = {}   # jar -> [(pkg, optional)]
    for f in sorted(os.listdir(mods)):
        if f.endswith('.jar'):
            try:
                mf = unfold(manifest_raw(os.path.join(mods, f)))
                exp_map[f] = set(split_pkgs(header(mf, 'Export-Package')))
                imp_map[f] = imports_of(mf)
            except Exception:
                pass
    auto = sorted(os.listdir(os.path.join(mods, 'autostart'))) if os.path.isdir(os.path.join(mods, 'autostart')) else []
    for f in auto:
        if f.endswith('.jar'):
            try:
                mf = unfold(manifest_raw(os.path.join(mods, 'autostart', f)))
                exp_map[f] = set(split_pkgs(header(mf, 'Export-Package')))
                imp_map.setdefault(f, imports_of(mf))
            except Exception:
                pass

    def provider(pkg):
        return [j for j, pkgs in exp_map.items() if pkg in pkgs]

    added = []
    for _ in range(20):
        exp_all = set()
        for j in sel:
            exp_all |= exp_map.get(j, set())
        need = []
        for j in sel:
            for pkg, optional in imp_map.get(j, []):
                if optional or pkg in PROVIDED_PREFIXES or any(pkg.startswith(p) for p in PROVIDED_PREFIXES):
                    continue
                if pkg in exp_all:
                    continue
                provs = [p for p in provider(pkg) if p not in sel]
                if provs:
                    need.extend(provs)
                # 无提供者: 假定 JDK/框架 (记警告)
        need = sorted(dict.fromkeys(need))
        if not need:
            break
        for j in need:
            sel.append(j)
            added.append(j)
        print(f'+{len(need)}: {", ".join(need)}')

    if added:
        print(f'共新增 {len(added)} 个 jar')
        if write:
            with open(cl_txt, 'a', encoding='utf-8') as f:
                f.write('# wireclosure 自动补齐\n')
                for j in added:
                    f.write(j + '\n')
            print('已写回', cl_txt)
    else:
        print('wiring 闭包已收敛')

if __name__ == '__main__':
    main()

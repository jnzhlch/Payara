"""用法: findjar.py <glassfish_modules_dir> <term>
term 为类全名 (com.x.Y) 或包名 (com.x.y); 在 modules/*.jar 中找:
1) 含该类路径 .class 条目的 jar; 2) Export-Package 声明该包的 jar
"""
import os, re, sys, zipfile

root, term = sys.argv[1], sys.argv[2]
pkg_path = term.replace('.', '/')
hits = []
for f in sorted(os.listdir(root)):
    if not f.endswith('.jar'):
        continue
    p = os.path.join(root, f)
    try:
        z = zipfile.ZipFile(p)
    except Exception:
        continue
    ok = False
    try:
        for n in z.namelist():
            if n.endswith('.class') and n.startswith(pkg_path):
                ok = True
                break
    except Exception:
        pass
    if not ok:
        try:
            mf = z.read('META-INF/MANIFEST.MF').decode('utf-8', 'ignore')
            if re.search(r'Export-Package:[^A-Za-z]*' + re.escape(term) + r'(?=[,;\s]|$)', mf, re.I):
                ok = True
        except KeyError:
            pass
    if ok:
        hits.append(f)
print('\n'.join(hits) if hits else '(无命中)')

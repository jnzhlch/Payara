"""特性清单生成器 — feature-resolution M2
用法: gen-features.py <featureset-pom> <modules-dir> <out-dir>
遍历 featureset/packager pom 树, 每个 packager 生成一份特性清单 (<packager-id>.properties):
  feature.bundles=<jar 名 CSV>
  依赖 jar 名 = 依赖的 artifactId.jar (type=zip / 聚合 pom 除外)
同时输出 <out-dir>/_report.txt: 每个 packager 的 jar 数 + 在 modules/ 中缺失的 jar。
聚合 pom (type=pom) 按以下顺序查找子 pom:
  appserver/packager/<a>, nucleus/packager/<a>, appserver/featuresets/<a>
"""
import os, re, sys, glob
import xml.etree.ElementTree as ET

REPO = os.path.normpath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
POM_NS = {'m': 'http://maven.apache.org/POM/4.0.0'}

def find_pom(artifact_id):
    for pat in ('appserver/packager/%s/pom.xml', 'nucleus/packager/%s/pom.xml',
                'appserver/featuresets/%s/pom.xml', 'nucleus/featuresets/%s/pom.xml'):
        p = os.path.join(REPO, pat % artifact_id)
        if os.path.isfile(p):
            return p
    return None

def parse_deps(pom_path):
    """返回 (pom引用列表, zip引用列表, jar名列表)"""
    tree = ET.parse(pom_path)
    root = tree.getroot()
    deps = root.find('m:dependencies', POM_NS)
    poms, zips, jars = [], [], []
    if deps is None:
        return poms, zips, jars
    for d in deps.findall('m:dependency', POM_NS):
        aid = d.findtext('m:artifactId', default='', namespaces=POM_NS).strip()
        dtype = d.findtext('m:type', default='jar', namespaces=POM_NS).strip()
        if not aid:
            continue
        if dtype == 'pom':
            poms.append(aid)
        elif dtype == 'zip':
            zips.append(aid)
        else:
            if 'plugin' in aid or aid in ('packages', 'legal-fragment', 'distributions'):
                continue
            jars.append(aid + '.jar')
    return poms, zips, jars

def walk(artifact_id, seen_poms, packager_jars, depth=0):
    """递归展开: featureset(type=pom 嵌套, type=zip 引 packager) -> jar 集"""
    pom = find_pom(artifact_id)
    if pom is None:
        return ['未找到 pom: ' + artifact_id]
    if pom in seen_poms:
        return []
    seen_poms.add(pom)
    errors = []
    is_packager = ('/packager/' in pom.replace('\\', '/'))
    norm = pom.replace('\\', '/')
    poms, zips, jars = parse_deps(pom)
    if is_packager:
        # packager: jar 依赖 = bundle 集; zip 若指向另一个 packager 则继续下钻
        # (如 glassfish-management → nucleus-management), 其余 zip 是发行片段工件, 跳过
        packager_jars.setdefault(artifact_id, set()).update(jars)
        for sub in zips:
            sub_pom = find_pom(sub)
            if sub_pom is not None and '/packager/' in sub_pom.replace('\\', '/'):
                errors += walk(sub, seen_poms, packager_jars, depth + 1)
        return errors
    # featureset: pom 引用 = 子 featureset; zip 引用 = 子 packager
    for sub in zips:
        errors += walk(sub, seen_poms, packager_jars, depth + 1)
    for sub in poms:
        errors += walk(sub, seen_poms, packager_jars, depth + 1)
    return errors

def main():
    fs_pom, modules_dir, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]
    root_id = os.path.basename(os.path.dirname(fs_pom))
    seen, packager_jars = set(), {}
    errors = walk(root_id, seen, packager_jars)
    os.makedirs(out_dir, exist_ok=True)
    mods = set()
    for j in os.listdir(modules_dir):
        if j.endswith('.jar'):
            mods.add(j)
    report = []
    emitted = 0
    for pid in sorted(packager_jars):
        jars = sorted(packager_jars[pid])
        missing = [j for j in jars if j not in mods]
        with open(os.path.join(out_dir, pid + '.properties'), 'w', encoding='utf-8', newline='\n') as f:
            f.write('# 特性清单: %s (生成自 %s packager)\n' % (pid, pid))
            f.write('feature.bundles=%s\n' % ','.join(jars))
        emitted += 1
        report.append('%-32s jars=%-4d 缺失=%d %s' % (pid, len(jars), len(missing),
                      (' MISSING: ' + ','.join(missing[:5])) if missing else ''))
    report.append('---')
    report.append('packagers=%d, 生成清单=%d, 根=%s' % (len(packager_jars), emitted, root_id))
    for e in errors:
        report.append('WARN: ' + e)
    with open(os.path.join(out_dir, '_report.txt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(report) + '\n')
    print('\n'.join(report))

if __name__ == '__main__':
    main()

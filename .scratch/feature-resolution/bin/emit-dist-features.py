"""合成 dist 级特性清单 — feature-resolution
用法: emit-dist-features.py <gen-dir> <verified-dir> <out-dir>
来源优先级:
  1. verified-dir 中存在同名(语义名)清单 → 用 boot 验证过的内容
  2. 否则用 gen-dir 的 packager 清单(生成草稿, 头部标注「未 boot 验证」)
语义命名映射 SEMANTIC 把 packager id 改成用户可见的特性名;
 Deps 定义已验证的特性依赖边。
"""
import os, re

SEMANTIC = {
    'glassfish-web': 'web',
    'glassfish-jcdi': 'cdi',
    'glassfish-jpa': 'jpa',
    'glassfish-ejb-lite': 'ejb-lite',
    'glassfish-jsf': 'jsf',
    'glassfish-jdbc': 'jdbc',
    'glassfish-jta': 'jta',
    'glassfish-jts': 'jts',
    'glassfish-ha': 'ha',
    'glassfish-hazelcast': 'hazelcast',
    'hazelcast': 'hazelcast',
    'glassfish-cluster': 'cluster',
    'glassfish-corba-base': 'corba-base',
    'glassfish-grizzly-full': 'grizzly-full',
    'glassfish-management': 'management',
    'glassfish-common': 'common',
    'glassfish-common-web': 'common-web',
    'appserver-core': 'appserver-core',
    'json': 'json',
    'healthcheck': 'healthcheck',
    'requesttracing': 'requesttracing',
    'notification': 'notification',
    'asadmin-recorder': 'asadmin-recorder',
    'phonehome': 'phonehome',
    'jmx-monitoring-package': 'jmx-monitoring',
    'payara-rest-endpoints': 'payara-rest-endpoints',
    'payara-executor-service': 'payara-executor-service',
    'environment-warning': 'environment-warning',
    'payara-micro-package': 'payara-micro',
    'h2db': 'h2db',
    'jersey': 'jersey',
    'glassfish-gui': 'gui',
}

# 已 boot 验证的依赖边（勿凭记忆扩充; 新边必须先过部署验证）
DEPS = {
    'web': ['cdi'],
    'admin-rest': ['jersey'],
}

# 生成草稿里已知不可解析、需从清单剔除的 jar（与全量发行版同等状态）
EXCLUDE = {
    'cdi': ['bcel.jar'],            # 发行版无 commons-io 提供者, 全量同样不可解析
    'web': ['jsf-connector.jar', 'jersey-mvc-connector.jar'],  # JSF/MVC 属独立特性, 留给 jsf/mvc
}

def main():
    gen_dir, verified_dir, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(out_dir, exist_ok=True)
    emitted = []
    # 1) 语义命名合并（同语义名多 packager 时合并 bundle 集, 如未来 admin-rest 拆分）
    for f in sorted(os.listdir(gen_dir)):
        if not f.endswith('.properties'):
            continue
        pid = f[:-len('.properties')]
        name = SEMANTIC.get(pid, pid)
        txt = open(os.path.join(gen_dir, f), encoding='utf-8').read()
        m = re.search(r'feature\.bundles=(.+)', txt)
        if not m:
            continue
        jars = [j.strip() for j in m.group(1).split(',') if j.strip()]
        if name in EXCLUDE:
            jars = [j for j in jars if j not in EXCLUDE[name]]
        target = os.path.join(out_dir, name + '.properties')
        if name in ('web', 'cdi', 'admin-rest') and os.path.isfile(os.path.join(verified_dir, name + '.properties')):
            continue  # 用验证版
        if os.path.isfile(target):
            # 合并到已存在文件（多 packager 同名）
            cur = re.search(r'feature\.bundles=(.+)', open(target, encoding='utf-8').read()).group(1)
            merged = list(dict.fromkeys([x.strip() for x in cur.split(',')] + jars))
            txt2 = re.sub(r'feature\.bundles=.+', 'feature.bundles=' + ','.join(merged), open(target, encoding='utf-8').read(), flags=re.S)
            open(target, 'w', encoding='utf-8', newline='\n').write(txt2)
            continue
        hdr = ('# 特性: %s (生成自 %s packager; 生成草稿, 未 boot 验证)\n' % (name, pid))
        deps = DEPS.get(name)
        if deps:
            hdr += 'feature.dependencies=%s\n' % ','.join(deps)
        open(target, 'w', encoding='utf-8', newline='\n').write(hdr + 'feature.bundles=' + ','.join(jars) + '\n')
        emitted.append(name)

    # 2) 验证版覆盖（整文件替换 + 写入依赖边）
    for f in sorted(os.listdir(verified_dir)):
        if not f.endswith('.properties'):
            continue
        name = f[:-len('.properties')]
        txt = open(os.path.join(verified_dir, f), encoding='utf-8').read()
        deps = DEPS.get(name)
        if deps and 'feature.dependencies' not in txt:
            txt = txt.replace('feature.bundles=', 'feature.dependencies=%s\nfeature.bundles=' % ','.join(deps), 1)
        txt = txt.replace('(生成草稿, 未 boot 验证)', '')
        # 验证版头部第一行注释替换为验证标记
        lines = txt.split('\n')
        if lines and lines[0].startswith('#'):
            lines[0] = '# 特性: %s (boot 验证通过)' % name
            txt = '\n'.join(lines)
        open(os.path.join(out_dir, f), 'w', encoding='utf-8', newline='\n').write(txt)
        emitted.append(name + '(verified)')

    print('dist 特性清单 %d 份 → %s' % (len(emitted), out_dir))
    for e in sorted(set(emitted)):
        print('  ' + e)

if __name__ == '__main__':
    import sys
    main()

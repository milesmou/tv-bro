"""Validate release identity before signing or publishing an APK."""
import argparse
import json
import os
import re
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--github-output', type=Path)
    args = parser.parse_args()
    source = (Path(__file__).resolve().parents[1] / 'app/build.gradle.kts').read_text(encoding='utf-8')
    version = re.search(r'versionName\s*=\s*"([^"]+)"', source).group(1)
    package = re.search(r'applicationId\s*=\s*"([^"]+)"', source).group(1)
    code = int(re.search(r'versionCode\s*=\s*(\d+)', source).group(1))
    if not re.fullmatch(r'\d+\.\d+\.\d+', version) or code < 1:
        raise SystemExit('应用版本必须为 x.y.z，versionCode 必须为正整数')
    if package != 'com.milesmou.tvbrowser':
        raise SystemExit('发布包名必须为 com.milesmou.tvbrowser')
    tag = f'v{version}'
    if os.getenv('GITHUB_REF_TYPE') == 'tag' and os.getenv('GITHUB_REF_NAME') != tag:
        raise SystemExit('Git 标签与应用版本不一致')
    requested = os.getenv('REQUESTED_VERSION')
    if requested and requested != version:
        raise SystemExit('手动发布版本与应用版本不一致')
    print(json.dumps(dict(version=version, versionCode=code, applicationId=package, tag=tag)))
    if args.github_output:
        with args.github_output.open('a', encoding='utf-8') as output:
            output.write(f'version={version}\nversionCode={code}\ntag={tag}\n')


if __name__ == '__main__':
    main()

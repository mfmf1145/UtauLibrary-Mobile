#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 microsoft/fluentui-system-icons (MIT) 生成 Android VectorDrawable 图标。

OpenUtau 使用 Avalonia FluentTheme，其界面内联图标同属 Fluent 图标语言，
因此这里选用同一套图标，保证风格一致（不使用任何自创图标）。

用法:
    python3 tools/generate_icons.py
输出:
    app/src/main/res/drawable/ou_ic_*.xml
"""
import os
import re
import subprocess
import sys
import time
import urllib.parse

REPO = "microsoft/fluentui-system-icons"
BRANCH = "main"
BASE = "https://raw.githubusercontent.com/%s/%s/assets/{dir}/SVG/ic_fluent_{name}_24_{style}.svg" % (REPO, BRANCH)

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")

# (仓库中的目录名, 图标 snake 名, 输出资源名)
ICONS = [
    ("Puzzle Piece", "puzzle_piece", "ou_ic_plugin"),
    ("Mic", "mic", "ou_ic_phonemizer"),
    ("Music Note 2", "music_note_2", "ou_ic_voicebank"),
    ("Box Multiple", "box_multiple", "ou_ic_installed"),
    ("Settings", "settings", "ou_ic_settings"),
    ("Arrow Download", "arrow_download", "ou_ic_download"),
    ("Delete", "delete", "ou_ic_delete"),
    ("Folder Open", "folder_open", "ou_ic_folder"),
    ("Dismiss", "dismiss", "ou_ic_close"),
    ("Arrow Sync", "arrow_sync", "ou_ic_refresh"),
    ("Search", "search", "ou_ic_search"),
    ("Add", "add", "ou_ic_add"),
    ("Chevron Right", "chevron_right", "ou_ic_chevron_right"),
    ("Open", "open", "ou_ic_open"),
    ("Warning", "warning", "ou_ic_warning"),
    ("Info", "info", "ou_ic_info"),
    ("Weather Moon", "weather_moon", "ou_ic_moon"),
    ("Weather Sunny", "weather_sunny", "ou_ic_sun"),
    ("Document Text", "document_text", "ou_ic_document"),
    ("Globe", "globe", "ou_ic_globe"),
    ("Person", "person", "ou_ic_person"),
    ("Link", "link", "ou_ic_link"),
    ("Checkmark Circle", "checkmark_circle", "ou_ic_check"),
    ("Error Circle", "error_circle", "ou_ic_error"),
    ("More Horizontal", "more_horizontal", "ou_ic_more"),
    ("Arrow Left", "arrow_left", "ou_ic_back"),
    ("Star", "star", "ou_ic_star"),
    ("Cloud Arrow Down", "cloud_arrow_down", "ou_ic_cloud_download"),
    ("Tag", "tag", "ou_ic_tag"),
    ("Text Bullet List", "text_bullet_list", "ou_ic_list"),
    ("Shield", "shield", "ou_ic_license"),
    ("Checkmark", "checkmark", "ou_ic_checkmark"),
    ("Copy", "copy", "ou_ic_copy"),
    ("Play", "play", "ou_ic_play"),
    ("Pause", "pause", "ou_ic_pause"),
    ("Filter", "filter", "ou_ic_filter"),
    ("Arrow Upload", "arrow_upload", "ou_ic_upload"),
    ("Clock", "clock", "ou_ic_clock"),
    ("Beaker", "beaker", "ou_ic_engine"),
]

paths_re = re.compile(r"<path\b[^>]*?/?>", re.S)
d_re = re.compile(r'\bd="([^"]+)"')
fillrule_re = re.compile(r'fill-rule="([^"]+)"')
opacity_re = re.compile(r'opacity="([^"]+)"')


def fetch(url, dest):
    for _ in range(4):
        r = subprocess.run(
            ["curl", "-sSL", "--retry", "2", "-o", dest, "-w", "%{http_code}", url],
            capture_output=True, text=True, timeout=60,
        )
        if r.returncode == 0 and r.stdout.strip() == "200":
            return True
        time.sleep(1)
    return False


def main():
    os.makedirs(OUT, exist_ok=True)
    ok, fail = [], []
    for dirname, name, res in ICONS:
        url = BASE.format(dir=urllib.parse.quote(dirname), name=name, style="regular")
        tmp = "/tmp/utm_%s.svg" % res
        if not fetch(url, tmp):
            fail.append((res, "download"))
            continue
        svg = open(tmp, encoding="utf-8").read()
        body = []
        for m in paths_re.finditer(svg):
            tag = m.group(0)
            dm = d_re.search(tag)
            if not dm:
                continue
            attrs = ['android:fillColor="#FF000000"']
            fr = fillrule_re.search(tag)
            if fr and fr.group(1) == "evenodd":
                attrs.append('android:fillType="evenOdd"')
            op = opacity_re.search(tag)
            if op:
                try:
                    a = float(op.group(1))
                    if a < 1.0:
                        attrs.append('android:fillAlpha="%s"' % round(a, 4))
                except ValueError:
                    pass
            body.append('    <path %s\n        android:pathData="%s" />' % ("\n        ".join(attrs), dm.group(1)))
        if not body:
            fail.append((res, "no path"))
            continue
        xml = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Generated from Microsoft Fluent UI System Icons (MIT License).\n"
            "     Copyright (c) Microsoft Corporation.\n"
            "     Same icon family used by OpenUtau (Avalonia FluentTheme). -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="24"\n    android:viewportHeight="24">\n'
            + "\n".join(body) + "\n</vector>\n"
        )
        open(os.path.join(OUT, res + ".xml"), "w", encoding="utf-8").write(xml)
        ok.append(res)
    print("generated:", len(ok))
    if fail:
        print("failed:", fail)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

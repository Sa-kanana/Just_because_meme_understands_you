# -*- coding: utf-8 -*-
from pathlib import Path
import re

env = Path(r"d:/Code/Just_because_meme_understands_you/MemeAgent/.env")
t = env.read_text(encoding="utf-8")
adds = {
    "FIRECRAWL_BILIBILI_UPLOAD_URL": "https://space.bilibili.com/94510621/upload/video",
    "FIRECRAWL_BILIBILI_TOP_VIDEOS": "5",
    "FIRECRAWL_SCRAPE_PROXY": "auto",
}
for k, v in adds.items():
    if re.search(rf"^{k}=", t, flags=re.M):
        t = re.sub(rf"^{k}=.*$", f"{k}={v}", t, flags=re.M)
    else:
        t = t.rstrip() + f"\n{k}={v}\n"
env.write_text(t, encoding="utf-8")
print("env ok")

jp = Path(
    r"d:/Code/Just_because_meme_understands_you/just_because_meme_understands_you-backend"
    r"/src/main/resources/application-dev.yml"
)
if jp.exists():
    jt = jp.read_text(encoding="utf-8")
    if "crawl:" in jt:
        if re.search(r"cron:", jt):
            jt = re.sub(r"cron:\s*'[^']*'", "cron: '0 0 10 1,8,15,22 * *'", jt)
        else:
            jt = jt.replace(
                "schedule-enabled: true",
                "schedule-enabled: true\n    cron: '0 0 10 1,8,15,22 * *'",
                1,
            )
        jt = re.sub(r"(?m)^(\s*limit:\s*)\d+", r"\g<1>5", jt, count=1)
        jp.write_text(jt, encoding="utf-8")
        print("dev yml ok")
else:
    print("no application-dev.yml")

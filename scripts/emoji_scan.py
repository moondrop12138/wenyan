import re
import pathlib
import sys

# P0-1 emoji 检测正则（项目总监门禁用）
emoji_re = re.compile(
    "[\U0001F300-\U0001F9FF"
    "\U00002600-\U000026FF"
    "\U00002700-\U000027BF"
    "\U0000FE00-\U0000FE0F"
    "\U0001F000-\U0001F02F"
    "\U0001F0A0-\U0001F0FF"
    "\U0001F100-\U0001F64F"
    "\U0001F680-\U0001F6FF"
    "\U0001F900-\U0001F9FF"
    "\U0001FA00-\U0001FA6F"
    "\U0001FA70-\U0001FAFF"
    "\U0000200D"
    "\U000020E3"
    "\U000E0020-\U000E007F]"
)

root = pathlib.Path(r"C:/Users/Khalil/WorkBuddy/apk2/docs")
files = [f for f in root.rglob("*") if f.suffix in (".md", ".json", ".css", ".kt", ".ts", ".tsx", ".js", ".jsx", ".html", ".vue")]
fails = 0
for f in files:
    try:
        content = f.read_text(encoding="utf-8")
    except Exception:
        continue
    m = emoji_re.findall(content)
    if m:
        fails += 1
        print("FAIL", f.name, m[:5])
print(f"SCAN DONE: {len(files)} files, {fails} fails")
sys.exit(1 if fails else 0)

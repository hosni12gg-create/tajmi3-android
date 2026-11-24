"""Safe SQLite backup, keeps the last 14 copies."""
import os, sqlite3, time
from pathlib import Path
base = Path(__file__).resolve().parent
src = Path(os.environ.get("TAJMI3_DB", base / "data" / "tajmi3.db"))
out = base / "backups"; out.mkdir(exist_ok=True)
dst = out / time.strftime("tajmi3-%Y%m%d-%H%M.db")
s = sqlite3.connect(src); d = sqlite3.connect(dst); s.backup(d); d.close(); s.close()
for old in sorted(out.glob("tajmi3-*.db"))[:-14]:
    old.unlink()
print("backup ->", dst)

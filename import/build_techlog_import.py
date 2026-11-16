#!/usr/bin/env python3
"""Turn the techlog MySQL dump into a one-shot Liber Telos import file.

Does not touch the phone. Account mapping is fixed:

- 现金、硬币 → 现金
- 余额宝 → 余额宝
- 市政交通一卡通、市政交通一卡通押金 → 北京交通一卡通
- 北京招商银行储蓄卡 → 招商银行工资卡
- 招商银行银联信用卡 → 招行信用卡
- 住房公积金 → 工商银行储蓄卡
- 宜定盈 → new investment account, snapshot balance kept
- every other old account, and accounts already deleted inside the dump,
  → new closed account with balance 0 and a 2019-01-01 adjustment to 0
"""

from __future__ import annotations

import json
from collections import Counter, defaultdict
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from zoneinfo import ZoneInfo

DUMP = Path("/Users/zeyu/Workspace/blog/techlog_backup/techlog_sql/techlog.sql")
OUT = Path(__file__).resolve().parent / "techlog_historical.json"

MERGE = {
    "现金": "现金",
    "硬币": "现金",
    "余额宝": "余额宝",
    "市政交通一卡通": "北京交通一卡通",
    "市政交通一卡通押金": "北京交通一卡通",
    "北京招商银行储蓄卡": "招商银行工资卡",
    "招商银行银联信用卡": "招行信用卡",
    "住房公积金": "工商银行储蓄卡",
}
KEEP_OPEN = {"宜定盈"}
INCOME_CATS = {
    "工资薪水",
    "基金",
    "福利补贴",
    "公积金",
    "应收款",
    "利息",
    "租金",
    "报销款",
    "奖金",
    "零花",
    "退款返款",
    "赔付款",
    "兼职外快",
    "漏记款",
}
# Leaf names the app rewrites or deletes on every cold start.
SAFE_LEAF = {
    "加油": "加油（历史）",
    "公交地铁": "公交地铁（历史）",
}
KIND = {
    "现金": "CASH",
    "储蓄卡": "DEBIT",
    "信用卡": "CREDIT",
    "投资账户": "INVESTMENT",
    "网络账户": "VIRTUAL",
    "债权/债务人": "VIRTUAL",
    "公交卡": "DEBIT",
}
ICON = {
    "CASH": "cash",
    "DEBIT": "debit_card",
    "CREDIT": "credit_card",
    "INVESTMENT": "stable_fund",
    "VIRTUAL": "cash",
}
CUR = {"人民币": "CNY", "港币": "HKD", "美元": "USD"}
SHANGHAI = ZoneInfo("Asia/Shanghai")
ADJUST_AT = int(datetime(2019, 1, 1, tzinfo=SHANGHAI).timestamp() * 1000)


def parse_tuples(s: str, start: int) -> list[list[str]]:
    rows: list[list[str]] = []
    i = start
    n = len(s)

    def parse_value(i: int) -> tuple[str, int]:
        while i < n and s[i] in " \n\r\t":
            i += 1
        if s[i] == "'":
            i += 1
            buf: list[str] = []
            while i < n:
                c = s[i]
                if c == "\\" and i + 1 < n:
                    buf.append(s[i + 1])
                    i += 2
                    continue
                if c == "'":
                    if i + 1 < n and s[i + 1] == "'":
                        buf.append("'")
                        i += 2
                        continue
                    return "".join(buf), i + 1
                buf.append(c)
                i += 1
            raise RuntimeError("unterminated string")
        j = i
        while j < n and s[j] not in ",)":
            j += 1
        return s[i:j].strip(), j

    while i < n:
        while i < n and s[i] in " \n\r\t,":
            i += 1
        if i >= n or s[i] != "(":
            break
        i += 1
        vals: list[str] = []
        while True:
            v, i = parse_value(i)
            vals.append(v)
            while i < n and s[i] in " \n\r\t":
                i += 1
            if s[i] == ",":
                i += 1
                continue
            if s[i] == ")":
                i += 1
                break
            raise RuntimeError(f"bad tuple at {i}: {s[i:i+20]!r}")
        rows.append(vals)
    return rows


def insert_at(text: str, table: str) -> int:
    key = f"INSERT INTO `{table}` VALUES "
    at = text.find(key)
    if at < 0:
        raise SystemExit(f"missing {table}")
    return at + len(key)


def cents(text: str) -> int:
    return int(Decimal(text).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP) * 100)


def epoch_utc(text: str) -> int | None:
    if not text or text.startswith("0000"):
        return None
    dt = datetime.strptime(text, "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc)
    return int(dt.timestamp() * 1000)


def classify(rec_type: str, category: str, sub: str, has_from: bool, has_to: bool) -> str:
    if rec_type == "1":
        return "EXPENSE"
    if rec_type == "2":
        return "INCOME"
    if rec_type in {"3", "4", "5"}:
        return "TRANSFER"
    if rec_type == "0":
        if has_from and has_to and category in {"转账", "借出", "收款"}:
            return "TRANSFER"
        if category in INCOME_CATS and not sub:
            return "INCOME"
        return "EXPENSE"
    raise SystemExit(f"unknown recType {rec_type}")


def leaf_name(category: str, sub: str) -> tuple[str, str]:
    parent = category.strip() or "未分类"
    child = sub.strip() or parent
    child = SAFE_LEAF.get(child, child)
    return parent, child


def main() -> None:
    text = DUMP.read_text(encoding="utf-8", errors="replace")
    accounts = parse_tuples(text, insert_at(text, "account"))
    ledgers = parse_tuples(text, insert_at(text, "ledgers"))
    by_esid = {a[2]: a for a in accounts}
    known = {a[1] for a in accounts}
    missing = MERGE.keys() | KEEP_OPEN
    if missing - known:
        raise SystemExit(f"dump missing accounts {missing - known}")
    unknown_named = known - MERGE.keys() - KEEP_OPEN
    closed_named = sorted(unknown_named)

    refs: Counter[str] = Counter()
    for row in ledgers:
        for col in (8, 9):
            esid = row[col]
            if esid and esid not in by_esid:
                refs[esid] += 1
    deleted_name = {
        esid: f"已注销账户{i}"
        for i, esid in enumerate(sorted(refs), start=1)
    }

    def resolve(esid: str) -> str | None:
        if not esid:
            return None
        acc = by_esid.get(esid)
        if acc is None:
            return deleted_name[esid]
        return MERGE.get(acc[1], acc[1])

    currency_of: dict[str, str] = {}
    for acc in accounts:
        name = MERGE.get(acc[1], acc[1])
        currency_of[name] = CUR.get(acc[5], "CNY")
    for name in deleted_name.values():
        currency_of[name] = "CNY"

    new_accounts = []
    for acc in accounts:
        old_name = acc[1]
        if old_name in MERGE:
            continue
        kind_src = acc[7]
        kind = "INVESTMENT" if old_name in KEEP_OPEN else KIND[kind_src]
        balance = cents(acc[8]) if old_name in KEEP_OPEN else 0
        created = epoch_utc(acc[3]) or ADJUST_AT
        remark = (
            "历史投资账户，余额为 2018 年备份快照"
            if old_name in KEEP_OPEN
            else "已注销。2019-01-01 余额调整到 0"
        )
        new_accounts.append(
            {
                "name": old_name,
                "kind": kind,
                "currency": CUR.get(acc[5], "CNY"),
                "balanceCents": balance,
                "iconKey": ICON[kind],
                "cardNumber": acc[9],
                "remark": remark,
                "createdAt": created,
                "snapshotCents": cents(acc[8]),
                "closed": old_name not in KEEP_OPEN,
            }
        )
    for esid, name in deleted_name.items():
        new_accounts.append(
            {
                "name": name,
                "kind": "VIRTUAL",
                "currency": "CNY",
                "balanceCents": 0,
                "iconKey": "cash",
                "cardNumber": "",
                "remark": "原账本中的账户已删除。2019-01-01 余额调整到 0",
                "createdAt": ADJUST_AT,
                "snapshotCents": 0,
                "closed": True,
            }
        )

    type_counts: Counter[str] = Counter()
    self_transfers = 0
    txns = []
    for row in ledgers:
        if len(row) != 14:
            raise SystemExit(f"bad ledger width {len(row)} id={row[0]}")
        amount = cents(row[10])
        if amount <= 0:
            raise SystemExit(f"non-positive amount id={row[0]}")
        has_from, has_to = bool(row[8]), bool(row[9])
        kind = classify(row[4], row[12], row[13], has_from, has_to)
        type_counts[kind] += 1
        account = resolve(row[8]) if row[8] else None
        other = resolve(row[9]) if row[9] else None
        occurred = epoch_utc(row[2])
        if occurred is None:
            raise SystemExit(f"bad date id={row[0]}")
        created = epoch_utc(row[3]) or occurred
        note = row[6].strip()
        if kind == "TRANSFER" and (not note) and row[12] in {"借出", "收款"}:
            note = row[12]
        cur_code = {"人民币": "CNY", "港币": "HKD", "美元": "USD"}.get(row[11], row[11] or None)
        target_cur = currency_of[account or other or ""]
        if cur_code and cur_code != target_cur:
            extra = f"原币种：{row[11]}"
            note = f"{note}；{extra}" if note else extra
        item: dict = {
            "type": kind,
            "amountCents": amount,
            "note": note,
            "occurredAt": occurred,
            "createdAt": created,
        }
        if kind == "TRANSFER":
            if not account or not other:
                raise SystemExit(f"transfer missing side id={row[0]} {account} {other}")
            if account == other:
                self_transfers += 1
            item["fromAccount"] = account
            item["toAccount"] = other
        else:
            if not account:
                raise SystemExit(f"{kind} missing account id={row[0]}")
            parent, child = leaf_name(row[12], row[13])
            item["account"] = account
            item["categoryType"] = kind
            item["parent"] = parent
            item["child"] = child
        txns.append(item)

    for acc in new_accounts:
        if not acc["closed"]:
            continue
        snapshot = acc["snapshotCents"]
        txns.append(
            {
                "type": "BALANCE_ADJUSTMENT",
                "amountCents": abs(snapshot),
                "account": acc["name"],
                "adjustDirection": "INCOME" if snapshot < 0 else "EXPENSE",
                "note": "余额调整到 0",
                "occurredAt": ADJUST_AT,
                "createdAt": ADJUST_AT,
            }
        )

    for acc in new_accounts:
        acc.pop("snapshotCents")
        acc.pop("closed")

    payload = {
        "requiredAccounts": sorted(set(MERGE.values())),
        "newAccounts": new_accounts,
        "transactions": txns,
    }
    OUT.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    names = [a["name"] for a in new_accounts]
    print(f"wrote {OUT} bytes={OUT.stat().st_size}")
    print("types", dict(type_counts))
    print("txns", len(txns), "selfTransfers", self_transfers)
    print("new", len(names))
    print("closed named", closed_named)
    print("deleted placeholders", len(deleted_name), "refs", sum(refs.values()))
    print("keep", [(a["name"], a["balanceCents"], a["currency"]) for a in new_accounts if a["name"] in KEEP_OPEN])
    cats = Counter((t.get("categoryType"), t.get("parent"), t.get("child")) for t in txns if t["type"] != "TRANSFER" and t["type"] != "BALANCE_ADJUSTMENT")
    print("category pairs", len(cats))
    dangerous = [k for k in cats if k[2] in {"加油", "公交地铁", "利息"}]
    print("names that startup migration rewrites", dangerous)


if __name__ == "__main__":
    main()

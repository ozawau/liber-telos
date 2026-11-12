#!/usr/bin/env python3
"""Build liber_telos.db v8 from scraped Feidee JSON dumps."""
from __future__ import annotations

import json
import sqlite3
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "liber_telos_import.db"

PARENT_COLORS = [
    0xFFC44D7A, 0xFFE07A3D, 0xFF5B6B8C, 0xFF2F6FED,
    0xFF0EA5A4, 0xFF8B5CF6, 0xFFDC2626, 0xFF64748B,
    0xFF0D4F4F, 0xFF15803D, 0xFF2563EB, 0xFF475569,
]
ICON_BY_PARENT = {
    "职业收入": "salary",
    "其他收入": "other_in",
    "购物消费": "shop",
    "食品酒水": "food",
    "居家生活": "home",
    "行车交通": "transit",
    "子女费用": "kids",
    "休闲娱乐": "fun",
    "人情费用": "gift",
    "医疗费用": "medical",
    "其他杂项": "loss",
}


def money_to_cents(v) -> int:
    if v is None or v == "":
        return 0
    d = Decimal(str(v)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    return int(d * 100)


def map_kind(a: dict) -> str:
    t = a.get("type") or ""
    return {
        "Cash": "CASH",
        "Debit_Card": "DEBIT",
        "Credit_Card": "CREDIT",
        "Investment": "INVESTMENT",
        "Liability": "VIRTUAL_LIABILITY",
        "Fictitious": "VIRTUAL",
    }.get(t, "VIRTUAL")


def map_icon(a: dict) -> str:
    name = a.get("name") or ""
    t = a.get("type") or ""
    if "余额宝" in name:
        return "yuebao"
    if "微信" in name:
        return "wechat"
    if "花呗" in name:
        return "huabei"
    if "美团" in name:
        return "meituan"
    if "交通" in name or "一卡通" in name:
        return "transit_card"
    if t == "Cash":
        return "cash"
    if t == "Debit_Card":
        return "debit_card"
    if t == "Credit_Card":
        return "credit_card"
    if t == "Investment":
        return "stock" if "股票" in name else "stable_fund"
    return "cash"


def parse_day(v) -> int | None:
    try:
        n = int(str(v).strip())
        return n if 1 <= n <= 31 else None
    except Exception:
        return None


def credit_days(a: dict) -> tuple[int | None, int | None]:
    vo = a.get("credit_card_vo") or {}
    billing = parse_day(vo.get("billing_date"))
    days = parse_day(vo.get("repayment_days"))
    if billing is None:
        return None, None
    if days is None:
        return billing, None
    repay = billing + days
    while repay > 31:
        repay -= 31
    return billing, repay


def main() -> None:
    accounts_src = json.loads((ROOT / "accounts_flat.json").read_text())
    cats_tree = json.loads((ROOT / "categories_.json").read_text())["data"]
    txs = json.loads((ROOT / "transactions.json").read_text())

    if OUT.exists():
        OUT.unlink()
    conn = sqlite3.connect(OUT)
    cur = conn.cursor()
    cur.executescript(
        """
        CREATE TABLE android_metadata (locale TEXT);
        INSERT INTO android_metadata VALUES ('zh_CN');
        CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT);
        -- placeholder; replaced after first Room open if needed, but we keep empty and let
        -- destructive migration recreate. Better: install app first then replace after open.
        CREATE TABLE IF NOT EXISTS categories (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          name TEXT NOT NULL,
          type TEXT NOT NULL,
          parentId INTEGER,
          sortOrder INTEGER NOT NULL,
          iconKey TEXT NOT NULL,
          colorArgb INTEGER NOT NULL,
          defaultAccountId INTEGER,
          isProtected INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS accounts (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          name TEXT NOT NULL,
          kind TEXT NOT NULL,
          currency TEXT NOT NULL,
          cardNumber TEXT NOT NULL,
          issuer TEXT NOT NULL,
          remark TEXT NOT NULL,
          billingDay INTEGER,
          repaymentDay INTEGER,
          iconKey TEXT NOT NULL,
          includeInTotal INTEGER NOT NULL,
          hidden INTEGER NOT NULL,
          balanceCents INTEGER NOT NULL,
          createdAt INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS transactions (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          amountCents INTEGER NOT NULL,
          type TEXT NOT NULL,
          categoryId INTEGER,
          accountId INTEGER,
          fromAccountId INTEGER,
          toAccountId INTEGER,
          linkedTransactionId INTEGER,
          note TEXT NOT NULL,
          occurredAt INTEGER NOT NULL,
          createdAt INTEGER NOT NULL,
          updatedAt INTEGER NOT NULL,
          latitude REAL,
          longitude REAL
        );
        """
    )

    feidee_to_local: dict[str, int] = {}
    protected_income = None
    protected_expense = None

    for sort_i, p in enumerate(cats_tree):
        typ = "INCOME" if p.get("type") == "Income" else "EXPENSE"
        color = PARENT_COLORS[sort_i % len(PARENT_COLORS)]
        icon = ICON_BY_PARENT.get(p["name"], "custom")
        cur.execute(
            "INSERT INTO categories(name,type,parentId,sortOrder,iconKey,colorArgb,defaultAccountId,isProtected) VALUES (?,?,?,?,?,?,?,?)",
            (p["name"], typ, None, sort_i, icon, color, None, 0),
        )
        pid = cur.lastrowid
        feidee_to_local[p["id"]] = pid
        for j, ch in enumerate(p.get("sub_categories") or []):
            is_prot = 1 if ch["name"] in ("其他支出", "其他收入") else 0
            cur.execute(
                "INSERT INTO categories(name,type,parentId,sortOrder,iconKey,colorArgb,defaultAccountId,isProtected) VALUES (?,?,?,?,?,?,?,?)",
                (ch["name"], typ, pid, j, icon, color, None, is_prot),
            )
            cid = cur.lastrowid
            feidee_to_local[ch["id"]] = cid
            if ch["name"] == "其他支出" and typ == "EXPENSE":
                protected_expense = cid
            if ch["name"] == "其他收入" and typ == "INCOME":
                protected_income = cid
        if p["name"] == "其他收入" and protected_income is None:
            cur.execute(
                "INSERT INTO categories(name,type,parentId,sortOrder,iconKey,colorArgb,defaultAccountId,isProtected) VALUES (?,?,?,?,?,?,?,?)",
                ("其他收入", "INCOME", pid, 100, icon, color, None, 1),
            )
            protected_income = cur.lastrowid
        if p["name"] == "其他杂项" and protected_expense is None:
            cur.execute(
                "INSERT INTO categories(name,type,parentId,sortOrder,iconKey,colorArgb,defaultAccountId,isProtected) VALUES (?,?,?,?,?,?,?,?)",
                ("其他支出", "EXPENSE", pid, 100, icon, color, None, 1),
            )
            protected_expense = cur.lastrowid

    assert protected_income and protected_expense

    feidee_acc_to_local: dict[str, int] = {}
    for a in accounts_src:
        name = (a.get("name") or "").strip()
        if not name:
            continue
        kind = map_kind(a)
        icon = map_icon(a)
        bal = money_to_cents(a.get("balance"))
        hidden = 1 if a.get("hidden") else 0
        currency = (a.get("currency") or {}).get("code") or "CNY"
        include = 0 if hidden else 1
        if a.get("counted_out_assets") is False:
            include = 0
        remark = (a.get("remark") or "").strip()
        card = a.get("card") or {}
        card_no = (card.get("card_no") or "").strip()
        issuer = (card.get("name") or "").strip()
        billing_day, repayment_day = (None, None)
        if kind == "CREDIT":
            billing_day, repayment_day = credit_days(a)
        created = int(a.get("created_time") or a.get("updated_time") or 0) or 1700000000000
        cur.execute(
            """
            INSERT INTO accounts(
              name,kind,currency,cardNumber,issuer,remark,billingDay,repaymentDay,
              iconKey,includeInTotal,hidden,balanceCents,createdAt
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            (
                name,
                kind,
                currency,
                card_no,
                issuer,
                remark,
                billing_day,
                repayment_day,
                icon,
                include,
                hidden,
                bal,
                created,
            ),
        )
        feidee_acc_to_local[a["id"]] = cur.lastrowid

    def acc_id(obj):
        if not obj or not obj.get("id"):
            return None
        return feidee_acc_to_local.get(obj["id"])

    def cat_id(obj):
        if not obj or not obj.get("id"):
            return None
        return feidee_to_local.get(obj["id"])

    def pick_amount(t):
        for k in [
            "amount",
            "expense",
            "income",
            "outbound",
            "inbound",
            "from_amount",
            "to_amount",
            "exchange_amount",
        ]:
            v = t.get(k)
            if v is None or v == "":
                continue
            cents = money_to_cents(v)
            if cents != 0:
                return abs(cents)
        return 0

    inserted = 0
    skipped = 0
    for t in sorted(txs, key=lambda x: int(x.get("transaction_time") or 0)):
        bt = t.get("business_type")
        occurred = int(t.get("transaction_time") or 0)
        created = int(t.get("created_time") or occurred or 0)
        updated = int(t.get("updated_time") or created or 0)
        note = (t.get("remark") or "").strip()
        amt = pick_amount(t)

        def insert_row(
            amount_cents,
            typ,
            category_id,
            account_id,
            from_id,
            to_id,
            note_text,
        ):
            nonlocal inserted
            cur.execute(
                """
                INSERT INTO transactions(
                  amountCents,type,categoryId,accountId,fromAccountId,toAccountId,
                  linkedTransactionId,note,occurredAt,createdAt,updatedAt,latitude,longitude
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    amount_cents,
                    typ,
                    category_id,
                    account_id,
                    from_id,
                    to_id,
                    None,
                    note_text,
                    occurred,
                    created,
                    updated,
                    None,
                    None,
                ),
            )
            inserted += 1

        if bt == "Expense":
            if amt == 0:
                skipped += 1
                continue
            insert_row(
                amt,
                "EXPENSE",
                cat_id(t.get("category")) or protected_expense,
                acc_id(t.get("account")) or acc_id(t.get("from_account")),
                None,
                None,
                note,
            )
        elif bt == "Income":
            if amt == 0:
                skipped += 1
                continue
            insert_row(
                amt,
                "INCOME",
                cat_id(t.get("category")) or protected_income,
                acc_id(t.get("account")) or acc_id(t.get("to_account")),
                None,
                None,
                note,
            )
        elif bt == "Transfer":
            amt = money_to_cents(
                t.get("from_amount") or t.get("to_amount") or t.get("amount") or 0
            )
            if amt == 0:
                skipped += 1
                continue
            insert_row(
                amt,
                "TRANSFER",
                None,
                None,
                acc_id(t.get("from_account")) or acc_id(t.get("account")),
                acc_id(t.get("to_account")),
                note or "转账",
            )
        elif bt in ("Balance_Changed", "Liability_Changed"):
            raw = money_to_cents(
                t.get("amount")
                or t.get("exchange_amount")
                or t.get("to_amount")
                or t.get("from_amount")
                or 0
            )
            aid = (
                acc_id(t.get("account"))
                or acc_id(t.get("to_account"))
                or acc_id(t.get("from_account"))
            )
            if raw == 0:
                skipped += 1
                continue
            if bt == "Balance_Changed":
                to_a = money_to_cents(t.get("to_amount") or 0)
                fr_a = money_to_cents(t.get("from_amount") or 0)
                delta = to_a - fr_a if t.get("to_amount") is not None else raw
                if delta >= 0:
                    cid, use_amt = protected_income, abs(delta) or abs(raw)
                else:
                    cid, use_amt = protected_expense, abs(delta)
                n = note or "余额变更"
            else:
                cid, use_amt, n = protected_expense, abs(raw), note or "负债变更"
            # 初始余额 / 负债变更：不计入收入支出
            insert_row(use_amt, "BALANCE_ADJUSTMENT", cid, aid, None, None, n)
        elif bt == "Refund":
            if amt == 0:
                skipped += 1
                continue
            row = cur.execute(
                "SELECT id FROM categories WHERE name='平台退款' AND type='INCOME'"
            ).fetchone()
            cid = row[0] if row else protected_income
            insert_row(
                amt,
                "INCOME",
                cid,
                acc_id(t.get("account")) or acc_id(t.get("from_account")),
                None,
                None,
                note or "退款",
            )
        else:
            skipped += 1

    # Room identity hash — leave empty table; app uses destructive migration on version bump
    # After install, Room recreates schema then we can push again with matching hash.
    # Simpler path used below in shell: install app (creates v8 empty), then overwrite file,
    # then also write room_master_table from a freshly opened empty DB if needed.
    conn.commit()
    print(
        "wrote",
        OUT,
        "accounts",
        cur.execute("select count(*) from accounts").fetchone()[0],
        "categories",
        cur.execute("select count(*) from categories").fetchone()[0],
        "txns",
        inserted,
        "skipped",
        skipped,
    )
    # sample credit
    print(
        "credit sample",
        cur.execute(
            "select name,remark,billingDay,repaymentDay,issuer,cardNumber from accounts where kind='CREDIT'"
        ).fetchall(),
    )
    print(
        "remark sample",
        cur.execute(
            "select name,remark from accounts where remark!='' limit 5"
        ).fetchall(),
    )
    conn.close()


if __name__ == "__main__":
    main()

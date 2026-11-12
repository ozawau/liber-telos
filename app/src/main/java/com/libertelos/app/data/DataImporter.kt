package com.libertelos.app.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipInputStream

object DataImporter {
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    fun parseCsvZip(file: File): ExportBundle = file.inputStream().use { parseCsvZip(it) }

    fun parseCsvZip(input: InputStream): ExportBundle {
        var accountsCsv: String? = null
        var categoriesCsv: String? = null
        var transactionsCsv: String? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.substringAfterLast('/').lowercase(Locale.US)
                val text = zip.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF")
                when (name) {
                    "accounts.csv" -> accountsCsv = text
                    "categories.csv" -> categoriesCsv = text
                    "transactions.csv" -> transactionsCsv = text
                }
                zip.closeEntry()
            }
        }
        require(accountsCsv != null && categoriesCsv != null && transactionsCsv != null) {
            "ZIP 需包含 accounts.csv / categories.csv / transactions.csv"
        }
        return ExportBundle(
            accounts = parseAccountsCsv(accountsCsv!!),
            categories = parseCategoriesCsv(categoriesCsv!!),
            transactions = parseTransactionsCsv(transactionsCsv!!)
        )
    }

    fun parseExcel(file: File): ExportBundle = file.inputStream().use { parseExcel(it) }

    fun parseExcel(input: InputStream): ExportBundle {
        val parts = linkedMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        val shared = parseSharedStrings(parts["xl/sharedStrings.xml"])
        val sheetMap = parseWorkbookSheets(parts["xl/workbook.xml"])
        fun sheetRows(name: String): List<List<String>> {
            val path = sheetMap[name] ?: error("缺少工作表：$name")
            val xml = parts[path] ?: parts["xl/$path"] ?: error("缺少 sheet：$path")
            return parseSheet(xml, shared)
        }
        val accountRows = sheetRows("账户")
        val categoryRows = sheetRows("分类")
        val txnRows = sheetRows("流水")
        return ExportBundle(
            accounts = parseAccountsTable(accountRows),
            categories = parseCategoriesTable(categoryRows),
            transactions = parseTransactionsTable(txnRows)
        )
    }

    private fun parseAccountsCsv(text: String): List<Account> {
        val table = parseCsv(text)
        return parseAccountsTable(table)
    }

    private fun parseCategoriesCsv(text: String): List<Category> {
        val table = parseCsv(text)
        return parseCategoriesTable(table)
    }

    private fun parseTransactionsCsv(text: String): List<Transaction> {
        val table = parseCsv(text)
        return parseTransactionsTable(table)
    }

    private fun parseAccountsTable(table: List<List<String>>): List<Account> {
        if (table.isEmpty()) return emptyList()
        val header = table.first().map { normalizeHeader(it) }
        val idx = headerIndex(header)
        return table.drop(1).mapNotNull { row ->
            val id = cell(row, idx, "id")?.toLongOrNull() ?: return@mapNotNull null
            val name = cell(row, idx, "name", "名称")?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val kind = parseKind(cell(row, idx, "kind", "类型"))
            Account(
                id = id,
                name = name,
                kind = kind,
                currency = cell(row, idx, "currency", "币种")?.ifBlank { "CNY" } ?: "CNY",
                cardNumber = cell(row, idx, "cardNumber", "卡号").orEmpty(),
                issuer = cell(row, idx, "issuer", "发卡行").orEmpty(),
                remark = cell(row, idx, "remark", "备注").orEmpty(),
                billingDay = cell(row, idx, "billingDay", "账单日")?.toIntOrNull(),
                repaymentDay = cell(row, idx, "repaymentDay", "还款日")?.toIntOrNull(),
                iconKey = cell(row, idx, "iconKey", "图标")?.ifBlank { "cash" } ?: "cash",
                includeInTotal = parseBool(cell(row, idx, "includeInTotal", "计入总账"), true),
                hidden = parseBool(cell(row, idx, "hidden", "隐藏"), false),
                balanceCents = parseYuanCents(cell(row, idx, "balanceYuan", "余额")),
                createdAt = parseTime(cell(row, idx, "createdAt", "创建时间"))
            )
        }
    }

    private fun parseCategoriesTable(table: List<List<String>>): List<Category> {
        if (table.isEmpty()) return emptyList()
        val header = table.first().map { normalizeHeader(it) }
        val idx = headerIndex(header)
        return table.drop(1).mapNotNull { row ->
            val id = cell(row, idx, "id")?.toLongOrNull() ?: return@mapNotNull null
            val name = cell(row, idx, "name", "名称")?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            Category(
                id = id,
                name = name,
                type = parseTxnType(cell(row, idx, "type", "收支类型")),
                parentId = cell(row, idx, "parentId", "上级ID")?.toLongOrNull(),
                sortOrder = cell(row, idx, "sortOrder", "排序")?.toIntOrNull() ?: 0,
                iconKey = cell(row, idx, "iconKey", "图标").orEmpty(),
                colorArgb = 0xFF64748BL,
                defaultAccountId = cell(row, idx, "defaultAccountId", "默认账户ID")?.toLongOrNull(),
                isProtected = parseBool(cell(row, idx, "isProtected", "系统保护"), false)
            )
        }
    }

    private fun parseTransactionsTable(table: List<List<String>>): List<Transaction> {
        if (table.isEmpty()) return emptyList()
        val header = table.first().map { normalizeHeader(it) }
        val idx = headerIndex(header)
        return table.drop(1).mapNotNull { row ->
            val id = cell(row, idx, "id")?.toLongOrNull() ?: return@mapNotNull null
            val type = parseTxnType(cell(row, idx, "type", "类型"))
            val amount = parseYuanCents(cell(row, idx, "amountYuan", "金额"))
            if (amount <= 0L && type != TxnType.BALANCE_ADJUSTMENT) {
                // 仍允许导入 0？跳过无效行
            }
            Transaction(
                id = id,
                amountCents = amount,
                type = type,
                categoryId = cell(row, idx, "categoryId", "分类ID")?.toLongOrNull(),
                accountId = cell(row, idx, "accountId", "账户ID")?.toLongOrNull(),
                fromAccountId = cell(row, idx, "fromAccountId", "转出账户ID")?.toLongOrNull(),
                toAccountId = cell(row, idx, "toAccountId", "转入账户ID")?.toLongOrNull(),
                linkedTransactionId = cell(row, idx, "linkedTransactionId", "关联流水ID")
                    ?.toLongOrNull(),
                note = cell(row, idx, "note", "备注").orEmpty(),
                occurredAt = parseTime(cell(row, idx, "occurredAt", "发生时间")),
                createdAt = parseTime(cell(row, idx, "createdAt", "创建时间")),
                updatedAt = parseTime(cell(row, idx, "updatedAt", "修改时间")),
                latitude = cell(row, idx, "latitude")?.toDoubleOrNull(),
                longitude = cell(row, idx, "longitude")?.toDoubleOrNull(),
                locationAddress = cell(row, idx, "locationAddress", "地址").orEmpty()
            )
        }
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val reader = BufferedReader(InputStreamReader(ByteArrayInputStream(text.toByteArray())))
        var line = reader.readLine() ?: return emptyList()
        // handle multiline quoted fields simply by joining until quotes balance
        while (true) {
            var current = line
            while (current.count { it == '"' } % 2 != 0) {
                val next = reader.readLine() ?: break
                current += "\n" + next
            }
            rows += splitCsvLine(current)
            line = reader.readLine() ?: break
        }
        return rows.filter { it.any { cell -> cell.isNotBlank() } }
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    out += sb.toString()
                    sb.clear()
                }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val list = ArrayList<String>()
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }
        var inT = false
        val sb = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") {
                        inT = true
                        sb.clear()
                    } else if (parser.name == "si") {
                        sb.clear()
                    }
                }
                XmlPullParser.TEXT -> if (inT) sb.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") inT = false
                    if (parser.name == "si") {
                        list += sb.toString()
                        sb.clear()
                    }
                }
            }
            parser.next()
        }
        return list
    }

    private fun parseWorkbookSheets(bytes: ByteArray?): Map<String, String> {
        require(bytes != null) { "无效的 Excel 文件" }
        val map = linkedMapOf<String, String>()
        val idToTarget = linkedMapOf<String, String>()
        // sheet path defaults
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }
        var index = 1
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet$index"
                val rId = parser.getAttributeValue(
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                    "id"
                )
                val path = "xl/worksheets/sheet$index.xml"
                map[name] = path
                if (rId != null) idToTarget[rId] = path
                index++
            }
            parser.next()
        }
        return map
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }
        var currentRow = ArrayList<String>()
        var inV = false
        var cellType = ""
        var value = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = ArrayList()
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        value = StringBuilder()
                    }
                    "v", "t" -> inV = true
                }
                XmlPullParser.TEXT -> if (inV) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> inV = false
                    "c" -> {
                        val raw = value.toString()
                        val text = if (cellType == "s") {
                            raw.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                        } else {
                            raw
                        }
                        currentRow += text
                    }
                    "row" -> if (currentRow.isNotEmpty()) rows += currentRow.toList()
                }
            }
            parser.next()
        }
        return rows
    }

    private fun headerIndex(header: List<String>): Map<String, Int> =
        header.mapIndexed { i, h -> h to i }.toMap()

    private fun normalizeHeader(raw: String): String =
        raw.trim().lowercase(Locale.ROOT)
            .replace(" ", "")
            .replace("_", "")

    private fun cell(row: List<String>, idx: Map<String, Int>, vararg keys: String): String? {
        for (key in keys) {
            val i = idx[normalizeHeader(key)] ?: continue
            if (i in row.indices) return row[i]
        }
        return null
    }

    private fun parseKind(raw: String?): AccountKind {
        val text = raw?.trim().orEmpty()
        AccountKind.entries.firstOrNull { it.label == text }?.let { return it }
        AccountKind.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }?.let { return it }
        return AccountKind.VIRTUAL
    }

    private fun parseTxnType(raw: String?): TxnType {
        return when (raw?.trim()) {
            "支出", "EXPENSE" -> TxnType.EXPENSE
            "收入", "INCOME" -> TxnType.INCOME
            "转账", "TRANSFER" -> TxnType.TRANSFER
            "报销", "REIMBURSEMENT" -> TxnType.REIMBURSEMENT
            "余额变更", "BALANCE_ADJUSTMENT" -> TxnType.BALANCE_ADJUSTMENT
            else -> runCatching { TxnType.valueOf(raw.orEmpty()) }.getOrDefault(TxnType.EXPENSE)
        }
    }

    private fun parseBool(raw: String?, default: Boolean): Boolean {
        val text = raw?.trim()?.lowercase(Locale.ROOT) ?: return default
        return when (text) {
            "1", "true", "yes", "y", "是" -> true
            "0", "false", "no", "n", "否" -> false
            else -> default
        }
    }

    private fun parseYuanCents(raw: String?): Long {
        val text = raw?.trim()?.replace(",", "").orEmpty()
        if (text.isEmpty()) return 0L
        val yuan = text.toDoubleOrNull() ?: return 0L
        return Math.round(yuan * 100.0)
    }

    private fun parseTime(raw: String?): Long {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return System.currentTimeMillis()
        text.toLongOrNull()?.let { return it }
        return runCatching { timeFmt.parse(text)?.time }.getOrNull()
            ?: System.currentTimeMillis()
    }
}

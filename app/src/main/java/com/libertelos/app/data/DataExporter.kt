package com.libertelos.app.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportBundle(
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Transaction>
)

object DataExporter {
    private val stampFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    fun stamp(): String = stampFmt.format(Date())

    fun writeCsvZip(target: File, bundle: ExportBundle) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
            zipCsv(zip, "accounts.csv", accountCsv(bundle.accounts))
            zipCsv(zip, "categories.csv", categoryCsv(bundle.categories))
            zipCsv(zip, "transactions.csv", transactionCsv(bundle.transactions, bundle))
        }
    }

    fun writeExcel(target: File, bundle: ExportBundle) {
        val sheets = listOf(
            SheetData("账户", accountRows(bundle.accounts)),
            SheetData("分类", categoryRows(bundle.categories)),
            SheetData("流水", transactionRows(bundle.transactions, bundle))
        )
        SimpleXlsxWriter.write(target, sheets)
    }

    private fun zipCsv(zip: ZipOutputStream, name: String, body: String) {
        val bytes = ("\uFEFF$body").toByteArray(Charsets.UTF_8)
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun accountCsv(accounts: List<Account>): String {
        val header = listOf(
            "id", "name", "kind", "currency", "cardNumber", "issuer", "remark",
            "billingDay", "repaymentDay", "iconKey", "includeInTotal", "hidden",
            "balanceYuan", "createdAt"
        )
        val rows = accounts.map { a ->
            listOf(
                a.id.toString(),
                a.name,
                a.kind.label,
                a.currency,
                a.cardNumber,
                a.issuer,
                a.remark,
                a.billingDay?.toString().orEmpty(),
                a.repaymentDay?.toString().orEmpty(),
                a.iconKey,
                if (a.includeInTotal) "1" else "0",
                if (a.hidden) "1" else "0",
                yuan(a.balanceCents),
                formatTime(a.createdAt)
            )
        }
        return toCsv(header, rows)
    }

    private fun categoryCsv(categories: List<Category>): String {
        val byId = categories.associateBy { it.id }
        val header = listOf(
            "id", "name", "type", "parentId", "parentName", "sortOrder",
            "iconKey", "defaultAccountId", "isProtected"
        )
        val rows = categories.map { c ->
            listOf(
                c.id.toString(),
                c.name,
                typeLabel(c.type),
                c.parentId?.toString().orEmpty(),
                c.parentId?.let { byId[it]?.name }.orEmpty(),
                c.sortOrder.toString(),
                c.iconKey,
                c.defaultAccountId?.toString().orEmpty(),
                if (c.isProtected) "1" else "0"
            )
        }
        return toCsv(header, rows)
    }

    private fun transactionCsv(
        transactions: List<Transaction>,
        bundle: ExportBundle
    ): String {
        val cats = bundle.categories.associateBy { it.id }
        val accounts = bundle.accounts.associateBy { it.id }
        val header = listOf(
            "id", "type", "amountYuan", "categoryId", "category", "parentCategory",
            "accountId", "account", "fromAccountId", "fromAccount",
            "toAccountId", "toAccount", "linkedTransactionId", "note",
            "occurredAt", "createdAt", "updatedAt", "latitude", "longitude", "locationAddress"
        )
        val rows = transactions.map { t ->
            val cat = t.categoryId?.let { cats[it] }
            val parent = cat?.parentId?.let { cats[it] }
            listOf(
                t.id.toString(),
                typeLabel(t.type),
                yuan(t.amountCents),
                t.categoryId?.toString().orEmpty(),
                cat?.name.orEmpty(),
                parent?.name.orEmpty(),
                t.accountId?.toString().orEmpty(),
                t.accountId?.let { accounts[it]?.name }.orEmpty(),
                t.fromAccountId?.toString().orEmpty(),
                t.fromAccountId?.let { accounts[it]?.name }.orEmpty(),
                t.toAccountId?.toString().orEmpty(),
                t.toAccountId?.let { accounts[it]?.name }.orEmpty(),
                t.linkedTransactionId?.toString().orEmpty(),
                t.note,
                formatTime(t.occurredAt),
                formatTime(t.createdAt),
                formatTime(t.updatedAt),
                t.latitude?.toString().orEmpty(),
                t.longitude?.toString().orEmpty(),
                t.locationAddress
            )
        }
        return toCsv(header, rows)
    }

    private fun accountRows(accounts: List<Account>): List<List<String>> {
        val header = listOf(
            "ID", "名称", "类型", "币种", "卡号", "发卡行", "备注",
            "账单日", "还款日", "图标", "计入总账", "隐藏", "余额", "创建时间"
        )
        return listOf(header) + accounts.map { a ->
            listOf(
                a.id.toString(),
                a.name,
                a.kind.label,
                a.currency,
                a.cardNumber,
                a.issuer,
                a.remark,
                a.billingDay?.toString().orEmpty(),
                a.repaymentDay?.toString().orEmpty(),
                a.iconKey,
                if (a.includeInTotal) "是" else "否",
                if (a.hidden) "是" else "否",
                yuan(a.balanceCents),
                formatTime(a.createdAt)
            )
        }
    }

    private fun categoryRows(categories: List<Category>): List<List<String>> {
        val byId = categories.associateBy { it.id }
        val header = listOf(
            "ID", "名称", "收支类型", "上级ID", "上级名称", "排序",
            "图标", "默认账户ID", "系统保护"
        )
        return listOf(header) + categories.map { c ->
            listOf(
                c.id.toString(),
                c.name,
                typeLabel(c.type),
                c.parentId?.toString().orEmpty(),
                c.parentId?.let { byId[it]?.name }.orEmpty(),
                c.sortOrder.toString(),
                c.iconKey,
                c.defaultAccountId?.toString().orEmpty(),
                if (c.isProtected) "是" else "否"
            )
        }
    }

    private fun transactionRows(
        transactions: List<Transaction>,
        bundle: ExportBundle
    ): List<List<String>> {
        val cats = bundle.categories.associateBy { it.id }
        val accounts = bundle.accounts.associateBy { it.id }
        val header = listOf(
            "ID", "类型", "金额",
            "分类ID", "分类", "一级分类",
            "账户ID", "账户", "转出账户ID", "转出账户", "转入账户ID", "转入账户",
            "关联流水ID", "备注", "发生时间", "创建时间", "修改时间", "地址"
        )
        return listOf(header) + transactions.map { t ->
            val cat = t.categoryId?.let { cats[it] }
            val parent = cat?.parentId?.let { cats[it] }
            listOf(
                t.id.toString(),
                typeLabel(t.type),
                yuan(t.amountCents),
                t.categoryId?.toString().orEmpty(),
                cat?.name.orEmpty(),
                parent?.name.orEmpty(),
                t.accountId?.toString().orEmpty(),
                t.accountId?.let { accounts[it]?.name }.orEmpty(),
                t.fromAccountId?.toString().orEmpty(),
                t.fromAccountId?.let { accounts[it]?.name }.orEmpty(),
                t.toAccountId?.toString().orEmpty(),
                t.toAccountId?.let { accounts[it]?.name }.orEmpty(),
                t.linkedTransactionId?.toString().orEmpty(),
                t.note,
                formatTime(t.occurredAt),
                formatTime(t.createdAt),
                formatTime(t.updatedAt),
                t.locationAddress
            )
        }
    }

    private fun toCsv(header: List<String>, rows: List<List<String>>): String =
        buildString {
            appendLine(header.joinToString(",") { escapeCsv(it) })
            rows.forEach { row ->
                appendLine(row.joinToString(",") { escapeCsv(it) })
            }
        }

    private fun escapeCsv(value: String): String {
        val needsQuote = value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    private fun yuan(cents: Long): String =
        String.format(Locale.CHINA, "%.2f", cents / 100.0)

    private fun formatTime(ms: Long): String = timeFmt.format(Date(ms))

    private fun typeLabel(type: TxnType): String = when (type) {
        TxnType.EXPENSE -> "支出"
        TxnType.INCOME -> "收入"
        TxnType.TRANSFER -> "转账"
        TxnType.REIMBURSEMENT -> "报销"
        TxnType.BALANCE_ADJUSTMENT -> "余额变更"
    }
}

private data class SheetData(val name: String, val rows: List<List<String>>)

/** 极简 xlsx 写入（多 sheet），无第三方依赖。 */
private object SimpleXlsxWriter {
    fun write(target: File, sheets: List<SheetData>) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
            fun put(path: String, body: String) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }

            put(
                "[Content_Types].xml",
                buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                    append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                    append("""<Default Extension="xml" ContentType="application/xml"/>""")
                    append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                    sheets.indices.forEach { i ->
                        append("""<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                    }
                    append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
                    append("""</Types>""")
                }
            )

            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )

            put(
                "xl/_rels/workbook.xml.rels",
                buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                    sheets.indices.forEach { i ->
                        append("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
                    }
                    append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
                    append("""</Relationships>""")
                }
            )

            put(
                "xl/workbook.xml",
                buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
                    append("<sheets>")
                    sheets.forEachIndexed { i, sheet ->
                        append("""<sheet name="${xmlEscape(sheet.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
                    }
                    append("</sheets></workbook>")
                }
            )

            val shared = LinkedHashMap<String, Int>()
            fun sid(value: String): Int =
                shared.getOrPut(value) { shared.size }

            sheets.forEach { sheet ->
                sheet.rows.forEach { row ->
                    row.forEach { sid(it) }
                }
            }

            put(
                "xl/sharedStrings.xml",
                buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${shared.size}" uniqueCount="${shared.size}">""")
                    shared.keys.forEach { s ->
                        append("<si><t>${xmlEscape(s)}</t></si>")
                    }
                    append("</sst>")
                }
            )

            sheets.forEachIndexed { index, sheet ->
                put(
                    "xl/worksheets/sheet${index + 1}.xml",
                    buildString {
                        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                        append("<sheetData>")
                        sheet.rows.forEachIndexed { rIndex, row ->
                            append("""<row r="${rIndex + 1}">""")
                            row.forEachIndexed { cIndex, cell ->
                                val ref = cellRef(cIndex, rIndex)
                                val idx = sid(cell)
                                append("""<c r="$ref" t="s"><v>$idx</v></c>""")
                            }
                            append("</row>")
                        }
                        append("</sheetData></worksheet>")
                    }
                )
            }
        }
    }

    private fun cellRef(col: Int, row: Int): String {
        var c = col
        val sb = StringBuilder()
        while (c >= 0) {
            sb.insert(0, ('A'.code + c % 26).toChar())
            c = c / 26 - 1
        }
        return sb.toString() + (row + 1)
    }

    private fun xmlEscape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\u0009', '\n', '\r' -> append(ch)
                else -> if (ch.code in 0x20..0xD7FF ||
                    ch.code in 0xE000..0xFFFD
                ) {
                    append(ch)
                }
            }
        }
    }
}

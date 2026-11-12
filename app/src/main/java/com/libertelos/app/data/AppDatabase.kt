package com.libertelos.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun fromTxnType(value: TxnType): String = value.name

    @TypeConverter
    fun toTxnType(value: String): TxnType = TxnType.valueOf(value)

    @TypeConverter
    fun fromAccountKind(value: AccountKind): String = value.name

    @TypeConverter
    fun toAccountKind(value: String): AccountKind = AccountKind.valueOf(value)
}

@Database(
    entities = [Category::class, Account::class, Transaction::class],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN locationAddress TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            LedgerStorage.migrateFromLegacyIfNeeded(context)
            val dbFile = LedgerStorage.databaseFile(context)
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                dbFile.absolutePath
            )
                .addMigrations(MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}

object DefaultCategories {
    val expenseGroups = listOf(
        CategorySeedGroup(
            name = "购物",
            iconKey = "shop",
            colorArgb = 0xFFC44D7A,
            children = listOf("日常用品", "礼品纪念品", "买菜原料", "医疗药品")
        ),
        CategorySeedGroup(
            name = "餐饮",
            iconKey = "food",
            colorArgb = 0xFFE07A3D,
            children = listOf("早午晚餐", "零食饮料")
        ),
        CategorySeedGroup(
            name = "居家",
            iconKey = "home",
            colorArgb = 0xFF5B6B8C,
            children = listOf("水电燃气", "网费", "手机话费", "物业费", "房租", "房屋修缮", "房贷还款")
        ),
        CategorySeedGroup(
            name = "交通",
            iconKey = "transit",
            colorArgb = 0xFF2F6FED,
            children = listOf("公交", "地铁", "打车", "火车", "飞机", "共享单车", "共享电动车")
        ),
        CategorySeedGroup(
            name = "子女",
            iconKey = "kids",
            colorArgb = 0xFF0EA5A4,
            children = listOf("零食", "衣服", "日常用品", "学费", "培训费")
        ),
        CategorySeedGroup(
            name = "娱乐",
            iconKey = "fun",
            colorArgb = 0xFF8B5CF6,
            children = listOf("电影", "聚会", "棋牌", "运动")
        ),
        CategorySeedGroup(
            name = "人情",
            iconKey = "gift",
            colorArgb = 0xFFDC2626,
            children = listOf("请客", "婚丧嫁娶", "孝敬长辈")
        ),
        CategorySeedGroup(
            name = "医疗",
            iconKey = "medical",
            colorArgb = 0xFF0EA5A4,
            children = listOf("门诊", "住院", "药品")
        ),
        CategorySeedGroup(
            name = "投资",
            iconKey = "invest",
            colorArgb = 0xFF2563EB,
            children = listOf("理财亏损")
        ),
        CategorySeedGroup(
            name = "其他",
            iconKey = "loss",
            colorArgb = 0xFF64748B,
            children = listOf("丢失", "其他支出"),
            protectedChildren = setOf("其他支出")
        )
    )

    /** 收入两级分类（暂定，可按你的清单再改） */
    val incomeGroups = listOf(
        CategorySeedGroup(
            name = "工资",
            iconKey = "salary",
            colorArgb = 0xFF0D4F4F,
            children = listOf("基本工资", "加班费", "兼职收入")
        ),
        CategorySeedGroup(
            name = "奖金",
            iconKey = "bonus",
            colorArgb = 0xFF15803D,
            children = listOf("年终奖", "绩效奖金", "红包")
        ),
        CategorySeedGroup(
            name = "投资",
            iconKey = "invest",
            colorArgb = 0xFF2563EB,
            children = listOf("利息收入", "理财收入", "股息分红")
        ),
        CategorySeedGroup(
            name = "报销退款",
            iconKey = "other_in",
            colorArgb = 0xFF0EA5A4,
            children = listOf("财务报销", "平台退款")
        ),
        CategorySeedGroup(
            name = "其他",
            iconKey = "other_in",
            colorArgb = 0xFF475569,
            children = listOf("其他收入"),
            protectedChildren = setOf("其他收入")
        )
    )

    suspend fun seed(dao: CategoryDao) {
        if (dao.count() > 0) return
        seedGroups(dao, TxnType.EXPENSE, expenseGroups)
        seedGroups(dao, TxnType.INCOME, incomeGroups)
    }

    private suspend fun seedGroups(
        dao: CategoryDao,
        type: TxnType,
        groups: List<CategorySeedGroup>
    ) {
        groups.forEachIndexed { index, group ->
            val parentId = dao.insert(
                Category(
                    name = group.name,
                    type = type,
                    parentId = null,
                    sortOrder = index,
                    iconKey = group.iconKey,
                    colorArgb = group.colorArgb
                )
            )
            group.children.forEachIndexed { childIndex, childName ->
                dao.insert(
                    Category(
                        name = childName,
                        type = type,
                        parentId = parentId,
                        sortOrder = childIndex,
                        iconKey = group.iconKey,
                        colorArgb = group.colorArgb,
                        isProtected = childName in group.protectedChildren
                    )
                )
            }
        }
    }

    fun groupByParent(categories: List<Category>): List<CategoryGroup> {
        val parents = categories.filter { it.isParent }.sortedBy { it.sortOrder }
        val children = categories.filter { it.isLeaf }.groupBy { it.parentId }
        return parents.map { parent ->
            CategoryGroup(
                parent = parent,
                children = children[parent.id].orEmpty().sortedBy { it.sortOrder }
            )
        }
    }
}

object DefaultAccounts {
    val all = listOf(
        Account(
            name = "现金",
            kind = AccountKind.CASH,
            currency = "CNY",
            iconKey = "cash",
            includeInTotal = true
        ),
        Account(
            name = "微信余额",
            kind = AccountKind.VIRTUAL,
            currency = "CNY",
            issuer = "微信支付",
            iconKey = "wechat",
            includeInTotal = true
        )
    )
}

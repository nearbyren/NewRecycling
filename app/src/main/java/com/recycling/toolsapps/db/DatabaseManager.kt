package com.recycling.toolsapps.db

/**
 * @author: lr
 * @created on: 2024/8/29 10:45 PM
 * @description:
 */
import android.content.Context
import android.os.Environment
import android.provider.ContactsContract.Directory.PACKAGE_NAME
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.recycling.toolsapps.dao.ConfigFlowDao
import com.recycling.toolsapps.dao.FileFlowDao
import com.recycling.toolsapps.dao.LatticeFlowDao
import com.recycling.toolsapps.dao.LogFlowDao
import com.recycling.toolsapps.dao.ResFlowDao
import com.recycling.toolsapps.dao.StateFlowDao
import com.recycling.toolsapps.dao.TransFlowDao
import com.recycling.toolsapps.dao.WeightFlowDao
import com.recycling.toolsapps.http.MailConfig
import com.recycling.toolsapps.http.MailSender
import com.recycling.toolsapps.model.ConfigEntity
import com.recycling.toolsapps.model.FileEntity
import com.recycling.toolsapps.model.LatticeEntity
import com.recycling.toolsapps.model.LogEntity
import com.recycling.toolsapps.model.ResEntity
import com.recycling.toolsapps.model.StateEntity
import com.recycling.toolsapps.model.TransEntity
import com.recycling.toolsapps.model.WeightEntity
import com.serial.port.utils.AppUtils
import com.serial.port.utils.Loge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val s = "TABLE"

object DatabaseManager {
    private const val TAG = "DatabaseManager"

    private const val DATABASE_NAME = "recycling_database"
    private const val DATABASE_PATH = "/data/data/com.recycling.toolsapps/databases/"

    @Volatile
    private var instance: SQLDatabase? = null
    private fun getDatabase(context: Context): SQLDatabase {
        return instance ?: synchronized(this) {
            //升级数据添加字段
            val MIGRATION_1_2 = object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // 执行 ALTER TABLE 添加新字段
//                    database.execSQL("ALTER TABLE BoxDevice ADD COLUMN boxUTime TEXT DEFAULT ''")
//                    database.execSQL("ALTER TABLE ResEntity ADD COLUMN sn TEXT DEFAULT NULL")
//                    database.execSQL("ALTER TABLE FileEntity ADD COLUMN status INTEGER NOT NULL DEFAULT 0")
                }
            }
            val MIGRATION_2_3 = object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // 执行 ALTER TABLE 添加新字段
                    database.execSQL("ALTER TABLE ResEntity ADD COLUMN cmd TEXT DEFAULT ''")
                    database.execSQL("ALTER TABLE ResEntity ADD COLUMN version TEXT DEFAULT ''")
                    database.execSQL("ALTER TABLE ResEntity ADD COLUMN sn TEXT DEFAULT ''")
                }
            }
            val MIGRATION_3_4 = object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // 执行 ALTER TABLE 添加新字段
                    database.execSQL("ALTER TABLE WeightEntity ADD COLUMN cabinId TEXT DEFAULT ''")
                }
            }
            val MIGRATION_4_5 = object : Migration(4, 5) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // 执行 ALTER TABLE 添加新字段
                    database.execSQL("ALTER TABLE LatticeEntity ADD COLUMN time TEXT DEFAULT ''")
                    database.execSQL("ALTER TABLE ConfigEntity ADD COLUMN time TEXT DEFAULT ''")
                }
            }

            val MIGRATION_5_6 = object : Migration(5, 6) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // 执行 ALTER TABLE 添加新字段
                    database.execSQL("ALTER TABLE WeightEntity ADD COLUMN openType INTEGER NOT NULL DEFAULT 0")
                }
            }
            val MIGRATION_6_7 = object : Migration(6, 7) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // 执行 ALTER TABLE 添加新字段
                    database.execSQL("ALTER TABLE LatticeEntity ADD COLUMN closeCount INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE LatticeEntity ADD COLUMN weightPercent INTEGER NOT NULL DEFAULT 0")
                }
            }
            val MIGRATION_7_8 = object : Migration(8, 9) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // 执行 ALTER TABLE 添加新字段
//                    database.execSQL("ALTER TABLE StateEntity ADD COLUMN newField REAL DEFAULT 0.0")
//                    database.execSQL("ALTER TABLE WeightEntity ADD COLUMN conWeigh TEXT ")
                }
            }
            val MIGRATION_7_1 = object : Migration(9, 10) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // 执行 ALTER TABLE 添加新字段
//                    database.execSQL("ALTER TABLE StateEntity ADD COLUMN newField REAL DEFAULT 0.0")
//                    database.execSQL("ALTER TABLE WeightEntity ADD COLUMN conWeigh TEXT ")
                }
            }
            val MIGRATION_10_11 = object : Migration(11, 12) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // 执行 ALTER TABLE 添加新字段
//                    database.execSQL("ALTER TABLE StateEntity ADD COLUMN weighY REAL NOT NULL DEFAULT 0.0 ")
//                    database.execSQL("ALTER TABLE WeightEntity ADD COLUMN curWeightY TEXT DEFAULT ''")
                }
            }
            // 数据库名称
            val newInstance =
                Room.databaseBuilder(
                    context.applicationContext,
                    SQLDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_10_11).addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            //初始化信息
//                                    initState()
                        }
                    }
                }).build()
            instance = newInstance
            newInstance
        }

    }

    /**
     * 复制整个 databases 目录到外部存储
     */
    fun copyDatabasesDirectory(context: Context, targetDirName: String = "socket_box_crash") {
        try {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    // 1. 获取源目录和目标目录
                    val sourceDir = getDatabasesDirectory(context)
                    val targetDir = getTargetDirectory(context, targetDirName)

                    Loge.d(TAG, "开始复制数据库目录...")
                    Loge.d(TAG, "源目录: ${sourceDir.absolutePath}")
                    Loge.d(TAG, "目标目录: ${targetDir.absolutePath}")

                    // 2. 验证源目录是否存在
                    if (!sourceDir.exists() || !sourceDir.isDirectory) {
                        Loge.e(TAG, "源目录不存在或不是目录")
                        return@launch
                    }

                    // 3. 确保目标目录存在
                    if (!targetDir.exists() && !targetDir.mkdirs()) {
                        Loge.e(TAG, "无法创建目标目录")
                        return@launch
                    }

                    // 4. 复制整个目录
                    val success = copyDirectoryRecursively(sourceDir, targetDir)

                    if (success) {
                        Loge.d(TAG, "数据库目录复制成功")

                        // 5. 验证复制结果
                        val verificationResult = verifyCopyResult(sourceDir, targetDir)

                        withContext(Dispatchers.Main) {
                            if (verificationResult.success) {
                                // 复制成功，可以发送通知或回调
                                onCopyComplete(verificationResult)
                            } else {
                                Loge.e(TAG, "复制验证失败: ${verificationResult.message}")
                            }
                        }
                    } else {
                        Loge.e(TAG, "数据库目录复制失败")
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Loge.e(TAG, "复制数据库目录异常: ${e.message}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Loge.e(TAG, "启动复制协程失败: ${e.message}")
        }
    }

    /**
     * 获取 databases 目录
     */
    private fun getDatabasesDirectory(context: Context): File {
        // 方法1：使用 Context 获取数据库目录
        val databasesDir = context.getDatabasePath(DATABASE_NAME).parentFile
        if (databasesDir != null && databasesDir.exists()) {
            return databasesDir
        }

        // 方法2：使用包名构建路径
        val fallbackPath = "/data/data/$PACKAGE_NAME/databases/"
        val fallbackDir = File(fallbackPath)

        if (fallbackDir.exists()) {
            return fallbackDir
        }

        // 方法3：使用 applicationInfo 获取数据目录
        val appInfo = context.applicationInfo
        val dataDir = File(appInfo.dataDir, "databases")

        if (dataDir.exists()) {
            return dataDir
        }

        // 如果都不存在，返回第一个路径
        return fallbackDir
    }

    /**
     * 获取目标目录
     */
    private fun getTargetDirectory(context: Context, dirName: String): File {
        // 优先使用外部存储
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (externalDir != null) {
            val fResult = File(externalDir, "$dirName/db")
            if (!fResult.exists()) {
                Loge.e(TAG, "创建目录: ${fResult.absolutePath}")
                fResult.mkdirs() // 创建 log 目录
            }
            return fResult
        }

        // 如果外部存储不可用，使用内部存储
        val internalDir = context.filesDir
        return File(internalDir, dirName)
    }

    /**
     * 递归复制目录
     */
    private fun copyDirectoryRecursively(sourceDir: File, targetDir: File): Boolean {
        return try {
            // 确保目标目录存在
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                Loge.e(TAG, "无法创建目标目录: ${targetDir.absolutePath}")
                return false
            }

            // 遍历源目录中的所有文件
            val sourceFiles = sourceDir.listFiles() ?: emptyArray()
            var successCount = 0
            var totalCount = 0

            for (sourceFile in sourceFiles) {
                totalCount++
                val targetFile = File(targetDir, sourceFile.name)

                if (sourceFile.isDirectory) {
                    // 如果是目录，递归复制
                    if (copyDirectoryRecursively(sourceFile, targetFile)) {
                        successCount++
                    }
                } else {
                    // 如果是文件，复制文件
                    if (copyFileWithProgress(sourceFile, targetFile)) {
                        successCount++
                    }
                }
            }

            val success = successCount == totalCount
            if (!success) {
                Loge.w(TAG, "部分文件复制失败: $successCount/$totalCount")
            }

            // 即使部分失败也返回成功，因为我们可能只需要主数据库文件
            successCount > 0
        } catch (e: Exception) {
            e.printStackTrace()
            Loge.e(TAG, "递归复制目录失败: ${e.message}")
            false
        }
    }

    /**
     * 复制单个文件（带进度跟踪）
     */
    private fun copyFileWithProgress(sourceFile: File, targetFile: File): Boolean {
        return try {
            // 如果目标文件已存在，先删除
            if (targetFile.exists()) {
                targetFile.delete()
            }

            var totalBytesCopied = 0L
            val totalBytes = sourceFile.length()

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192) // 8KB 缓冲区
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesCopied += bytesRead

                        // 可以在这里发送进度更新（可选）
                        if (totalBytes > 0) {
                            val progress = (totalBytesCopied * 100 / totalBytes).toInt()
                            if (progress % 25 == 0) { // 每25%记录一次
                                Loge.d(TAG, "复制 ${sourceFile.name}: $progress%")
                            }
                        }
                    }
                    output.flush()
                }
            }

            // 验证文件大小
            val sourceSize = sourceFile.length()
            val targetSize = targetFile.length()

            if (sourceSize == targetSize) {
                Loge.d(TAG, "文件复制成功: ${sourceFile.name} (${sourceSize} 字节)")
                true
            } else {
                Loge.e(
                    TAG,
                    "文件大小不匹配: ${sourceFile.name} (源: $sourceSize, 目标: $targetSize)"
                )
                targetFile.delete()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Loge.e(TAG, "复制文件失败 ${sourceFile.name}: ${e.message}")
            false
        }
    }

    /**
     * 验证复制结果
     */
    private fun verifyCopyResult(sourceDir: File, targetDir: File): CopyVerificationResult {
        return try {
            // 获取源目录中的所有文件
            val sourceFiles = getAllFilesInDirectory(sourceDir)
            val targetFiles = getAllFilesInDirectory(targetDir)

            // 检查文件数量
            if (sourceFiles.size != targetFiles.size) {
                return CopyVerificationResult(
                    success = false,
                    message = "文件数量不匹配 (源: ${sourceFiles.size}, 目标: ${targetFiles.size})"
                )
            }

            // 检查每个文件的大小
            val mismatchedFiles = mutableListOf<String>()
            for ((index, sourceFile) in sourceFiles.withIndex()) {
                val targetFile = targetFiles[index]

                if (sourceFile.name != targetFile.name) {
                    mismatchedFiles.add("文件名不匹配: ${sourceFile.name} vs ${targetFile.name}")
                } else if (sourceFile.length() != targetFile.length()) {
                    mismatchedFiles.add("文件大小不匹配: ${sourceFile.name} (源: ${sourceFile.length()}, 目标: ${targetFile.length()})")
                }
            }

            if (mismatchedFiles.isNotEmpty()) {
                return CopyVerificationResult(
                    success = false,
                    message = "文件验证失败: ${mismatchedFiles.joinToString("; ")}"
                )
            }

            // 额外检查：尝试打开主数据库文件
            val mainDbFile = targetFiles.find { it.name == DATABASE_NAME }
            mainDbFile?.let { dbFile ->
                if (!verifyDatabaseFile(dbFile)) {
                    return CopyVerificationResult(
                        success = false,
                        message = "主数据库文件验证失败"
                    )
                }
            }

            CopyVerificationResult(
                success = true,
                message = "复制验证通过",
                sourceFileCount = sourceFiles.size,
                targetFileCount = targetFiles.size,
                totalSize = targetFiles.sumOf { it.length() }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            CopyVerificationResult(
                success = false,
                message = "验证过程中出错: ${e.message}"
            )
        }
    }

    /**
     * 获取目录中的所有文件（递归）
     */
    private fun getAllFilesInDirectory(directory: File): List<File> {
        val files = mutableListOf<File>()

        if (!directory.exists() || !directory.isDirectory) {
            return files
        }

        directory.walk().filter { it.isFile }.forEach { file ->
            files.add(file)
        }

        return files
    }

    /**
     * 验证数据库文件
     */
    private fun verifyDatabaseFile(dbFile: File): Boolean {
        return try {
            // 检查文件是否存在且有内容
            if (!dbFile.exists() || dbFile.length() == 0L) {
                return false
            }

            // 可以添加更复杂的验证，比如检查 SQLite 文件头
            val buffer = ByteArray(16)
            FileInputStream(dbFile).use { input ->
                val bytesRead = input.read(buffer)
                if (bytesRead >= 16) {
                    // SQLite 文件头通常以 "SQLite format 3" 开头
                    val header = String(buffer, Charsets.UTF_8)
                    if (header.startsWith("SQLite format 3")) {
                        return true
                    }
                }
            }

            // 如果文件头检查失败，至少文件存在且有内容
            dbFile.length() > 0
        } catch (e: Exception) {
            Loge.e(TAG, "验证数据库文件失败: ${e.message}")
            false
        }
    }

    /**
     * 复制完成回调（在主线程执行）
     */
    private fun onCopyComplete(result: CopyVerificationResult) {
        // 这里可以更新 UI 或发送通知
        Loge.d(TAG, "复制完成: ${result.message}")

        // 示例：发送通知
        sendCopyCompleteNotification(result)
    }

    /**
     * 发送复制完成通知
     */
    private fun sendCopyCompleteNotification(result: CopyVerificationResult) {
        val context = AppUtils.getContext()

        val notificationTitle = if (result.success) {
            "数据库备份完成"
        } else {
            "数据库备份失败"
        }

        val notificationText = if (result.success) {
            "成功备份 ${result.sourceFileCount} 个文件 (${formatFileSize(result.totalSize)})"
        } else {
            result.message
        }

        // 使用 NotificationCompat 创建通知
        // 这里需要实现具体的通知逻辑
        Loge.d(TAG, "$notificationTitle: $notificationText")
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size >= 1024 * 1024 -> "${String.format("%.2f", size.toDouble() / (1024 * 1024))} MB"
            size >= 1024 -> "${String.format("%.2f", size.toDouble() / 1024)} KB"
            else -> "$size B"
        }
    }

    /**
     * 兼容旧代码的方法（复制单个数据库文件）
     */
    @Deprecated(
        "请使用 copyDatabasesDirectory 方法",
        ReplaceWith("copyDatabasesDirectory(context, databaseName)")
    )
    fun copyDatabase(context: Context, databaseName: String = DATABASE_NAME) {
        copyDatabasesDirectory(context, "socket_box_crash")
    }

    /**
     * 旧版的 copyFile 方法（保留兼容性）
     */
    private fun copyFile(inputFile: File, outputFile: File) {
        copyFileWithProgress(inputFile, outputFile)
    }

    /**
     * 备份结果数据类
     */
    data class CopyVerificationResult(
        val success: Boolean,
        val message: String,
        val sourceFileCount: Int = 0,
        val targetFileCount: Int = 0,
        val totalSize: Long = 0L,
    )


    private suspend fun senMail(file: File) {
        val mailConfig = MailConfig.Builder().apply {
            host = "smtp.qq.com"
            port = 587
//                    port = 465
            username = "860023654@qq.com"
            password = "raiszbpinaznbbjd" // 或 oauthToken("ya29.token")
            setRecipient("860023654@qq.com")
            setSubject("拷贝db文件")
            setBody("<b>主要查看附件信息</b>")
            setAttach(file)
        }.build()
        when (val result = MailSender.sendDirectly(mailConfig)) {
            is MailSender.Result.Success -> Loge.d("发送邮件 发送成功")
            is MailSender.Result.Failure -> Loge.d("发送邮件 ${result.exception}")
        }
    }

    /***************************************获取 箱体 实例*************************************************/
    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @return 返回日志dao
     */
    private fun getLatticeFlowDao(context: Context): LatticeFlowDao {
        return getDatabase(context).latticeFlow()
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     */
    fun queryLattices(context: Context): List<LatticeEntity> {
        return getLatticeFlowDao(context).queryLattices()
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param latticeEntity
     */
    fun insertLattice(context: Context, latticeEntity: LatticeEntity): Long {
        return getLatticeFlowDao(context).insert(latticeEntity)
    }

    /**
     * 提供外部 API 方法
     * @param context 上下文
     * @param latticeEntity
     * @return
     */
    fun upLatticeEntity(context: Context, latticeEntity: LatticeEntity): Int {
        return getLatticeFlowDao(context).upLatticeEntity(latticeEntity)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param cabinId
     * @return
     */
    fun queryLatticeEntity(context: Context, cabinId: String): LatticeEntity {
        return getLatticeFlowDao(context).queryLatticeEntity(cabinId)
    }
    /***************************************获取 箱体 实例2 *************************************************/
    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @return 返回日志dao
     */
    private fun getStateFlowDao(context: Context): StateFlowDao {
        return getDatabase(context).stateFlow()
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param stateEntity
     */
    fun insertState(context: Context, stateEntity: StateEntity): Long {
        return getStateFlowDao(context).insert(stateEntity)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param cabinId
     * @return
     */
    fun queryStateEntity(context: Context, cabinId: String): StateEntity {
        return getStateFlowDao(context).queryStateEntity(cabinId)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param doorStatus
     * @param cabinId
     */
    fun upStateStatus(context: Context, doorStatus: Int, cabinId: String) {
        return getStateFlowDao(context).upStateStatus(doorStatus, cabinId)
    }

    /**
     * 提供外部 API 方法
     * @param context 上下文
     * @param stateEntity
     * @return
     */
    fun upStateEntity(context: Context, stateEntity: StateEntity): Int {
        return getStateFlowDao(context).upStateEntity(stateEntity)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param cabinId
     * @return
     */
    fun queryStateList(context: Context): List<StateEntity> {
        return getStateFlowDao(context).queryStateList()
    }

    /***************************************获取 箱体 实例*************************************************/

    /***************************************获取 初始化配置 实例*************************************************/
    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @return 返回日志dao
     */
    private fun getInitConfigFlowDao(context: Context): ConfigFlowDao {
        return getDatabase(context).initConfigFlow()
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param configEntity
     */
    fun insertConfig(context: Context, configEntity: ConfigEntity): Long {
        return getInitConfigFlowDao(context).insert(configEntity)
    }

    /**
     * 提供外部 API 方法
     * @param context 上下文
     * @param configEntity
     * @return
     */
    fun upConfigEntity(context: Context, configEntity: ConfigEntity): Int {
        return getInitConfigFlowDao(context).upConfigEntity(configEntity)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param sn
     * @return
     */
    fun queryInitConfig(context: Context, sn: String): ConfigEntity {
        return getInitConfigFlowDao(context).queryInitConfig(sn)
    }

    /***************************************获取 初始化配置 实例*************************************************/

    /***************************************获取 事务记录 实例*************************************************/
    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @return
     */
    private fun getTransFlowDao(context: Context): TransFlowDao {
        return getDatabase(context).transFlowFlow()
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param trensEntity 插入一条记录
     */
    fun insertTrans(context: Context, trensEntity: TransEntity): Long {
        return getTransFlowDao(context).insert(trensEntity)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param closeStatus
     * @param transId
     */
    fun upTransCloseStatus(context: Context, closeStatus: Int, transId: String) {
        getTransFlowDao(context).upTransCloseStatus(closeStatus, transId)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param openStatus
     * @param transId
     */
    fun upTransOpenStatus(context: Context, openStatus: Int, transId: String) {
        getTransFlowDao(context).upTransOpenStatus(openStatus, transId)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param openStatus
     */
    fun queryTransOpenStatus(context: Context, openStatus: Int): List<TransEntity> {
        return getTransFlowDao(context).queryTransOpenStatus(openStatus)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     */
    fun queryTransMax(context: Context): TransEntity {
        return getTransFlowDao(context).queryTransMax()
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param transId
     */
    fun queryTransEntity(context: Context, transId: String): TransEntity {
        return getTransFlowDao(context).queryTransEntity(transId)
    }

    /**
     * 提供外部 API 方法
     * @param context 上下文
     * @param transEntity
     * @return
     */
    fun upTransEntity(context: Context, transEntity: TransEntity): Int {
        return getTransFlowDao(context).upTransEntity(transEntity)
    }
    /***************************************获取 打开仓 实例*************************************************/

    /***************************************获取 记录当前重量 实例*************************************************/
    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @return
     */
    private fun getWeightFlowDao(context: Context): WeightFlowDao {
        return getDatabase(context).weightFlowDao()
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param weightEntity 插入一条记录
     */
    fun insertWeight(context: Context, weightEntity: WeightEntity): Long {
        return getWeightFlowDao(context).insert(weightEntity)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param status
     */
    fun queryWeightStatus(context: Context, status: Int): List<WeightEntity> {
        return getWeightFlowDao(context).queryWeightStatus(status)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param transId
     */
    fun queryWeightId(context: Context, transId: String): WeightEntity {
        return getWeightFlowDao(context).queryWeightId(transId)
    }

    /**
     * 提供外部 API 方法
     * @param context 上下文
     * @param weightEntity
     * @return
     */
    fun upWeightEntity(context: Context, weightEntity: WeightEntity): Int {
        return getWeightFlowDao(context).upWeightEntity(weightEntity)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     */
    fun queryWeightMax(context: Context): WeightEntity {
        return getWeightFlowDao(context).queryWeightMax()
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param status  10.进行中 1.完成
     * @param transId 事务id
     */
    fun upWeightStatus(context: Context, status: Int, transId: String) {
        getWeightFlowDao(context).upWeightStatus(status, transId)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param transId
     */
    fun queryWeightEntity(context: Context, transId: String): WeightEntity {
        return getWeightFlowDao(context).queryWeightEntity(transId)
    }

    /***************************************获取 记录当前重量*************************************************/

    /***************************************获取 文件上传 实例*************************************************/
    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @return
     */
    private fun getFileFlowDao(context: Context): FileFlowDao {
        return getDatabase(context).fileFlowDao()
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param fileEntity 插入一条记录
     */
    fun insertFile(context: Context, fileEntity: FileEntity): Long {
        return getFileFlowDao(context).insert(fileEntity)
    }

    /**
     * 提供外部 API 方法
     * @param context 上下文
     * @param cmd
     * @param transId
     * @return
     */
    fun queryFileEntity(context: Context, cmd: String, transId: String): FileEntity {
        return getFileFlowDao(context).queryFileEntity(cmd, transId)
    }

    /**
     * 提供外部 API 方法
     * @param context 上下文
     * @param FileEntity
     * @return
     */
    fun upFileEntity(context: Context, fileEntity: FileEntity): Int {
        return getFileFlowDao(context).upFileEntity(fileEntity)
    }

    /***************************************获取 文件上传*************************************************/

    /***************************************获取 资源 实例*************************************************/
    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @return
     */
    private fun getResFlowDao(context: Context): ResFlowDao {
        return getDatabase(context).resFlowDao()
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param resourceEntity 插入一条记录
     */
    fun insertRes(context: Context, resourceEntity: ResEntity): Long {
        return getResFlowDao(context).insert(resourceEntity)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param filename
     * @return
     */
    fun queryResName(context: Context, filename: String): ResEntity {
        return getResFlowDao(context).queryResName(filename)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param version
     * @param sn
     * @param cmd
     * @return
     */
    fun queryResCmd(context: Context, version: String, sn: String, cmd: String): ResEntity {
        return getResFlowDao(context).queryResCmd(version, sn, cmd)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param version
     * @param sn
     * @param cmd
     * @return
     */
    fun queryResCmd(context: Context, version: String, cmd: String): ResEntity {
        return getResFlowDao(context).queryResCmd(version, cmd)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param version
     * @return
     */
    fun queryResVersion(context: Context, version: String): ResEntity {
        return getResFlowDao(context).queryResVersion(version)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     */
    fun queryResMax(context: Context): ResEntity {
        return getResFlowDao(context).queryResEntityMax()
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param cmd
     */
    fun queryResNewAPk(context: Context, cmd: String): ResEntity {
        return getResFlowDao(context).queryResNewAPk(cmd)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param cmd
     */
    fun queryResNewBin(context: Context, sn: String, cmd: String): ResEntity {
        return getResFlowDao(context).queryResNewBin(sn, cmd)
    }

    /**
     * 提供外部 API 方法
     * @param context 上下文
     * @param resourceEntity
     * @return
     */
    fun upResEntity(context: Context, resourceEntity: ResEntity): Int {
        return getResFlowDao(context).upResEntity(resourceEntity)
    }

    /**
     * 提供外部 API 方法
     * @param context 上下文
     * @param id
     * @param status
     * @return
     */
    fun upResStatus(context: Context, id: Long, status: Int) {
        getResFlowDao(context).upResStatus(id, status)
    }

    /***************************************获取 资源*************************************************/

    /***************************************获取 日志记录 实例*************************************************/
    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @return
     */
    private fun getLogInfoFlowDao(context: Context): LogFlowDao {
        return getDatabase(context).logFlow()
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @param
     */
    fun insertLog(context: Context, logEntity: LogEntity): Long {
        return getLogInfoFlowDao(context).insert(logEntity)
    }

    /***
     * 提供外部 API 方法
     * @param context 上下文
     * @return
     */
    fun queryLogs(context: Context): Flow<List<LogEntity>> {
        return getLogInfoFlowDao(context).queryLoginInfos()
    }

    /***************************************获取 日志记录 实例*************************************************/

}

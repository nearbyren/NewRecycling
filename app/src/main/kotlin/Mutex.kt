import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

@RequiresApi(Build.VERSION_CODES.O) fun main() {

//    LicenseGenerator.main()
    runBlocking {
        val queue = TaskQueue<Int>()

        // 启动生产者协程
        repeat(3) { producerId ->
            launch(Dispatchers.Default) {
                repeat(5) {a->
                    val task = Random.nextInt(100)
//                    delay(150) // 模拟任务生成耗时
                    queue.enqueue(task)
                   println("Producer $producerId enqueued: $a-${task}")
                }
            }
        }

        // 启动消费者协程
        repeat(2) { consumerId ->
            launch(Dispatchers.Default) {
                repeat(7) { // 消费总数 > 生产总数，测试挂起逻辑
                    val task = queue.dequeue()
//                    delay(100) // 模拟任务处理耗时
                   println("Consumer $consumerId processed: $task")
                }
            }
        }

        delay(3000) // 等待所有协程完成
    }



    // 创建任务队列
    val downloadQueue = TaskQueue<Int>()
    runBlocking {
        // 生产者协程
        launch {
            listOf(100, 200, 300, 400, 500, 600, 700, 800, 900).forEach { url ->
                val task = createDownloadTask(url)
                downloadQueue.enqueue(task)
            }
        }

// 消费者协程（支持多个并行消费者）
        repeat(4) {
            launch {
                while (true) {
                    val task = downloadQueue.dequeue()
                    processDownload(task)
                }
            }
        }
    }

}

fun createDownloadTask(url: Int): Int {
    val task = Random.nextInt(url, url + 100)
   println("测试 createDownloadTask task = $task")
    return task
}

fun processDownload(task: Int) {
   println("测试 processDownload task = $task")
}
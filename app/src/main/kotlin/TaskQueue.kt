import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskQueue<T> {
    // 互斥锁保护队列和等待者列表
    private val mutex = Mutex()

    // 存储待处理任务的队列
    private val taskQueue = ArrayDeque<T>()

    // 存储等待任务的协程列表
    private val waiters = ArrayDeque<CompletableDeferred<T>>()

    /**
     * 添加任务到队列
     * 如果有等待的消费者，直接将任务传递给第一个等待者
     * 否则将任务加入队列缓存
     */
    suspend fun enqueue(task: T) {
        mutex.withLock {
            if (waiters.isNotEmpty()) {
                val waiter = waiters.removeFirst()
                waiter.complete(task) // 直接唤醒等待的消费者
            } else {
                taskQueue.addLast(task) // 缓存任务
            }
        }
    }

    /**
     * 从队列获取任务
     * 如果队列为空则挂起协程，直到有新任务到达
     */
    suspend fun dequeue(): T {
        val deferred = CompletableDeferred<T>()

        // 尝试快速获取任务（队列非空时）
        mutex.withLock {
            if (taskQueue.isNotEmpty()) {
                return taskQueue.removeFirst()
            }
            waiters.add(deferred) // 加入等待队列
        }

        // 挂起等待生产者唤醒
        return deferred.await()
    }

    /**
     * 获取当前队列大小（调试用）
     */
    suspend fun size(): Int = mutex.withLock { taskQueue.size }
}
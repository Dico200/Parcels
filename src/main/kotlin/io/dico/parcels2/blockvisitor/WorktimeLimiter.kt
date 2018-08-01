package io.dico.parcels2.blockvisitor

import io.dico.parcels2.ParcelsPlugin
import io.dico.parcels2.util.FunctionHelper
import kotlinx.coroutines.experimental.CancellationException
import kotlinx.coroutines.experimental.Job
import org.bukkit.scheduler.BukkitTask
import java.lang.System.currentTimeMillis
import java.util.*
import java.util.logging.Level
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineUninterceptedOrReturn

typealias TimeLimitedTask = suspend WorkerScope.() -> Unit
typealias WorkerUpdateLister = Worker.(Double, Long) -> Unit

data class TickWorktimeOptions(var workTime: Int, var tickInterval: Int)

sealed class WorktimeLimiter {
    /**
     * Submit a [task] that should be run synchronously, but limited such that it does not stall the server
     * a bunch
     */
    abstract fun submit(task: TimeLimitedTask): Worker

    /**
     * Get a list of all workers
     */
    abstract val workers: List<Worker>
}

interface Timed {
    /**
     * The time that elapsed since this worker was dispatched, in milliseconds
     */
    val elapsedTime: Long
}

interface Worker : Timed {
    /**
     * The coroutine associated with this worker, if any
     */
    val job: Job?

    /**
     * true if this worker has completed
     */
    val isComplete: Boolean

    /**
     * If an exception was thrown during the execution of this task,
     * returns that exception. Returns null otherwise.
     */
    val completionException: Throwable?

    /**
     * A value indicating the progress of this worker, in the range 0.0 <= progress <= 1.0
     * with no guarantees to its accuracy. May be null.
     */
    val progress: Double?

    /**
     * Calls the given [block] whenever the progress of this worker is updated,
     * if [minInterval] milliseconds expired since the last call.
     * The first call occurs after at least [minDelay] milliseconds in a likewise manner.
     * Repeated invocations of this method result in an [IllegalStateException]
     *
     * if [asCompletionListener] is true, [onCompleted] is called with the same [block]
     */
    fun onProgressUpdate(minDelay: Int, minInterval: Int, asCompletionListener: Boolean = true, block: WorkerUpdateLister): Worker

    /**
     * Calls the given [block] when this worker completes, with the progress value 1.0.
     * Repeated invocations of this method result in an [IllegalStateException]
     */
    fun onCompleted(block: WorkerUpdateLister): Worker
}

interface WorkerScope : Timed {
    /**
     * A task should call this frequently during its execution, such that the timer can suspend it when necessary.
     */
    suspend fun markSuspensionPoint()

    /**
     * A task should call this method to indicate its progress
     */
    fun setProgress(progress: Double)
}

private interface WorkerContinuation : Worker, WorkerScope {
    /**
     * Start or resume the execution of this worker
     * returns true if the worker completed
     */
    fun resume(worktime: Long): Boolean
}

/**
 * An object that controls one or more jobs, ensuring that they don't stall the server too much.
 * There is a configurable maxiumum amount of milliseconds that can be allocated to all workers together in each server tick
 * This object attempts to split that maximum amount of milliseconds equally between all jobs
 */
class TickWorktimeLimiter(private val plugin: ParcelsPlugin, var options: TickWorktimeOptions) : WorktimeLimiter() {
    // The currently registered bukkit scheduler task
    private var bukkitTask: BukkitTask? = null
    // The workers.
    private var _workers = LinkedList<WorkerContinuation>()
    override val workers: List<Worker> = _workers

    override fun submit(task: TimeLimitedTask): Worker {
        val worker: WorkerContinuation = WorkerImpl(plugin.functionHelper, task)
        _workers.addFirst(worker)
        if (bukkitTask == null) bukkitTask = plugin.functionHelper.scheduleRepeating(0, options.tickInterval) { tickJobs() }
        return worker
    }

    private fun tickJobs() {
        val workers = _workers
        if (workers.isEmpty()) return
        val tickStartTime = System.currentTimeMillis()

        val iterator = workers.listIterator(index = 0)
        while (iterator.hasNext()) {
            val time = System.currentTimeMillis()
            val timeElapsed = time - tickStartTime
            val timeLeft = options.workTime - timeElapsed
            if (timeLeft <= 0) return

            val count = workers.size - iterator.nextIndex()
            val timePerJob = (timeLeft + count - 1) / count
            val worker = iterator.next()
            val completed = worker.resume(timePerJob)
            if (completed) {
                iterator.remove()
            }
        }

        if (workers.isEmpty()) {
            bukkitTask?.cancel()
            bukkitTask = null
        }
    }

}

private class WorkerImpl(val functionHelper: FunctionHelper,
                         val task: TimeLimitedTask) : WorkerContinuation {
    override var job: Job? = null; private set

    override val elapsedTime
        get() = job?.let {
            if (it.isCompleted) startTimeOrElapsedTime
            else currentTimeMillis() - startTimeOrElapsedTime
        } ?: 0L

    override val isComplete get() = job?.isCompleted == true

    override var completionException: Throwable? = null; private set

    override var progress: Double? = null; private set

    private var startTimeOrElapsedTime: Long = 0L // startTime before completed, elapsed time otherwise
    private var onProgressUpdate: WorkerUpdateLister? = null
    private var progressUpdateInterval: Int = 0
    private var lastUpdateTime: Long = 0L
    private var onCompleted: WorkerUpdateLister? = null
    private var continuation: Continuation<Unit>? = null
    private var nextSuspensionTime: Long = 0L

    private fun initJob(job: Job) {
        this.job?.let { throw IllegalStateException() }
        this.job = job
        startTimeOrElapsedTime = System.currentTimeMillis()
        job.invokeOnCompletion { exception ->
            // report any error that occurred
            completionException = exception?.also {
                if (it !is CancellationException)
                    functionHelper.plugin.logger.log(Level.SEVERE, "TimeLimitedTask for plugin ${functionHelper.plugin.name} generated an exception", it)
            }

            // convert to elapsed time here
            startTimeOrElapsedTime = System.currentTimeMillis() - startTimeOrElapsedTime
            onCompleted?.let { it(1.0, elapsedTime) }
        }
    }

    override fun onProgressUpdate(minDelay: Int, minInterval: Int, asCompletionListener: Boolean, block: WorkerUpdateLister): Worker {
        onProgressUpdate?.let { throw IllegalStateException() }
        onProgressUpdate = block
        progressUpdateInterval = minInterval
        lastUpdateTime = System.currentTimeMillis() + minDelay - minInterval
        if (asCompletionListener) onCompleted(block)
        return this
    }

    override fun onCompleted(block: WorkerUpdateLister): Worker {
        onCompleted?.let { throw IllegalStateException() }
        onCompleted = block
        return this
    }

    override suspend fun markSuspensionPoint() {
        if (System.currentTimeMillis() >= nextSuspensionTime)
            suspendCoroutineUninterceptedOrReturn { cont: Continuation<Unit> ->
                continuation = cont
                COROUTINE_SUSPENDED
            }
    }

    override fun setProgress(progress: Double) {
        this.progress = progress
        val onProgressUpdate = onProgressUpdate ?: return
        val time = System.currentTimeMillis()
        if (time > lastUpdateTime + progressUpdateInterval) {
            onProgressUpdate(progress, elapsedTime)
            lastUpdateTime = time
        }
    }

    override fun resume(worktime: Long): Boolean {
        nextSuspensionTime = currentTimeMillis() + worktime

        continuation?.let {
            continuation = null
            it.resume(Unit)
            return continuation == null
        }

        job?.let {
            nextSuspensionTime = 0L
            throw IllegalStateException()
        }

        try {
            val job = functionHelper.launchLazilyOnMainThread { task() }
            initJob(job = job)
            job.start()
        } catch (t: Throwable) {
            // do nothing: handled by job.invokeOnCompletion()
        }

        return continuation == null
    }

}

/*
/**
 * While the implementation of [kotlin.coroutines.experimental.intrinsics.intercepted] is intrinsic, it should look something like this
 * We don't care for intercepting the coroutine as we want it to resume immediately when we call resume().
 * Thus, above, we use an unintercepted suspension. It's not necessary as the dispatcher (or interceptor) also calls it synchronously, but whatever.
 */
private fun <T> Continuation<T>.interceptedImpl(): Continuation<T> {
    return context[ContinuationInterceptor]?.interceptContinuation(this) ?: this
}
 */

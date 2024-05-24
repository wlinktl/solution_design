import java.util.concurrent.*;

public class Orchestrator {

    private final ConcurrentHashMap<String, String> serviceStatus = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<Runnable> taskQueue = new PriorityBlockingQueue<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final CountDownLatch latch = new CountDownLatch(3); // example count

    public void startOrchestration() {
        // Example tasks
        Runnable task1 = () -> {
            try {
                // Simulate task
                Thread.sleep(1000);
                serviceStatus.put("Service1", "Completed");
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Runnable task2 = () -> {
            try {
                // Simulate task
                Thread.sleep(1500);
                serviceStatus.put("Service2", "Completed");
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Runnable task3 = () -> {
            try {
                // Simulate task
                Thread.sleep(500);
                serviceStatus.put("Service3", "Completed");
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        taskQueue.add(task1);
        taskQueue.add(task2);
        taskQueue.add(task3);

        while (!taskQueue.isEmpty()) {
            executorService.execute(taskQueue.poll());
        }

        try {
            latch.await();
            System.out.println("All services have completed their tasks.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executorService.shutdown();
    }

    public static void main(String[] args) {
        Orchestrator orchestrator = new Orchestrator();
        orchestrator.startOrchestration();
    }
}

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PodCPUUsage {

    public static void main(String[] args) {
        // Initialize Kubernetes client
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            
            // Get all pods across all namespaces
            PodList podList = client.pods().inAnyNamespace().list();
            
            // Get current metrics (CPU usage) for pods
            PodMetricsList podMetricsList = client.top().pods().metrics().inAnyNamespace().list();

            // Map to hold current CPU usage of pods
            Map<String, String> podCpuUsageMap = new HashMap<>();
            for (PodMetrics podMetrics : podMetricsList.getItems()) {
                String podName = podMetrics.getMetadata().getName();
                String namespace = podMetrics.getMetadata().getNamespace();
                // Sum CPU usage for all containers within the pod
                String totalCpuUsage = podMetrics.getContainers().stream()
                    .map(container -> container.getUsage().get("cpu"))
                    .map(Quantity::getAmount)
                    .reduce("0", (a, b) -> String.valueOf(Double.parseDouble(a) + Double.parseDouble(b)));
                podCpuUsageMap.put(namespace + "/" + podName, totalCpuUsage);
            }

            // Iterate through all pods and print requests, limits, and current CPU usage
            for (Pod pod : podList.getItems()) {
                String podName = pod.getMetadata().getName();
                String namespace = pod.getMetadata().getNamespace();

                System.out.println("Pod: " + podName + " (Namespace: " + namespace + ")");
                
                pod.getSpec().getContainers().forEach(container -> {
                    // Get resource requests and limits
                    Map<String, Quantity> requests = container.getResources().getRequests();
                    Map<String, Quantity> limits = container.getResources().getLimits();

                    // Parse requests and limits
                    String cpuRequest = requests != null && requests.containsKey("cpu") ? requests.get("cpu").getAmount() : "None";
                    String cpuLimit = limits != null && limits.containsKey("cpu") ? limits.get("cpu").getAmount() : "None";
                    
                    // Fetch current CPU usage from metrics map
                    String currentCpuUsage = podCpuUsageMap.getOrDefault(namespace + "/" + podName, "N/A");

                    // Display resource information
                    JSONObject podInfo = new JSONObject();
                    podInfo.put("containerName", container.getName());
                    podInfo.put("cpuRequest", cpuRequest);
                    podInfo.put("cpuLimit", cpuLimit);
                    podInfo.put("currentCpuUsage", currentCpuUsage);
                    
                    System.out.println(podInfo.toString(2)); // Pretty-print JSON
                });

                System.out.println("-----------------------------");
            }
        }
    }
}
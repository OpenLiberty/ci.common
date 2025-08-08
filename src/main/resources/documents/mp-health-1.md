:summary: MicroProfile Health API dependency configuration for Maven project to use health check features
To use MicroProfile Health API in your Maven project, add the following dependency to your `pom.xml` file:

```xml
<dependency>
    <groupId>org.eclipse.microprofile.health</groupId>
    <artifactId>microprofile-health-api</artifactId>
    <version>4.0</version> <!-- match the Microprofile version you're targeting -->
    <scope>provided</scope>
</dependency>
```

:summary: MicroProfile Health feature in the Liberty server.xml configuration file
If you are deploying your application to Liberty, enable the MicroProfile Health feature in your Liberty `server.xml` configuration file:
```xml
<featureManager>
    <feature>mpHealth-4.0</feature> <!-- match the Microprofile version you're targeting -->
</featureManager>
```
This setting allows Liberty to detect and register your health check classes and expose the `/health` endpoints.

:summary: MicroProfile liveness health check to check the memory usage less than 90%
You can implement a custom liveness health check by creating a Java class that uses the MicroProfile Health API to monitor memory usage. The following example checks if memory usage is below 90%:

```java
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import jakarta.enterprise.context.ApplicationScoped;

@Liveness
@ApplicationScoped
public class LivenessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        long memUsed = memBean.getHeapMemoryUsage().getUsed();
        long memMax = memBean.getHeapMemoryUsage().getMax();

        return HealthCheckResponse.named("Liveness Check")
                                  .status(memUsed < memMax * 0.9)
                                  .build();
    }

}
```
This health check returns "UP" if memory usage is below 90%, and "DOWN" otherwise.

:summary: MicroProfile liveness health endpoint to view the liveness health report
After you implement and deploy your liveness health check, you can verify its operation by accessing the liveness endpoint at `http://<host>:<port>/health/live`.
The endpoint will report "UP" if the memory usage is below the threshold you set in your health check class.

:summary: MicroProfile readiness health check to check the required host and port connection 
You can implement a custom readiness health check by creating a Java class that uses the MicroProfile Health API to monitor the connection to the required host and port. The following example checks if the required host and port connection is live:
```java
import java.net.Socket;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;

@Readiness
@ApplicationScoped
public class ReadinessCheck implements HealthCheck {

    private static String REQURIED_HOST = "localhost";
    private static int REQURIED_PORT = 9080;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder responseBuilder =
            HealthCheckResponse.named("Readiness Check");
        try {
            Socket socket = new Socket(REQURIED_HOST, REQURIED_PORT);
            socket.close();
            responseBuilder.up();
        } catch (Exception e) {
            responseBuilder.down();
        }
        return responseBuilder.build();
    }

}
```
This health check returns "UP" if the connection is successfully connected, and "DOWN" otherwise.

:summary: MicroProfile readiness health endpoint to view the readiness health report
After you implement and deploy your readiness health check, you can verify its operation by accessing the readiness endpoint at `http://<host>:<port>/health/ready`.

The endpoint will report "UP" if the connection is successfully connected, and "DOWN" otherwise.

:summary: MicroProfile startup health check to check the CPU usage less than 95%
You can implement a custom startup health check by creating a Java class that uses the MicroProfile Health API to monitor CPU usage. The following example checks if CPU usage is below 95%:
```java
import java.lang.management.ManagementFactory;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Startup;

import com.sun.management.OperatingSystemMXBean;

import jakarta.enterprise.context.ApplicationScoped;

@Startup
@ApplicationScoped
public class StartupCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        OperatingSystemMXBean bean = (com.sun.management.OperatingSystemMXBean)
        ManagementFactory.getOperatingSystemMXBean();
        double cpuUsed = bean.getCpuLoad();
        return HealthCheckResponse.named("Startup Check")
                                  .status(cpuUsed < 0.95).build();
    }
}
```
This health check returns "UP" if CPU usage is below 95%, and "DOWN" otherwise.

:summary: MicroProfile startup health endpoint to view the startup health report
After you implement and deploy your startup health check, you can verify its operation by accessing the startup endpoint at `http://<host>:<port>/health/started`.
The endpoint will report "UP" if the CPU usage is below the threshold you set in your health check class.


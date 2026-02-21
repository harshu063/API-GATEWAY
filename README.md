# E-Commerce API Gateway
A robust and scalable API Gateway serving as the central entry point for the e-commerce microservices architecture. This gateway handles routing, load balancing, authentication, and provides a unified interface for client applications to interact with various backend services.
## Table of Contents
- [Linked Microservices](#linked-microservices)
- [Features of this API Gateway](#features-of-this-api-gateway)
- [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Configuration](#configuration)
    - [How to Run the Application](#how-to-run-the-application)

- [Author](#author)

## Linked Microservices
This API Gateway acts as a single point of entry and routes requests to the following microservices:
- **User Service** - Handles user authentication, registration, and profile management
- **Product Service** - Manages product catalog, inventory, and product information
- **Order Service** - Processes orders, order tracking, and order history
- **Payment Service** - Manages payment processing and transaction handling
- **Notification Service** - Sends email and SMS notifications to users

## Features of this API Gateway
### Core Capabilities
- **🔀 Dynamic Routing** - Intelligent request routing to appropriate microservices based on URL patterns
- **⚖️ Load Balancing** - Distributes incoming requests across multiple service instances
- **🔒 Centralized Authentication** - Single authentication layer for all microservices
- **🛡️ Security** - Built-in security features including CORS configuration and request validation
- **📊 Request/Response Logging** - Comprehensive logging for monitoring and debugging via Prometheus
- **📈 Monitoring & Health Checks** - Real-time health monitoring of all connected services

## Getting Started
Follow these instructions to get the API Gateway up and running on your local machine for development and testing purposes.
### Prerequisites
Before you begin, ensure you have the following installed:
- **Java Development Kit (JDK)** - Version 21 or higher``` bash
  java -version
- **Maven** - Version 3.6 or higher
- Eureka Server -Running on Port 8761
- Microservices - At least one microservice should be running for the gateway to route requests
- Redis- for caching
``` bash
  mvn -version

Maven - Version 3.6 or higher``` bash
  mvn -version
```

Git - For cloning the repository``` bash
git --version
```

Service Discovery Server - Ensure your service registry (e.g., Eureka, Consul) is running
Backend Microservices - At least one microservice should be running for the gateway to route requests
Configuration
Clone the repository``` bash
   git clone https://github.com/yourusername/Api_Gateway.git
   cd Api_Gateway
```

Configure application properties
Navigate to src/main/resources/application.properties and update the following configurations:``` properties
# Server Configuration
server.port=8080
spring.application.name=api-gateway

# Service Registry Configuration
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/

# Microservice Routes
# Add your microservice URLs and routing configurations

# Security Configuration
# Configure JWT secret, token expiration, etc.

# Logging Configuration
logging.level.root=INFO
logging.level.org.springframework.cloud.gateway=DEBUG
```

Environment Variables (Optional)
You can also use environment variables for sensitive configuration:``` bash
   export JWT_SECRET=your-secret-key
   export EUREKA_SERVER_URL=http://localhost:8761/eureka/
```

How to Run the Application
Using Maven
Build the project``` bash
mvn clean install
```

Run the application``` bash
   mvn spring-boot:run
```

Using Maven Wrapper
On Windows``` bash
mvnw.cmd spring-boot:run
```

On Linux/Mac``` bash
   ./mvnw spring-boot:run
```

Using JAR file
Build the JAR``` bash
mvn clean package
```

Run the JAR``` bash
   java -jar target/Api_Gateway-0.0.1-SNAPSHOT.jar
```

Verify the Application
Once the application starts successfully, you should see:```
Started ApiGatewayApplication in X.XXX seconds
```

Access the gateway at: http://localhost:8080
Check health status: http://localhost:8080/actuator/health
 
Author
Harsh Gehlot

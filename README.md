# 🚀 Kubernetes Auto-Remediation & Cost Optimization Platform

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-1.30+-326CE5.svg)](https://kubernetes.io/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A production-grade Kubernetes monitoring system with **autonomous issue remediation** and **real-time cost analysis**. Built to demonstrate DevOps best practices, full-stack development, and FinOps principles.

---

## 📋 Table of Contents

- [Problem Statement](#-problem-statement)
- [Key Features](#-key-features)
- [Technology Stack](#-technology-stack)
- [Prerequisites](#-prerequisites)
- [Quick Start](#-quick-start)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Usage](#-usage)
- [API Documentation](#-api-documentation)
- [Deployment](#-deployment)
- [Architecture](#-architecture)
- [Monitoring & Observability](#-monitoring--observability)
- [Contributing](#-contributing)
- [Troubleshooting](#-troubleshooting)
- [License](#-license)

---

## 🎯 Problem Statement

### The Challenge

Modern Kubernetes environments face critical operational challenges:

- ❌ **Pods crash** and enter `CrashLoopBackOff`, requiring manual intervention
- ⏱️ **DevOps teams spend hours** debugging and restarting failed pods
- 💸 **Resource waste** goes unnoticed, leading to inflated cloud bills
- 🔍 **No centralized visibility** across namespaces and resources

### The Solution

A unified intelligent platform that:

- ✅ **Monitors** your entire Kubernetes cluster in real-time
- 🔍 **Detects** common issues like `CrashLoopBackOff`, `OOMKilled`, high restart counts
- 🤖 **Remediates** problems automatically with intelligent backoff logic
- 💰 **Optimizes costs** by identifying over-provisioned resources
- 📢 **Alerts teams** through activity feeds and comprehensive audit logs

---

## ✨ Key Features

### 🤖 Autonomous Auto-Remediation Engine

- **Smart Detection**: Identifies `CrashLoopBackOff`, high restarts, failed pods, pending issues
- **Automated Actions**: Deletes and recreates problematic pods automatically
- **Backoff Logic**: Prevents infinite loops with configurable max restart attempts
- **Policy-Driven**: Fully configurable remediation rules via UI or API
- **Audit Trail**: Every action logged to Redis with timestamps and metadata

### 💰 Cost Monitoring & Optimization (FinOps)

- **Real-Time Cost Analysis**: Calculate cluster costs by namespace, deployment, and pod
- **Resource Efficiency Score**: Identify over/under-provisioned workloads
- **Waste Detection**: Highlight idle pods and suggest optimizations
- **Cost Trends**: Historical data with charts showing spend over time
- **Savings Recommendations**: AI-driven suggestions to reduce costs by 30-50%

### 📊 Comprehensive Monitoring Dashboard

- **Cluster-Wide Visibility**: All pods, deployments, services, ingresses
- **Live Metrics**: Real-time CPU/Memory usage via Kubernetes Metrics Server
- **Namespace Filtering**: Drill down into specific namespaces
- **Pod Logs**: View container logs directly from the UI
- **Activity Feed**: Live updates showing remediation actions as they happen

### ⚡ Production-Ready Architecture

- **Caching Layer**: Redis for performance (sub-100ms response times)
- **Observability**: OpenTelemetry distributed tracing
- **Auto-Scaling**: HPA for horizontal scaling, VPA-ready
- **Health Checks**: Liveness/readiness probes on all components
- **RESTful API**: Clean, documented endpoints

---

## 🛠️ Technology Stack

### Backend
- **Java 17** - Modern LTS Java version
- **Spring Boot 3.5.0** - Application framework
- **Spring Web** - REST API development
- **Spring Actuator** - Health checks and metrics

### Kubernetes Integration
- **Kubernetes Java Client 18.0.0** - Official Kubernetes API client
- **Kubernetes Metrics Server** - Real-time resource metrics

### Storage & Caching
- **Redis** - High-performance caching and session storage

### Observability
- **OpenTelemetry** - Distributed tracing
- **Prometheus** - Metrics collection
- **Grafana** - Visualization and dashboards

### DevOps & CI/CD
- **Docker** - Containerization
- **GitLab CI/CD** - Continuous integration and deployment
- **Kustomize** - Kubernetes configuration management
- **Minikube** - Local development
- **K3s** - Production Kubernetes
- **GKE** - Google Kubernetes Engine support

---

## 📦 Prerequisites

Before you begin, ensure you have the following installed:

- **Java 17+** ([Download](https://adoptium.net/))
- **Maven 3.8+** ([Download](https://maven.apache.org/download.cgi))
- **Docker** ([Download](https://www.docker.com/get-started))
- **kubectl** ([Install](https://kubernetes.io/docs/tasks/tools/))
- **Access to a Kubernetes cluster** (Minikube, K3s, GKE, or any K8s cluster)
- **Redis** (Optional: for caching, can run in Docker)

### Kubernetes Cluster Options

Choose one of the following:

1. **Minikube** (Local development)
   ```bash
   minikube start --cpus=4 --memory=8192
   ```

2. **K3s** (Lightweight production)
   ```bash
   curl -sfL https://get.k3s.io | sh -
   ```

3. **GKE** (Google Cloud)
   ```bash
   gcloud container clusters create my-cluster --num-nodes=3
   ```

---

## 🚀 Quick Start

Get up and running in 5 minutes:

### 1. Clone the Repository
```bash
git clone https://github.com/hatem-nefzi/monitoringApplication.git
cd monitoringApplication
```

### 2. Build the Application
```bash
./mvnw clean package
```

### 3. Run Locally
```bash
java -jar target/monitoring-app-0.0.1-SNAPSHOT.jar
```

### 4. Access the Application
```
http://localhost:9090
```

### 5. Test the API
```bash
# Health check
curl http://localhost:9090/actuator/health

# Get all pods
curl http://localhost:9090/api/kubernetes/pods

# Get pods in a specific namespace
curl http://localhost:9090/api/kubernetes/pods/default
```

---

## 📥 Installation

### Local Development Setup

#### 1. Configure Kubernetes Access

Ensure your `kubeconfig` is properly configured:
```bash
kubectl config view
kubectl get nodes
```

#### 2. Start Redis (Optional)

Using Docker:
```bash
docker run -d --name redis -p 6379:6379 redis:latest
```

#### 3. Configure Application

Create `application-local.properties`:
```properties
# Server Configuration
server.port=9090

# Kubernetes Configuration
kubernetes.config.location=${HOME}/.kube/config

# Redis Configuration (if using)
spring.redis.host=localhost
spring.redis.port=6379

# Actuator Endpoints
management.endpoints.web.exposure.include=health,info,metrics,prometheus
```

#### 4. Build and Run

```bash
# Build
./mvnw clean package

# Run with local profile
java -jar target/monitoring-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

---

## ⚙️ Configuration

### Application Properties

Key configuration options in `application.properties`:

```properties
# Application
spring.application.name=monitoring-backend
server.port=9090

# Kubernetes
kubernetes.namespace=default

# Auto-Remediation
remediation.max-retries=3
remediation.backoff.initial=30000
remediation.backoff.multiplier=2
remediation.backoff.max=300000

# Cost Optimization
cost.cpu-price=0.0416
cost.memory-price=0.0055

# Redis Caching
spring.cache.type=redis
spring.redis.host=redis-service
spring.redis.port=6379

# Observability
management.tracing.sampling.probability=1.0
management.metrics.export.prometheus.enabled=true
```

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `KUBERNETES_CONFIG_LOCATION` | Path to kubeconfig file | `~/.kube/config` | No |
| `SERVER_PORT` | Application port | `9090` | No |
| `REDIS_HOST` | Redis host | `localhost` | No |
| `REDIS_PORT` | Redis port | `6379` | No |
| `REMEDIATION_MAX_RETRIES` | Max auto-remediation attempts | `3` | No |

---

## 💻 Usage

### Web Dashboard

Access the monitoring dashboard at `http://localhost:9090` to:
- View all cluster resources
- Monitor pod health and metrics
- Review auto-remediation actions
- Analyze cost optimization opportunities

### REST API Endpoints

#### Kubernetes Resources

```bash
# List all pods
GET /api/kubernetes/pods

# Get pods in namespace
GET /api/kubernetes/pods/{namespace}

# List deployments
GET /api/kubernetes/deployments

# List services
GET /api/kubernetes/services
```

#### Auto-Remediation

```bash
# Get remediation history
GET /api/remediation/actions

# Get remediation actions for a pod
GET /api/remediation/actions/{podName}

# Trigger manual remediation
POST /api/remediation/trigger
Content-Type: application/json
{
  "podName": "my-pod",
  "namespace": "default",
  "action": "restart"
}
```

#### Cost Analysis

```bash
# Get cluster cost summary
GET /api/cost/summary

# Get namespace costs
GET /api/cost/namespace/{namespace}

# Get cost optimization recommendations
GET /api/cost/recommendations
```

---

## 📚 API Documentation

### Swagger/OpenAPI

Once the application is running, access interactive API documentation:

```
http://localhost:9090/swagger-ui.html
```

### Key API Examples

#### Monitor Pod Health

```bash
curl -X GET http://localhost:9090/api/kubernetes/pods/default \
  -H "Accept: application/json"
```

Response:
```json
{
  "pods": [
    {
      "name": "nginx-deployment-7d64fc9b4-abc123",
      "namespace": "default",
      "status": "Running",
      "restartCount": 0,
      "cpuUsage": "10m",
      "memoryUsage": "64Mi"
    }
  ]
}
```

---

## 🚢 Deployment

### Docker Deployment

#### 1. Build Docker Image

```bash
docker build -t monitoring-backend:latest .
```

#### 2. Run Container

```bash
docker run -d \
  --name monitoring-backend \
  -p 9090:9090 \
  -v ~/.kube/config:/app/.kube/config \
  -e KUBERNETES_CONFIG_LOCATION=/app/.kube/config \
  monitoring-backend:latest
```

### Kubernetes Deployment

#### 1. Apply Base Configuration

```bash
kubectl apply -k k8s/base/
```

#### 2. Deploy to Staging (Minikube)

```bash
kubectl apply -k k8s/overlays/staging/
```

#### 3. Deploy to Production (K3s)

```bash
kubectl apply -k k8s/overlays/production/
```

#### 4. Verify Deployment

```bash
kubectl get pods -n monitoring
kubectl logs -f deployment/monitoring-backend -n monitoring
```

### GitLab CI/CD Pipeline

The project includes a complete CI/CD pipeline:

**Stages:**
1. **Build** - Maven compile and package
2. **Test** - Unit and integration tests
3. **Security** - OWASP dependency check, Trivy scanning
4. **Quality** - SonarQube analysis
5. **Docker** - Build and push container image
6. **Deploy Staging** - Deploy to Minikube
7. **Smoke Tests** - Automated testing
8. **Deploy Production** - Deploy to K3s
9. **Monitoring** - Verify Prometheus targets

**Trigger:** Push to `experiment` branch or merge to `main`

---

## 🏗️ Architecture

### High-Level Overview

```
┌─────────────────────────────────────────────────────────┐
│                   Kubernetes Cluster                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│  │   Pod    │  │   Pod    │  │   Pod    │             │
│  └──────────┘  └──────────┘  └──────────┘             │
└─────────────────────────────────────────────────────────┘
              │                    ▲
              │ Watches & Metrics  │ Auto-Remediation
              ▼                    │
┌──────────────────────────────────────────────────────────┐
│           Monitoring Backend (Spring Boot)               │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  K8s Client │  │  Remediation │  │ Cost Analysis │  │
│  │   Service   │  │    Engine    │  │    Service    │  │
│  └─────────────┘  └──────────────┘  └───────────────┘  │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │              REST API Controller                  │  │
│  └──────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
              │
              ▼
      ┌─────────────┐     ┌────────────────┐
      │    Redis    │     │  Prometheus/   │
      │   Cache     │     │    Grafana     │
      └─────────────┘     └────────────────┘
```

### Component Architecture

- **KubernetesService**: Interfaces with K8s API for resource management
- **RemediationEngine**: Detects and remediates pod issues automatically
- **CostAnalysisService**: Calculates and optimizes resource costs
- **RestControllers**: Expose RESTful APIs for external access
- **Redis**: Caches frequently accessed data and stores audit logs

---

## 📊 Monitoring & Observability

### Prometheus Metrics

The application exposes metrics at:
```
http://localhost:9090/actuator/prometheus
```

Key metrics:
- `kubernetes_pods_total` - Total number of pods
- `kubernetes_pods_running` - Running pods count
- `kubernetes_pods_failed` - Failed pods count
- `remediation_actions_total` - Total remediation actions
- `remediation_success_rate` - Success rate percentage
- `cost_total_cluster` - Total cluster cost

### Grafana Dashboards

Import pre-built dashboards from `k8s/monitoring/grafana-dashboard.json`

### OpenTelemetry Tracing

Distributed tracing is enabled by default. View traces in your observability backend (Jaeger, Zipkin, etc.)

---

## 🤝 Contributing

We welcome contributions! Please follow these steps:

### 1. Fork the Repository

```bash
git clone https://github.com/hatem-nefzi/monitoringApplication.git
cd monitoringApplication
git checkout -b feature/your-feature-name
```

### 2. Make Your Changes

- Follow existing code style and conventions
- Add tests for new features
- Update documentation as needed

### 3. Test Your Changes

```bash
# Run tests
./mvnw test

# Run integration tests
./mvnw verify

# Build the project
./mvnw clean package
```

### 4. Submit a Pull Request

- Provide a clear description of changes
- Reference any related issues
- Ensure all tests pass

### Code Style Guidelines

- Use **Java 17** features where appropriate
- Follow **Spring Boot** best practices
- Write **clean, self-documenting code**
- Add **JavaDoc** for public APIs
- Keep methods **small and focused**

---

## 🐛 Troubleshooting

### Common Issues

#### 1. Connection to Kubernetes Cluster Failed

**Problem:** `Unable to connect to Kubernetes cluster`

**Solution:**
```bash
# Verify kubeconfig
kubectl config view
kubectl cluster-info

# Set correct kubeconfig path
export KUBERNETES_CONFIG_LOCATION=~/.kube/config
```

#### 2. Redis Connection Error

**Problem:** `Cannot connect to Redis server`

**Solution:**
```bash
# Check Redis is running
docker ps | grep redis

# Start Redis if not running
docker run -d --name redis -p 6379:6379 redis:latest
```

#### 3. Port Already in Use

**Problem:** `Port 9090 is already in use`

**Solution:**
```bash
# Use a different port
java -jar target/monitoring-app-0.0.1-SNAPSHOT.jar --server.port=8080

# Or kill the process using the port
lsof -ti:9090 | xargs kill -9
```

#### 4. Permission Denied in Kubernetes

**Problem:** `Forbidden: User cannot list pods`

**Solution:**
```bash
# Create proper RBAC permissions
kubectl apply -f rbac-permissions.yaml
```

### Debug Mode

Enable debug logging:
```properties
logging.level.com.example.demo=DEBUG
logging.level.io.kubernetes=DEBUG
```

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

## 👏 Acknowledgments

- **Kubernetes Java Client** - Official Kubernetes API library
- **Spring Boot Team** - Excellent framework and documentation
- **OpenTelemetry** - Observability standards
- **Redis Labs** - High-performance caching
- **The DevOps Community** - Continuous inspiration and support

---

## 📞 Support

For questions, issues, or feature requests:

- 🐛 **Report Issues**: [GitHub Issues](https://github.com/hatem-nefzi/monitoringApplication/issues)
- 💬 **Discussions**: [GitHub Discussions](https://github.com/hatem-nefzi/monitoringApplication/discussions)
- 📧 **Email**: [Contact the team](mailto:hatem.nefzi@example.com)

---

<div align="center">

**⭐ Star this repository if you find it useful!**

Made with ❤️ by [Hatem Nefzi](https://github.com/hatem-nefzi)

</div>

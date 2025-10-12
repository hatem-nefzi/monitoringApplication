🚀 Kubernetes Auto-Remediation & Cost Optimization Platform
A production-grade Kubernetes monitoring system with autonomous issue remediation and real-time cost analysis. Built to demonstrate DevOps best practices, full-stack development, and FinOps principles.

🎯 Problem Statement
The Challenge:

Kubernetes pods crash and enter CrashLoopBackOff, requiring manual intervention
DevOps teams spend hours debugging and restarting failed pods
Resource waste goes unnoticed, leading to inflated cloud bills
No centralized visibility across namespaces and resources

The Solution:
A unified platform that:

Monitors your entire Kubernetes cluster in real-time
Detects common issues like CrashLoopBackOff, OOMKilled, high restart counts
Remediates problems automatically with intelligent backoff logic
Optimizes costs by identifying over-provisioned resources
Alerts teams through activity feeds and audit logs


✨ Key Features
🤖 Autonomous Auto-Remediation Engine

Smart Detection: Identifies CrashLoopBackOff, high restarts, failed pods, pending issues
Automated Actions: Deletes and recreates problematic pods automatically
Backoff Logic: Prevents infinite loops with configurable max restart attempts
Policy-Driven: Fully configurable remediation rules via UI or API
Audit Trail: Every action logged to Redis with timestamps and metadata

💰 Cost Monitoring & Optimization (FinOps)

Real-Time Cost Analysis: Calculate cluster costs by namespace, deployment, and pod
Resource Efficiency Score: Identify over/under-provisioned workloads
Waste Detection: Highlight idle pods and suggest optimizations
Cost Trends: Historical data with charts showing spend over time
Savings Recommendations: AI-driven suggestions to reduce costs by 30-50%

📊 Comprehensive Monitoring Dashboard

Cluster-Wide Visibility: All pods, deployments, services, ingresses
Live Metrics: Real-time CPU/Memory usage via Kubernetes Metrics Server
Namespace Filtering: Drill down into specific namespaces
Pod Logs: View container logs directly from the UI
Activity Feed: Live updates showing remediation actions as they happen

⚡ Production-Ready Architecture

Caching Layer: Redis for performance (sub-100ms response times)
Observability: OpenTelemetry distributed tracing
Auto-Scaling: HPA for horizontal scaling, VPA-ready
Health Checks: Liveness/readiness probes on all components
RESTful API: Clean, documented endpoints

🏗️ Architecture
High-Level Overview

┌─────────────────────────────────────────────────────────────┐
│                    Angular 18 Frontend                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Monitoring   │  │ Remediation  │  │ Cost         │      │
│  │ Dashboard    │  │ Dashboard    │  │ Dashboard    │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└────────────────────────────┬────────────────────────────────┘
                             │ REST API (HTTP)
                             ▼
┌─────────────────────────────────────────────────────────────┐
│               Spring Boot 3.x Backend (Java 17)              │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │          RemediationService (@Scheduled)            │    │
│  │  • Scans cluster every 30s                          │    │
│  │  • Detects issues (CrashLoopBackOff, etc)          │    │
│  │  • Takes corrective actions                         │    │
│  │  • Logs all actions to Redis                        │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │             CostAnalysisService                      │    │
│  │  • Calculates resource costs                        │    │
│  │  • Tracks usage trends                              │    │
│  │  • Generates optimization suggestions               │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │            KubernetesService (K8s Client)           │    │
│  │  • Connects to K8s API                              │    │
│  │  • Manages pods, deployments, resources             │    │
│  └─────────────────────────────────────────────────────┘    │
└────────────────────────┬────────────────────────────────────┘
                         │
         ┌───────────────┴────────────────┐
         ▼                                 ▼
┌──────────────────┐            ┌──────────────────┐
│   Kubernetes     │            │      Redis       │
│    Cluster       │            │   (Caching)      │
│  • Pods          │            │  • Actions       │
│  • Deployments   │            │  • Metrics       │
│  • Services      │            │  • Cost Data     │
│  • Metrics API   │            └──────────────────┘
└──────────────────┘

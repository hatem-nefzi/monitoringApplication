
---

# `docs/variables-reference.md`

```markdown
# Variables Reference

| Variable | Default | Scope | Utilisé par | Secret | Description |
|---|---|---:|---|:---:|---|
| `APP_NAME` | `app` | Stack | docker, k8s apply |  | Nom logique de l’application (sert d’alias d’image et de label `app=`). |
| `APP_PORT` | `8080` | Stack | runtime (svc) |  | Port interne de l’app (Service/Ingress). |
| `APP_SERVICE_NAME` | `${APP_NAME}-service` | Stack | smoke |  | Nom du Service exposé pour les tests. |
| `SONAR_PROJECT_KEY` | `$CI_PROJECT_PATH_SLUG` | Stack | sonarqube |  | Identifiant projet SonarQube. |
| `SONAR_PROJECT_NAME` | `$CI_PROJECT_NAME` | Stack | sonarqube |  | Nom projet SonarQube. |
| `SONAR_HOST_URL` | *(none)* | Env | sonarqube |  | URL du serveur SonarQube. |
| `SONAR_TOKEN` | *(none)* | Secret | sonarqube | ✅ | Token SonarQube. |
| `MAVEN_OPTS` | `-Dmaven.repo.local=.m2/repository` | Global | build/test |  | Cache Maven local. |
| `GIT_DEPTH` | `50` | Global | checkout |  | Historique Git étendu pour Sonar. |
| `REGISTRY_HOST` | `$CI_REGISTRY` | Global | docker/k8s |  | Hôte du registry (par défaut GitLab). |
| `REGISTRY_IMAGE` | `$CI_REGISTRY_IMAGE` | Global | docker |  | Référence d’image (namespace/projet). |
| `COMMIT_TAG` | `$CI_COMMIT_SHORT_SHA` | Computed | docker |  | Tag versionné traçable. |
| `IMAGE_VERSIONED` | `$REGISTRY_IMAGE:${COMMIT_TAG}` | Computed | docker/k8s |  | Image versionnée. |
| `IMAGE_LATEST` | `$REGISTRY_IMAGE:latest` | Computed | docker |  | Tag latest. |
| `CI_REGISTRY_USER` | auto | Secret | docker/k8s | ✅ | Utilisateur registry GitLab. |
| `CI_REGISTRY_PASSWORD` | auto | Secret | docker/k8s | ✅ | Mot de passe registry GitLab. |
| `NVD_API_KEY` | *(none)* | Secret | OWASP DC | ✅ | Clé NVD (vulnérabilités). |
| `FAIL_ON_CVSS` | `9` | Security | OWASP DC |  | Seuil d’échec. |
| `ENVIRONMENT_STAGING` | `staging` | Env | deploy:staging |  | Namespace Minikube. |
| `MINIKUBE_IP` | `192.168.49.2` | Env | kubeconfig |  | IP du cluster Minikube. |
| `MINIKUBE_PORT` | `8443` | Env | kubeconfig |  | Port API server Minikube. |
| `MINIKUBE_TOKEN` | *(none)* | Secret | kubeconfig | ✅ | Token SA pour CI. |
| `ENVIRONMENT_PRODUCTION` | `production` | Env | deploy:production |  | Namespace K3s. |
| `K3S_SERVER` | *(none)* | Secret | kubeconfig | ✅ | URL API K3s. |
| `K3S_CA_CERT` | *(none)* | Secret | kubeconfig | ✅ | CA cert (base64). |
| `K3S_CLIENT_CERT` | *(none)* | Secret | kubeconfig | ✅ | Client cert (base64). |
| `K3S_CLIENT_KEY` | *(none)* | Secret | kubeconfig | ✅ | Client key (base64). |
| `GKE_NAMESPACE` | `$APP_NAME` | Env | deploy:gke |  | Namespace GKE cible. |
| `GCP_SERVICE_ACCOUNT_KEY` | *(none)* | Secret | deploy:gke | ✅ | Clé SA GCP (base64). |
| `GCP_PROJECT_ID` | *(none)* | Env | deploy:gke |  | Projet GCP. |
| `GKE_CLUSTER_NAME` | *(none)* | Env | deploy:gke |  | Nom du cluster GKE. |
| `GKE_ZONE` | *(none)* | Env | deploy:gke |  | Zone GKE (ex: `europe-west1-b`). |
| `PROMETHEUS_STAGING_URL` | `http://$MINIKUBE_IP:30090` | Env | monitoring |  | URL Prometheus staging. |
| `GRAFANA_STAGING_URL` | `http://$MINIKUBE_IP:30080` | Env | ops |  | URL Grafana staging. |
| `PROMETHEUS_PRODUCTION_URL` | `http://prometheus.production.local` | Env | monitoring |  | URL Prometheus prod. |
| `GRAFANA_PRODUCTION_URL` | `http://grafana.production.local` | Env | ops |  | URL Grafana prod. |
| `KUSTOMIZE_VERSION` | `v5.4.3` | Core | k8s |  | Version kustomize installée dans les jobs. |
| `APP_HEALTH_PATH` | `/actuator/health` | Stack | smoke |  | Endpoint de santé. |
| `APP_SERVICE_PORT` | `80` | Stack | smoke |  | Port du Service exposé. |
| `LOCAL_TEST_PORT` | `9090` | Stack | smoke |  | Port local pour port-forward. |
| `SMOKE_ENDPOINTS` | `/actuator/health /api/kubernetes/pods` | Stack | smoke |  | Endpoints testés. |
| `PERF_REQUESTS` | `300` | Stack | smoke |  | Nb de requêtes perf. |
| `PERF_CONCURRENCY` | `5` | Stack | smoke |  | Concurrence perf. |

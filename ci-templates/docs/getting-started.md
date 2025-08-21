# 🚀 Guide de Démarrage - GitLab Templates Spring Boot

## 📋 Vue d'ensemble

Ce repository contient des templates GitLab CI/CD optimisés pour projets **Spring Boot**, basés sur l'architecture **Pipeline-as-a-Platform (PaaP)**. 

### 🎯 Principe : Pipeline-as-a-Platform

Notre approche transforme votre pipeline CI/CD en une **plateforme intelligente** qui :
- **Orchestre** les workflows au lieu d'exécuter directement
- **Délègue** chaque tâche à un conteneur spécialisé
- **Garantit** la reproductibilité avec "Build Once, Deploy Anywhere"
- **Industrialise** via des templates réutilisables

## 🏗️ Architecture des Templates

```
gitlab-templates/
├── templates/
│   ├── core/                    # Jobs techniques fondamentaux
│   ├── stack-technologique/spring-boot/            # Spécialisation Spring Boot
│   └── evenironmnets/              # Déploiements par environnement
├── shared/                     # Scripts et utilitaires
└── stack-technologique/spring-boot/examples/monitoring-backend               # mon projet actuel que j'ai deployé 
```

## 🚀 Démarrage Rapide

### 1. Projet Spring Boot Basique

```yaml
# .gitlab-ci.yml
include:
  - project: 'demo/ci-templates'
    ref: 'main'
    file: '/templates/stack-technologique/spring-boot/.gitlab-ci-spring-boot.yml'

# Variables spécifiques à votre projet
variables:
  APP_NAME: "mon-app"
  SONAR_PROJECT_KEY: "mon-app"
```

**C'est tout !** 🎉 Votre pipeline inclut automatiquement :
- ✅ Build Maven avec cache
- ✅ Tests unitaires
- ✅ Analyse SonarQube
- ✅ Scan de sécurité (OWASP + Trivy)
- ✅ Build Docker
- ✅ Déploiements multi-environnements
- ✅ Tests automatisés
- ✅ Monitoring

### 2. Projet avec Customisations

```yaml
# .gitlab-ci.yml
include:
  - project: 'devops/gitlab-templates'
    ref: 'main'
    file: '/templates/spring-boot/.gitlab-ci-spring-boot.yml'

variables:
  APP_NAME: "monitoring-backend"
  APP_PORT: "9090"
  FAIL_ON_CVSS: 7  # Sécurité plus stricte

# Override d'un job spécifique
build:
  extends: .maven_job
  script:
    - echo "🔨 Build custom avec profil Spring..."
    - mvn clean package -Pproduction -DskipTests
  rules:
    - if: $CI_COMMIT_BRANCH == "main"
```

## 🎯 Environnements Supportés

| Environnement | Plateforme | Usage | Template |
|---------------|------------|-------|----------|
| **DEV/Staging** | Minikube | Tests rapides | `.gitlab-ci-dev.yml` |
| **Pre-Production** | K3s | Validation complète | `.gitlab-ci-staging.yml` |
|  **Prod/Cloud** | GKE | Production Cloud | `.gitlab-ci-prod.yml` |

## 🔧 Configuration

### Variables Essentielles

```yaml
variables:
  # Application
  APP_NAME: "votre-app"
  APP_PORT: "8080"
  
  # Sécurité
  SONAR_PROJECT_KEY: "votre-app"
  FAIL_ON_CVSS: 9
  
  # Environnements
  STAGING_VM_IP: "192.168.100.155"
  PRODUCTION_VM_IP: "192.168.100.156"
  
  # GCP (si GKE)
  GCP_PROJECT_ID: "votre-projet"
  GKE_CLUSTER_NAME: "votre-cluster"
```

### Secrets GitLab Requis

```bash
# SonarQube
SONAR_HOST_URL=https://sonar.votre-domaine.com
SONAR_TOKEN=squ_xxxxxxxxxxxxx

# NVD API (pour OWASP)
NVD_API_KEY=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx

# Kubernetes
MINIKUBE_TOKEN=eyJhbGciOiJSUzI1Ni...
K3S_SERVER=https://192.168.100.156:6443
K3S_CA_CERT=LS0tLS1CRUdJTi...
K3S_CLIENT_CERT=LS0tLS1CRUdJTi...
K3S_CLIENT_KEY=LS0tLS1CRUdJTi...

# GCP (si GKE)
GCP_SERVICE_ACCOUNT_KEY=ewogICJ0eXBlIjogInNlcnZpY2U...

# Notifications (optionnel)
GITLAB_PERSONAL_TOKEN=glpat-xxxxxxxxxxxxxxxxxxxx
```

## 🔄 Flux de Déploiement

### Build Once, Deploy Anywhere

```mermaid
graph LR
    A[Code Push] --> B[Build Maven]
    B --> C[Build Docker]
    C --> D[Tag: SHA]
    D --> E[Deploy DEV]
    D --> F[Deploy STAGING]
    D --> G[Deploy PROD]
    D --> H[Deploy GKE]
```

**Principe clé** : Le même artefact Docker (tagué avec le SHA du commit) est déployé sur tous les environnements.

### Pipeline Complet (14 Stages)

```yaml
stages:
  - checkout        # 📥 Récupération code
  - build          # 🔨 Compilation Maven
  - test           # 🧪 Tests unitaires
  - security       # 🔒 OWASP Dependency Check
  - sonarqube      # 📊 Analyse qualité
  - docker         # 🐳 Build image Docker
  - docker_security # 🔍 Scan sécurité image
  - deploy-staging  # 🚀 Déploiement staging
  - staging-tests   # ✅ Tests staging
  - deploy-production # 🏭 Déploiement production
  - monitoring-verify # 📊 Vérification monitoring
  - deploy-gke      # ☁️ Déploiement cloud
```

## 🧪 Tests Automatisés

### Smoke Tests
```bash
# Tests rapides post-déploiement
/shared/scripts/smoke-tests.sh \
  -n staging \
  -e "/actuator/health /api/status"
```

### Tests Complets
- ✅ Health checks Spring Boot
- ✅ Tests de performance (Apache Bench)
- ✅ Validation API JSON
- ✅ Tests endpoints Kubernetes
- ✅ Vérification mémoire JVM

## 📊 Observabilité

### Métriques Automatiques
- **Prometheus** : Métriques application et infrastructure
- **Grafana** : Dashboards automatiques
- **ServiceMonitor** : Découverte automatique Prometheus

### Alerting
- Intégration avec AlertManager
- Notifications TPar Email
- Métriques pipeline dans GitLab

## 🔒 Sécurité Intégrée

### Shift-Left Security
- **OWASP Dependency Check** : Vulnérabilités dépendances
- **Trivy** : Scan images Docker et manifestes K8s
- **SonarQube** : Analyse statique code
- **Binary Authorization** : Vérification signatures (GKE)

### Policies as Code
- **OPA Gatekeeper** : Politiques Kubernetes
- **Falco** : Détection runtime (optionnel)

## 🚀 Exemples d'Usage

### Projet Minimal
```yaml
include:
  - project: 'devops/gitlab-templates'
    file: '/templates/spring-boot/.gitlab-ci-spring-boot.yml'

variables:
  APP_NAME: "simple-api"
```

### Microservice Complexe
```yaml
include:
  - project: 'devops/gitlab-templates'
    file: '/templates/spring-boot/.gitlab-ci-spring-boot.yml'

variables:
  APP_NAME: "user-service"
  APP_PORT: "8080"
  STAGING_REPLICAS: 2
  PRODUCTION_REPLICAS: 5

# Jobs additionnels
integration_tests:
  stage: staging-tests
  script:
    - mvn test -Pintegration
```

## 🔧 Scripts Utilitaires

### Déploiement Kustomize
```bash
/shared/scripts/kustomize-deploy.sh \
  -e production \
  -t abc123 \
  -o k8s/overlays/production \
  -n production
```

### Tests de Smoke
```bash
/shared/scripts/smoke-tests.sh \
  -n staging \
  -e "/health /metrics" \
  -t 300
```

## 📚 Ressources Additionnelles

- 📖 [Guide Pipeline Architecture](pipeline-architecture.md)
- 🔧 [Guide Templates](templates-guide.md)
- 🛠️ [Troubleshooting](troubleshooting.md)
- ✨ [Best Practices](best-practices.md)

## 🆘 Support

### Issues Communes

**Pipeline ne démarre pas**
```bash
# Vérifier les inclusions
include:
  - project: 'devops/gitlab-templates'
    ref: 'main'  # ← Vérifier la référence
    file: '/templates/spring-boot/.gitlab-ci-spring-boot.yml'
```

**Échec déploiement Kubernetes**
```bash
# Vérifier les secrets
kubectl get secrets -n votre-namespace
kubectl describe secret gitlab-registry-secret -n votre-namespace
```

**Tests qui échouent**
```bash
# Debug avec logs étendus
kubectl logs -l app=votre-app -n staging --tail=100
```

### Contacts
- 📧 **Équipe DevOps** : devops@votre-entreprise.com
- 💬 **Slack** : #devops-templates
- 📝 **GitLab Issues** : [Créer un ticket](https://gitlab.com/devops/gitlab-templates/-/issues)

---

🎉 **Félicitations !** Vous êtes maintenant prêt à utiliser nos templates Pipeline-as-a-Platform pour vos projets Spring Boot !
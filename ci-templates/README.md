# Pipeline-as-a-Platform — Getting Started (Spring Boot)

Ce dépôt fournit une plateforme de templates GitLab CI/CD réutilisables pour déployer des apps **Spring Boot** sur **Minikube (staging)**, **K3s (production)** et **GKE (cloud)**, avec **tests**, **qualité**, **sécurité**, **build/push Docker**, **déploiement Kustomize** et **vérifications monitoring**.

## 1) Inclure les templates (pinner une version)
Dans le projet applicatif, créez `.gitlab-ci.yml` minimal :
```yaml
include:
  - project: "my-group/gitlab-templates"
    ref: "v1.0.0"
    file:
      - "shared/variables/global.yml"
      - "shared/variables/computed.yml"
      - "templates/spring-boot/variables/defaults.yml"

      - "templates/core/.gitlab-ci-build.yml"
      - "templates/core/.gitlab-ci-tests.yml"
      - "templates/core/.gitlab-ci-quality.yml"
      - "templates/core/.gitlab-ci-security.yml"
      - "templates/core/.gitlab-ci-docker.yml"
      - "templates/core/.gitlab-ci-kubernetes.yml"
      - "templates/core/.gitlab-ci-k8s-smoke.yml"

      - "shared/variables/dev.yml"
      - "shared/variables/prod.yml"
      - "shared/variables/gke.yml"

      - "templates/platforms/minikube-dev.yml"
      - "templates/platforms/k3s-production.yml"
      - "templates/integrations/monitoring/prometheus-grafana.yml"
      - "templates/platforms/gke-production.yml"

variables:
  APP_NAME: "my-spring-boot-app"
  APP_PORT: "8080"
  SONAR_PROJECT_KEY: "$CI_PROJECT_PATH_SLUG"
  SONAR_PROJECT_NAME: "$CI_PROJECT_NAME"


2) Définir les variables CI requises

Dans GitLab → Settings → CI/CD → Variables :

Qualité / sécurité : SONAR_TOKEN (masked, protected), NVD_API_KEY (masked)

Registry GitLab : CI_REGISTRY_PASSWORD est fourni automatiquement (utiliser avec CI_REGISTRY_USER)

Minikube (staging) : MINIKUBE_TOKEN (masked)

K3s (production) : K3S_SERVER, K3S_CA_CERT, K3S_CLIENT_CERT, K3S_CLIENT_KEY (toutes masked + protected)

GKE (cloud) : GCP_SERVICE_ACCOUNT_KEY (base64), GCP_PROJECT_ID, GKE_CLUSTER_NAME, GKE_ZONE (masked+protected recommandés)

Aucun secret dans le repo. Tout passe par les CI Variables.


3) Structure Kustomize attendue

Dans le repo applicatif :

k8s/
  base/...
  overlays/
    staging/...
    production/...
    gke/...

Le nom d’image est injecté automatiquement via kustomize edit set image ${APP_NAME}=${IMAGE_VERSIONED}.

4) Branches & enchaînement

Staging (Minikube) : push sur experiment (ou regex configurable) → build → test → quality/security → docker → deploy:staging → smoke:staging

Production (K3s) : merge/push sur main → deploy:production → monitoring:verify

GKE : push sur experiment (exemple) → deploy:gke

Ajuster via rules: dans le .gitlab-ci.yml consommateur.

5) Stages

checkout → build → test → security → sonarqube → docker → docker_security → deploy-staging → staging-tests → deploy-production → monitoring-verify → deploy-gke

6) Bonnes pratiques

Marquer MASKED et PROTECTED toutes les variables sensibles

Ajouter un job de lint CI (YAML) si souhaité

Nettoyer les kubeconfigs en after_script: rm -f "$KUBECONFIG" || true

7) Dépannage rapide

Image non mise à jour : vérifier que APP_NAME == nom de l’image dans kustomization.yaml (transformé par le template)

Auth registry : le secret gitlab-registry-secret est recréé par le job → vérifier CI_REGISTRY_USER/PASSWORD

Minikube TLS : insecure-skip-tls-verify: true est activé côté kubeconfig CI

Prometheus : la vérif lit /api/v1/targets et filtre le job par ${APP_NAME}
# VPS Monitor API

API do **VPS Monitor**, um painel pessoal para acompanhar um servidor: CPU, RAM, disco, GPU, temperaturas, pods do Kubernetes, processos do host, sites e banco de dados. Os dados vêm de um **Prometheus**; a API só lê.

## Stack
- Java 21, Spring Boot 4
- Spring Security (JWT HS256, stateless)
- Login de um único usuário: senha (BCrypt) + código TOTP (app autenticador)
- Opcional: exige o JWT do **Cloudflare Access** em toda requisição

## Segurança
- As consultas PromQL são fixas no código. O cliente só escolhe entre métricas e períodos pré-definidos.
- 5 tentativas de login erradas travam o login por 15 minutos. Um código TOTP não pode ser usado duas vezes.
- Com `CF_ACCESS_TEAM_DOMAIN` e `CF_ACCESS_AUD` definidos, toda rota (menos o health) exige o header `Cf-Access-Jwt-Assertion` válido (assinatura, emissor, audiência e validade).

## Variáveis de ambiente
| Variável | Descrição |
|---|---|
| `JWT_SECRET` | Base64, mínimo 32 bytes |
| `JWT_TTL` | Validade do token (ISO-8601, padrão `PT8H`) |
| `ADMIN_USER` | Usuário do site |
| `ADMIN_PASSWORD_HASH` | Hash BCrypt da senha |
| `ADMIN_TOTP_SECRET` | Segredo TOTP em base32 (mínimo 16 caracteres) |
| `PROMETHEUS_URL` | Endereço do Prometheus |
| `CORS_ORIGINS` | Origens permitidas, separadas por vírgula |
| `CF_ACCESS_TEAM_DOMAIN` | Nome da equipe no Cloudflare Zero Trust (`<nome>.cloudflareaccess.com`). Vazio desliga a checagem |
| `CF_ACCESS_AUD` | Tag AUD da aplicação no Cloudflare Access |

Gerar as credenciais:
```bash
openssl rand -base64 32                                   # JWT_SECRET
python3 -c 'import base64,os;print(base64.b32encode(os.urandom(20)).decode())'   # ADMIN_TOTP_SECRET
python3 -c 'import bcrypt,getpass;print(bcrypt.hashpw(getpass.getpass().encode(),bcrypt.gensalt(12)).decode())'  # ADMIN_PASSWORD_HASH
```

## Rodando local
```bash
mvn test              # testes
mvn spring-boot:run   # precisa das variáveis acima
```

## Endpoints
Todos em `/api/**` exigem `Authorization: Bearer <token>`, exceto o login.

| Rota | O que devolve |
|---|---|
| `POST /api/auth/login` | `{username, password, code}` → token |
| `GET /api/auth/me` | Usuário logado |
| `GET /api/monitor/overview` | CPU, RAM, swap, disco, GPU, rede, temperaturas, contadores |
| `GET /api/monitor/series/{metric}?range=` | Série no tempo. `metric`: `cpu`, `ram`, `disk`, `gpu`, `temp`, `net`. `range`: `1h`, `6h`, `24h`, `7d` |
| `GET /api/monitor/apps` | Consumo por app: namespaces do k8s, serviços systemd e processos sem dono (marcados com aviso) |
| `GET /api/monitor/pods` | Pods: fase, pronto, reinícios, CPU, memória e limite |
| `GET /api/monitor/sites` | Sites testados (blackbox): no ar, status HTTP, tempo, validade do certificado, disponibilidade 24h |
| `GET /api/monitor/database` | PostgreSQL: no ar, conexões, tamanho por banco |
| `GET /api/monitor/alerts` | Alertas ativos do Prometheus |
| `GET /actuator/health` | Health check |

## Métricas esperadas no Prometheus
node-exporter (com `--collector.drm` para GPU AMD), kube-state-metrics, cAdvisor (kubelet), process-exporter (nome do grupo `{{.Cgroups}}|{{.Comm}}`), blackbox-exporter e postgres-exporter.

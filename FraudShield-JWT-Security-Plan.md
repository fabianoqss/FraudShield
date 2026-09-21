# FraudShield — Plano: JWT validado por serviço (RS256) em vez de header confiado

> Documento de estudo/implementação pessoal, gerado a partir de uma discussão sobre o
> modelo de autenticação atual do FraudShield. Cobre: o problema identificado, por que a
> solução óbvia ("Resource Server em todo serviço") não é segura do jeito que o projeto
> está hoje, e o plano concreto para corrigir isso.

---

## 1. Contexto — como funciona hoje

O `api-gateway` é o único ponto que entende JWT:

- `JwtAuthenticationFilter` (`api-gateway/.../security/filter/JwtAuthenticationFilter.java`)
  valida o `Authorization: Bearer <token>`, extrai `userId`/`email` dos claims e os
  reescreve como headers `X-User-Id`/`X-User-Email`.
- `UserContextRequestWrapper` garante que qualquer `X-User-Id`/`X-User-Email` que o
  cliente original tenha mandado é **descartado e sobrescrito** — evita spoofing via essas
  duas headers especificamente.
- Os serviços downstream (`account-service`, `transaction-service`, `auth-service`) têm um
  `UserHeaderAuthenticationFilter` que só lê `X-User-Id`, confere que é um UUID válido, e
  popula o `SecurityContextHolder` — **sem validar assinatura nenhuma**. Eles confiam que,
  se o header chegou, é porque passou pelo gateway.

## 2. O problema identificado

No `docker-compose.yml`, cada serviço publica sua porta direto pro host
(`8081:8081`, `8082:8082`, etc.) junto com a do `api-gateway` (`8080:8080`). Nada impede
hoje que alguém bata direto em `localhost:8082` (ou qualquer outra porta de serviço),
pulando o gateway inteiramente. Como o `UserHeaderAuthenticationFilter` só olha o header e
não verifica assinatura, **um request direto pode forjar `X-User-Id: <qualquer-uuid>` e se
passar por qualquer usuário**, sem token nenhum.

Isso já está documentado no `CLAUDE.md`, seção "Why can auth-service/account-service/
transaction-service still be hit directly...", com dois fixes planejados: `NetworkPolicy`
no Kubernetes (Fase 3) e assinar o header com HMAC (`INTERNAL_GATEWAY_SECRET`) — nenhum dos
dois implementado ainda.

## 3. A solução "óbvia" — e por que ela sozinha piora as coisas

Pergunta original: por que não colocar `oauth2ResourceServer().jwt()` (Resource Server) em
cada microsserviço, do jeito que o TradeForge já faz? Aí cada serviço validaria o JWT
localmente e o acesso direto pela porta deixaria de importar — sem token válido, sem
acesso, ponto final.

**O problema:** o algoritmo de assinatura usado hoje é **HS256 (HMAC, simétrico)**.
Confirmado em código — ambos usam a mesma chave, gerada a partir do mesmo `JWT_SECRET`:

```java
// auth-service — JwtService.java (assina)
this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
...
Jwts.builder()...signWith(signingKey)...

// api-gateway — JwtService.java (valida)
this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
...
Jwts.parser().verifyWith(signingKey)...
```

Com HMAC, a mesma chave que **verifica** uma assinatura também consegue **criar** uma nova
assinatura válida — não existe separação entre "poder ler/validar" e "poder emitir".

Hoje só 2 serviços guardam `JWT_SECRET`: `auth-service` (assina) e `api-gateway` (valida).
Se todos os 7 serviços Java passassem a validar localmente, todos precisariam da mesma
`JWT_SECRET`. Resultado: **qualquer um desses 7 comprometido vira capaz de forjar um token
válido para qualquer usuário** — não só de ler/aceitar tokens alheios, mas de literalmente
assinar um JWT novo se passando pelo `auth-service`. Isso troca um problema por outro pior:
em vez de "acesso direto pela porta ignora autenticação", passaríamos a ter "qualquer
serviço comprometido pode emitir identidade para qualquer usuário do sistema inteiro".

## 4. O caminho correto — migrar para RS256 (assimétrico)

RS256 usa um par de chaves:
- **Chave privada** — só quem assina precisa dela. Fica **só no `auth-service`**.
- **Chave pública** — só serve para verificar, não para criar. Pode ser distribuída
  livremente para todos os outros serviços (`api-gateway`, `account-service`,
  `transaction-service`, etc.) sem risco de forja.

Com isso, dá pra colocar Resource Server em cada serviço com segurança: cada um valida o
JWT localmente com a chave pública (fechando o acesso direto pela porta), e mesmo que um
serviço seja comprometido, o atacante só tem a chave pública — não consegue forjar nada
novo. É o mesmo modelo de um Authorization Server OAuth2 "de verdade" (chave assimétrica +
endpoint JWKS), que é inclusive o que já existe no `TradeForge`
(`AuthorizationServerConfig`).

---

## 5. Plano de implementação

### 5.1 Gerar o par de chaves RSA

```bash
# chave privada (2048 bits é o mínimo razoável; 4096 se quiser mais margem)
openssl genpkey -algorithm RSA -out jwt-private.pem -pkeyopt rsa_keygen_bits:2048

# extrai a chave pública correspondente
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem
```

Guardar `jwt-private.pem` **só** no `auth-service` (via variável de ambiente / secret, nunca
commitado — mesma regra já aplicada a `JWT_SECRET` hoje). `jwt-public.pem` pode ir para
`.env`/config de todos os outros serviços — não é segredo, só não deve ser alterável por
ninguém além do processo de deploy.

### 5.2 `auth-service` — assinar com a chave privada

Trocar `JwtService` de `Keys.hmacShaKeyFor(secret)` para carregar um `PrivateKey` RSA e
assinar com `RS256`:

```java
@Service
public class JwtService {

    private final PrivateKey privateKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.private-key}") String privateKeyPem, // carregado do .pem
            @Value("${jwt.expiration-ms}") long expirationMs
    ) throws Exception {
        this.privateKey = loadPrivateKey(privateKeyPem);
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusMillis(expirationMs)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private PrivateKey loadPrivateKey(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }
}
```

### 5.3 Cada serviço (gateway incluso) — validar com Resource Server + chave pública

Adicionar (onde faltar) `spring-boot-starter-oauth2-resource-server`. `account-service` já
tem `spring-boot-starter-security` — só falta essa dependência extra.

```java
@Configuration
public class ResourceServerConfig {

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.public-key}") String publicKeyPem) throws Exception {
        String cleaned = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) publicKey).build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/**").permitAll()
                // ... demais rotas permitAll já existentes (deposit, etc.)
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

Isso substitui o `UserHeaderAuthenticationFilter` de cada serviço — a identidade agora vem
do JWT validado localmente (`jwt.getSubject()` no lugar de ler `X-User-Id`), não mais de um
header confiado sem verificação.

### 5.4 O que fazer com `X-User-Id`/`X-User-Email`

Duas opções, escolher uma:

- **(A) Remover de vez** — cada serviço extrai `userId`/`email` direto do JWT
  (`Authentication.getName()` já resolve pra o `subject`, ou um
  `Converter<Jwt, ...>` customizado se quiser mais claims). Mais simples, uma fonte de
  verdade só.
- **(B) Manter como otimização, mas parar de confiar cegamente nele** — o gateway já
  validou o JWT, então continuar mandando os headers evita cada serviço reparsear o token
  toda vez; mas isso só é seguro se o acesso direto às portas for **também** bloqueado
  (Kubernetes `NetworkPolicy`, Fase 3) — senão o header ainda pode ser forjado por quem
  acessa a porta diretamente, mesmo com Resource Server ativo (a request forjada, sem JWT,
  simplesmente seria rejeitada pelo Resource Server antes de chegar no controller — então
  na prática (B) só é útil **depois** que (A) ou o Resource Server já garante que só
  requests com JWT válido chegam ali).

**Recomendação:** (A). É mais simples, remove a superfície de ataque do header por
completo, e o ganho de performance de (B) é irrelevante nessa escala (parse de JWT local é
da ordem de microssegundos).

### 5.5 `api-gateway` — o que muda

Continua sendo o ponto de entrada único e ainda faz sentido manter o `JwtAuthenticationFilter`
para rejeitar requests malformadas cedo (early exit, evita round-trip desnecessário pra um
serviço downstream) — mas agora isso vira **defesa em profundidade**, não a única camada.
Se preferir simplificar, o gateway também pode virar só mais um Resource Server (mesma
config do item 5.3) e parar de fazer parsing manual com `jjwt` — reduz duplicação de
código, já que a validação "de verdade" agora está em todo lugar mesmo.

### 5.6 Ordem sugerida de execução

1. Gerar o par de chaves (5.1), configurar `auth-service` para assinar com RS256 (5.2).
   Testar `POST /auth/login` isoladamente — confirmar que o token emitido é RS256
   (`header.alg == "RS256"`, dá pra conferir decodificando o JWT em jwt.io ou similar).
2. Adicionar Resource Server + `JwtDecoder` (chave pública) em **um** serviço primeiro —
   sugestão: `account-service`, já que tem mais superfície testável (`POST /accounts`,
   `GET /accounts/{id}/balance`) e testes end-to-end documentados no `CLAUDE.md` pra
   comparar antes/depois.
3. Confirmar que:
   - Request com JWT válido (assinado pela chave privada certa) → autoriza normalmente.
   - Request direto na porta do serviço, sem gateway, **com token válido** → agora
     funciona (esperado — o objetivo não é bloquear acesso direto, é exigir prova de
     identidade real em vez de confiar num header).
   - Request direto na porta, **sem token ou com `X-User-Id` forjado mas sem JWT** → 401,
     confirma que o gap está fechado.
4. Repetir 5.3 para os demais serviços (`transaction-service`, `auth-service` propriamente
   dito para rotas autenticadas como `/auth/password`, `ledger-service` quando existir).
5. Decidir e aplicar 5.4 (remover ou manter os headers).
6. Atualizar `api-gateway` (5.5) e o `docker-compose.yml`/`.env.example` com as novas
   variáveis (`JWT_PRIVATE_KEY` só no `auth-service`, `JWT_PUBLIC_KEY` em todo o resto —
   substituindo o atual `JWT_SECRET` único).
7. Atualizar o `CLAUDE.md` — seção "Security Rules" (hoje diz "JWT secret ... shared") e
   "Known Decisions and Trade-offs" (documentar esta decisão: por que RS256 em vez de
   HS256, e que isso supera/complementa o fix de `NetworkPolicy`/HMAC que já estava
   planejado lá).

---

## 6. O que isso resolve e o que continua em aberto

**Resolve:**
- Acesso direto às portas dos serviços deixa de ser um jeito de forjar identidade — sem
  JWT válido assinado pela chave privada real, não tem `X-User-Id` que valha.
- Elimina o risco de um serviço comprometido conseguir **forjar** tokens (só teria a chave
  pública).

**Não resolve sozinho (continua valendo o que já estava planejado):**
- Revogação imediata de token (login/logout) — RS256 não muda isso, é o mesmo trade-off já
  documentado em "Why refresh-token rotation without a Redis jti blocklist?".
- Isolamento de rede em si (um serviço comprometido ainda pode ser acessado por outro
  serviço na mesma rede Docker/K8s) — isso continua sendo trabalho de `NetworkPolicy`,
  complementar, não substituído por essa mudança.

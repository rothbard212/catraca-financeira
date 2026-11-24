# 🚦 Catraca Financeira

Serviço de checkout para pedidos de e-commerce integrado ao **Mercado Pago**. O pedido é criado, o pagamento é iniciado no gateway (Checkout Pro) e a confirmação chega de forma **assíncrona, por webhook**. O foco do projeto é a parte difícil de pagamentos: **idempotência, validação de assinatura, máquina de estados e resiliência** quando o gateway falha.

Quinto projeto de um portfólio de cinco aplicações.

📄 Requisitos, regras de negócio e decisões: [Requisitos.md](./Requisitos.md)

---

## 📋 O que o sistema faz

- Cria um pedido com itens e calcula o total em `BigDecimal`
- Cria um pagamento pendente e um checkout no Mercado Pago, devolvendo o link de pagamento
- Recebe webhooks, valida a assinatura (HMAC-SHA256), consulta o pagamento na API do gateway e atualiza pagamento e pedido
- Registra todo evento recebido, inclusive os rejeitados, para auditoria

---

## 🏗️ Arquitetura

```
controller/   → PedidoController (checkout), WebhookController (entrada dos eventos)
service/      → PedidoService, WebhookService (regras e transações)
gateway/      → GatewayPagamento (interface), GatewayPagamentoMercadoPago, AssinaturaMercadoPago
model/        → Pedido, ItemPedido, Pagamento, EventoWebhook, StatusPagamento (máquina de estados)
repository/   → Spring Data JPA
config/       → cliente HTTP do Mercado Pago (timeouts)
dto/          → records de entrada e saída
```

**Stack:** Java, Spring Boot, Spring Data JPA, PostgreSQL, Resilience4j, JUnit 5, Mockito.

O gateway fica atrás de uma **interface**: toda a lógica de negócio é testada com um gateway falso, e a integração real é uma classe à parte.

---

## 🧠 Decisões de arquitetura

- **Idempotência pelo banco, não por `if`**: cada evento é gravado primeiro, em uma tabela com constraint `UNIQUE` na chave de idempotência. Reenvios, mesmo simultâneos, são recusados pelo banco, que decide o vencedor.
- **O corpo do webhook nunca é a fonte da verdade**: o serviço consulta o pagamento na API do Mercado Pago e usa o status retornado. Isso também torna a ordem de chegada dos eventos irrelevante.
- **Falha ao falar com o gateway desfaz o evento**: tudo roda em uma transação. Se a consulta falha, o evento não fica gravado, o serviço responde 5xx e o Mercado Pago reenvia. Nenhuma notificação se perde.
- **Máquina de estados**: `PENDENTE → APROVADO | RECUSADO | CANCELADO` e `APROVADO → REEMBOLSADO | CHARGEBACK`. Transições inválidas são registradas, não aplicadas.
- **`external_reference`**: o ID do pagamento local é enviado ao criar o checkout e volta na consulta, que é como o webhook acha o pagamento certo.
- **Valor conferido**: o pedido só vira `PAGO` se o valor pago for igual ao total (`compareTo` em `BigDecimal`).
- **Assinatura em tempo constante**: a comparação do HMAC usa `MessageDigest.isEqual`.
- **Segredos fora do Git**: token e segredo do webhook em `application-local.properties`, ignorado pelo Git.

---

## 🧪 Testes

```bash
./mvnw test
```

**50 testes**, cobrindo:

- Máquina de estados (todas as transições válidas e inválidas, parametrizado)
- Validação de assinatura contra um vetor HMAC conhecido (válida, adulterada, sem cabeçalho, sem `request-id`)
- `WebhookService`: aprovação, recusa, valor divergente, sem mudança, transição inválida, evento duplicado (gateway nunca consultado), pagamento inexistente e `mpPaymentId` diferente
- `PedidoService`: total, pagamento pendente, `external_reference`, falha do gateway
- Tradução dos status do Mercado Pago
- `WebhookController`: tipo ignorado, assinatura inválida, sem id, pagamento válido e duplicado

### Validado contra o Mercado Pago real (sandbox, contas de teste)

- Pedido pago com o comprador de teste: evento `PROCESSADO`, pedido `PAGO`, pagamento `APROVADO` com o `mp_payment_id` gravado
- **Reenvio de uma notificação real (replay):** a constraint recusou o evento duplicado e a aplicação respondeu 200 sem processar de novo
- **Falha de rede ao criar o checkout (timeout):** a transação foi desfeita e nenhum pedido ou pagamento órfão ficou no banco

---

## 🔌 Endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/pedidos` | Cria pedido e pagamento pendente e devolve o link de pagamento |
| POST | `/webhooks/mercadopago` | Recebe notificações do Mercado Pago |

---

## ⚙️ Como rodar

```bash
# 1. Bancos
CREATE DATABASE catraca_financeira;

# 2. Segredos (arquivo ignorado pelo Git): src/main/resources/application-local.properties
spring.datasource.password=<senha do postgres>
mercadopago.access-token=<token da conta de teste do vendedor>
mercadopago.notification-url=https://<tunel>.ngrok-free.dev/webhooks/mercadopago
mercadopago.webhook-secret=<segredo mostrado no painel de webhooks>

# 3. Túnel público para receber webhooks localmente
ngrok http 8080

# 4. Rodar
./mvnw spring-boot:run
```

Para pagar no sandbox: use o `linkPagamento` em janela privada, logado como o **comprador de teste**.

---

## 🐛 Aprendizados de integração

- **Notificações de outros tipos**: o Mercado Pago também envia `merchant_order`, com outro formato e sem assinatura no mesmo padrão. Elas eram rejeitadas e enchiam a tabela de auditoria. Resolvido filtrando o tipo antes de validar a assinatura.
- **URL do webhook**: a notificação chegou na raiz do domínio (404) até a URL ser corrigida com o caminho completo, tanto na configuração quanto no painel.
- **Checkout Pro com contas de teste**: o link só funciona criado pelo vendedor de teste e aberto, em janela privada, pelo comprador de teste.
- **Corpo guardado pela metade**: guardar só o `toString` de um record escondeu o formato real das notificações. Passou a guardar o corpo completo.

---

## 🚧 Limitações conhecidas

- **Reembolso (RF05) e consulta de pedido (RF06) ainda não implementados.** A máquina de estados já prevê `REEMBOLSADO`, e o webhook processa o status quando ele chega, mas não há endpoint para solicitar o reembolso.
- **Várias tentativas de pagamento no mesmo checkout**: o sistema trata uma tentativa por pagamento. Uma segunda tentativa é registrada como `PAGAMENTO_MP_DIFERENTE`, sem alterar nada.
- `in_mediation` é tratado como `APROVADO`.
- `/pedidos` é aberto, sem autenticação.
- O circuit breaker (Resilience4j) está configurado, mas não foi exercitado com falhas repetidas do gateway.
- Sem tolerância de tempo (`ts`) na assinatura, então não há proteção contra replay de notificações antigas.
- Cada requisição com assinatura inválida grava uma linha de auditoria, o que permitiria encher a tabela. Um limite de requisições seria a melhoria.
- Esquema do banco por `ddl-auto=update`; em produção seriam migrações (Flyway).

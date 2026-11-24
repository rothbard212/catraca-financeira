# REQUISITOS.md — Catraca Financeira

> Documento vivo, atualizado conforme novos casos de borda forem descobertos.

---

## 1. Visão Geral

Serviço de checkout para pedidos de e-commerce, integrado ao **Mercado Pago (sandbox)**. O pedido é criado, o pagamento é iniciado no gateway, e a confirmação chega de forma **assíncrona por webhook**. O foco do projeto é a parte difícil de pagamentos: idempotência, validação de assinatura, máquina de estados e resiliência quando o gateway falha. Quinto projeto do portfólio. Stack: Java, Spring Boot, JPA, PostgreSQL.

---

## 2. Requisitos Funcionais

| ID | Requisito |
|----|-----------|
| RF01 | Criar um pedido com itens e valor total, iniciando em `AGUARDANDO_PAGAMENTO` |
| RF02 | Iniciar o pagamento de um pedido no Mercado Pago e devolver ao cliente o link/dados de pagamento |
| RF03 | Expor um endpoint público de webhook para receber notificações do gateway |
| RF04 | Atualizar o status do pagamento e do pedido a partir das notificações confirmadas |
| RF05 | Permitir reembolso de um pagamento aprovado |
| RF06 | Consultar o status atual de um pedido e o histórico dos eventos de pagamento |

---

## 3. Requisitos Não-Funcionais

| ID | Requisito |
|----|-----------|
| RNF01 | O mesmo evento recebido N vezes deve produzir exatamente um efeito (idempotência) |
| RNF02 | O webhook responde rápido; a falha interna deve resultar em novo envio pelo gateway, nunca em perda de evento |
| RNF03 | Chamadas à API do gateway têm timeout e proteção contra falha em cascata (circuit breaker) |
| RNF04 | Segredos (access token, chave de assinatura) ficam fora do Git, em `application-local.properties` |
| RNF05 | Todo evento recebido é registrado, inclusive os rejeitados, para auditoria |

---

## 4. Regras de Negócio

| ID | Regra |
|----|-------|
| RN01 | O status do pagamento segue uma **máquina de estados**: `PENDENTE → APROVADO \| RECUSADO \| CANCELADO`, e `APROVADO → REEMBOLSADO \| CHARGEBACK`. Transições fora desse grafo são rejeitadas |
| RN02 | A assinatura do webhook é validada (HMAC) **antes** de qualquer processamento. Assinatura inválida: resposta 401 e registro do evento |
| RN03 | O corpo do webhook nunca é fonte da verdade. O sistema consulta o pagamento na API do gateway e usa o status retornado |
| RN04 | Idempotência: cada evento é identificado por uma chave única e registrado em tabela com constraint `UNIQUE`. Evento já processado devolve 200 sem repetir efeitos |
| RN05 | O pedido só vira `PAGO` quando o pagamento é `APROVADO` **e** o valor pago confere com o total do pedido |
| RN06 | Efeitos colaterais da aprovação (marcar pedido como pago) acontecem uma única vez, na mesma transação do registro do evento |
| RN07 | Reembolso só é permitido para pagamento `APROVADO` ainda não reembolsado. Pedido de reembolso repetido não gera segundo reembolso |

---

## 5. Casos de Borda

| Cenário | Comportamento esperado | Status |
|---|---|---|
| Webhook duplicado | Segundo recebimento é reconhecido pela chave de idempotência e ignorado (RN04) | ✅ decidido |
| Webhooks duplicados **simultâneos** | `UNIQUE` no banco garante que só um processa; o outro recebe violação e responde 200 | ✅ decidido |
| Evento fora de ordem (ex: "aprovado" antes de "pendente") | Como o status vem da consulta ao gateway (RN03), o estado final é correto independente da ordem | ✅ decidido |
| Webhook chega antes de o pagamento estar salvo localmente | ⬜ decidir: responder erro para o gateway reenviar, ou criar o registro a partir da consulta |
| API do gateway fora do ar ao consultar o pagamento | Responder 5xx para que o Mercado Pago reenvie o webhook; o evento só é marcado como processado depois da consulta bem-sucedida | ✅ decidido |
| Servidor cai depois de o gateway aprovar e antes de salvar | Gateway reenvia o webhook; idempotência garante processamento único | ✅ decidido |
| Valor pago diferente do total do pedido | Pedido não vira `PAGO`, evento fica marcado como divergente para análise (RN05) | ✅ decidido |
| Reembolso duplicado | Rejeitado por RN07 | ✅ decidido |

---

## 6. Modelagem de Dados (rascunho)

| Tabela | Colunas principais | Observações |
|---|---|---|
| `pedidos` | id, valor_total, status, criado_em | |
| `itens_pedido` | id, pedido_id, descricao, quantidade, preco_unitario | |
| `pagamentos` | id, pedido_id, mp_payment_id (único), status, valor, atualizado_em, versao | `@Version` contra atualização concorrente |
| `eventos_webhook` | id, chave_idempotencia (única), payload, assinatura_valida, recebido_em, processado_em, resultado | Auditoria (RNF05) |

---

## 7. Estratégia de Testes

- **Unitário da máquina de estados:** todas as transições válidas e inválidas.
- **Assinatura HMAC:** válida, adulterada, ausente.
- **Idempotência:** mesmo evento duas vezes, e duas threads simultâneas (mesma técnica do teste de concorrência do Painel de Estoque).
- **Integração com o gateway mockado** (WireMock): aprovado, recusado, API fora do ar, timeout.
- **Fora de ordem:** eventos em sequência invertida terminam no mesmo estado final.

---

## 8. Decisões Tomadas

| Decisão | Justificativa |
|---|---|
| Spring Boot + PostgreSQL | Reaproveita a base e o conhecimento do Painel de Estoque |
| Mercado Pago em sandbox | Gateway real, sem cobrança de dinheiro de verdade |
| Consultar o gateway em vez de confiar no corpo do webhook (RN03) | O corpo pode ser forjado ou estar desatualizado; a API é a fonte da verdade |
| Idempotência por constraint `UNIQUE` no banco (RN04) | Proteção que vale mesmo sob concorrência, sem depender de checagem em código |
| Checkout Pro (redirecionamento para o Mercado Pago) | Evita tratar dados de cartão e mantém o foco do projeto no webhook, não no formulário de pagamento |
| Falha ao consultar o gateway resulta em resposta 5xx | Reaproveita o reenvio do próprio gateway, sem construir fila interna de reprocessamento nesta etapa |
| Resilience4j (timeout + circuit breaker) nas chamadas ao gateway | Atende RNF03 com algo concreto e testável |
| Reembolso parcial fora do escopo | Apenas reembolso total, registrado como melhoria futura |

---

## 9. Notas de Ambiente

- Receber webhooks na máquina local exige um túnel público (ex: ngrok) apontando para a porta da aplicação.
- O formato exato da assinatura do Mercado Pago (cabeçalhos e montagem da string assinada) deve ser conferido na **documentação oficial vigente** antes de implementar a validação.

---

## 10. Perguntas em Aberto

- [x] Checkout Pro ou Checkout API: **Checkout Pro**
- [x] Falha ao consultar o gateway: **responder 5xx para reenvio**
- [x] Circuit breaker ou retry simples: **Resilience4j**
- [x] Reembolso parcial: **fora do escopo**
- [ ] Webhook que chega antes de o pagamento estar salvo localmente: responder erro para reenvio ou criar o registro a partir da consulta?

---

## 11. Atualizações de Implementação

### Decisões consolidadas durante o desenvolvimento

| Decisão | Justificativa |
|---|---|
| `external_reference` = ID do `Pagamento` local | No Checkout Pro o ID de pagamento do Mercado Pago só nasce quando o cliente paga; o `external_reference` volta na consulta e permite achar o pagamento local. O `mpPaymentId` é gravado na primeira notificação |
| Chave de idempotência = `id` da notificação (e não o `data.id` do pagamento) | O mesmo pagamento gera várias notificações ao longo da vida; usar o ID do pagamento descartaria a segunda (ex: o reembolso) |
| Filtro de tipo **antes** da validação de assinatura | O Mercado Pago também envia `merchant_order`, em outro formato e sem `data.id`; antes elas eram rejeitadas como assinatura inválida e enchiam a tabela de auditoria |
| Corpo da notificação lido como `Map` | O record com campos fixos descartava os campos desconhecidos e escondeu o formato real das notificações |
| `in_mediation` tratado como `APROVADO` | É uma disputa sobre um pagamento já aprovado; mapear para `PENDENTE` seria uma transição inválida |
| Status desconhecido do gateway vira `PENDENTE` | Um status novo nunca derruba o processamento |
| Nome de status do gateway traduzido dentro da implementação do gateway | O serviço nunca vê os nomes do Mercado Pago |
| Cliente HTTP com timeout de conexão e de leitura; circuit breaker (Resilience4j) | Atende RNF03; circuito aberto resulta em 5xx e reenvio pelo gateway |

### Casos de borda validados na prática (sandbox)

- **Reenvio de notificação real (replay):** recusado pela constraint `UNIQUE`, resposta 200, nenhum efeito repetido.
- **Falha de rede ao criar o checkout:** transação desfeita, nenhum pedido ou pagamento órfão (confirmado pelos IDs ausentes na sequência).
- **Webhook sem pagamento local:** responde 5xx e o evento é desfeito, para o gateway reenviar.

### Perguntas em aberto resolvidas

- Webhook que chega antes de o pagamento estar salvo localmente: responde 5xx para reenvio.

### Perguntas em aberto

- [ ] Várias tentativas de pagamento no mesmo checkout: (A) cada tentativa vira um novo `Pagamento` ligado ao pedido, ou (B) um checkout aceita uma tentativa só.
- [ ] Reembolso (RF05) e consulta de pedido (RF06): ainda sem endpoint.
- [ ] Tolerância de tempo no `ts` da assinatura (proteção contra replay).
- [ ] Autenticação no endpoint `/pedidos`.
- [ ] Limite de requisições para eventos com assinatura inválida.

### Status dos requisitos

RF01 a RF04 implementados e validados contra o Mercado Pago em sandbox. **RF05 (reembolso) e RF06 (consulta) pendentes.**

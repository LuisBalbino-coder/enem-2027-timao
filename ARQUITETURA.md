# Arquitetura — ENEM 2027 Timão

Plataforma web local de estudos para o ENEM 2027, com meta de 600–650+ para Jogos Digitais na UFLA.
Lema pedagógico: **base forte em Humanas e Redação, retorno alto em Matemática, revisão inteligente perto da prova.**

---

## 1. Stack e decisões técnicas

| Camada | Escolha | Motivo |
|---|---|---|
| Front-end | HTML5 + CSS3 puro + JavaScript Vanilla | Sem build de front, roda offline, CSS dinâmico por variáveis |
| Back-end | **Java 21 + Javalin 6.7** | Sobe em ~1 s, build Maven rápido; API REST enxuta e explícita |
| Banco | **H2 2.3 em arquivo** (`data/enem2027.mv.db`) | Relacional, persistente, zero instalação, SQL padrão |
| Build | **Maven 3.9** + jar executável (shade) | Um comando: `./rodar.sh` |
| Versionamento | Git local | Commits por fase, sem push automático |

Instalações locais usadas (sem sudo): JDK 21 Temurin em `~/.local/opt/jdk-21` e Maven em `~/.local/opt/maven`.
O script `rodar.sh` exporta `JAVA_HOME`/`PATH` e recompila sempre que `src/` ou `pom.xml` ficam mais
novos que o jar, evitando subir uma build antiga.

> **Por que não Spring Boot?** Para um app local de usuário único, o ciclo `mvn package` + boot do Javalin é
> bem mais curto, e o acesso direto a JDBC/H2 mantém o SQL visível e auditável — requisito do projeto.

### Estrutura de pastas

```
enem-2027-timao/
├── ARQUITETURA.md
├── ROADMAP.md
├── pom.xml
├── rodar.sh                    # compila (se preciso) e sobe em localhost:7077
├── data/                       # banco H2 local (gitignored)
├── videos/                     # .mp4 locais da Fase 6 (gitignored)
└── src/main/
    ├── java/br/com/timao/enem/
    │   ├── App.java            # bootstrap Javalin + banco + seed
    │   ├── api/Api.java        # rotas REST
    │   ├── dao/                # SQL (ConteudoDao, ProgressoDao, ConfiguracaoDao)
    │   ├── db/                 # conexão, schema, carga de seeds
    │   ├── model/Payloads.java # records de resposta (JSON)
    │   └── service/            # Calendario (fases/urgência) e PlanoEstudos (cronograma)
    └── resources/
        ├── schema.sql
        ├── seed/               # JSONs de conteúdo, aulas em Markdown e calendário.json
        └── public/             # index.html, styles.css, app.js
```

---

## 2. Modelagem do banco (H2)

Chaves naturais (`slug`, `codigo`) tornam a carga de seeds idempotente: reiniciar o servidor atualiza o
conteúdo sem duplicar linhas e sem apagar o progresso do usuário.

| Tabela | Papel | Campos principais |
|---|---|---|
| `usuario` | Dono local do progresso (linha única, id 1) | `nome`, `meta_diaria_minutos`, `meta_questoes_dia` |
| `configuracao` | Preferências editáveis | `chave` (PK), `valor` — data alvo, metas |
| `materia` | Áreas do cronograma | `slug`, `nome`, `area`, `cor`, `prioridade`, `descricao` |
| `topico` | Tema de estudo | `slug`, `materia_id`, `titulo`, `resumo`, `dificuldade`, `fase` (1–3) |
| `aula` | Lição teórica ou videoaula | `topico_id`, `titulo`, `tipo`, `conteudo` (CLOB), `video_url`, `video_arquivo`, `ordem` |
| `questao` | Questão comentada | `codigo`, `topico_id`, `banca`, `ano`, `fonte`, `autoral`, `dificuldade`, `enunciado`, `gabarito`, `comentario` |
| `alternativa` | Opções A–E | `questao_id`, `letra`, `texto` |
| `flashcard` | Cartão de memorização | `codigo`, `topico_id`, `frente`, `verso`, `dica` |
| `flashcard_revisao` | Estado SRS por cartão (Fase 3) | `facilidade`, `intervalo_dias`, `proxima_revisao`, `acertos`, `erros` |
| `aula_concluida` | Progresso real de leitura | PK `(usuario_id, aula_id)`, `concluido_em` |
| `topico_progresso` | Última vez estudado (base do Desafio de Retenção) | `ultima_estudo`, `revisoes`, `proxima_revisao` |
| `tentativa_questao` | Histórico de respostas (Fase 4) | `questao_id`, `resposta`, `correta`, `tempo_seg`, `criado_em` |
| `sessao_estudo` | Meta diária e minutos | `data`, `minutos`, `tipo`, `descricao` |
| `marco` | Calendário pedagógico (provas, simulados, fim de fase) | `codigo` (PK), `data`, `titulo`, `descricao`, `tipo` |
| `redacao` | Rascunhos do laboratório (Fase 6) | `titulo`, `tema`, `texto`, `status`, `nota_estimada` |
| `repertorio` | Banco de citações | `codigo`, `area`, `titulo`, `autor`, `citacao`, `aplicacao`, `tags` |
| `tema_redacao` | Propostas para treinar | `codigo`, `titulo`, `ano`, `proposta`, `textos_motivadores` |
| `competencia_redacao` | As 5 competências do ENEM | `numero`, `titulo`, `descricao`, `criterios` |

São 18 tabelas. Índices criados em FKs e em `questao(dificuldade)`, `topico(materia_id)`, `flashcard(topico_id)`
e `marco(data)`.

### Política de conteúdo dos seeds

- As aulas são **texto autoral**, escritas para a realidade do ENEM (priorizando fáceis/médias da TRI).
- Questões têm o campo `autoral = true` e `fonte = "Autoral — padrão <banca>"`: são **escritas no estilo da
  banca**, não reproduções oficiais. A interface exibe o selo “autoral” para não induzir a erro.
- A **Fase 4** adiciona um importador de provas oficiais (INEP e vestibulares) com gabarito e fonte
  verificada, preenchendo o mesmo schema com `autoral = false`.
- Repertórios e temas de redação são autorais, com aplicação sugerida por eixo temático.

---

## 3. API REST (Fases 1 e 2)

| Método | Rota | Retorno |
|---|---|---|
| GET | `/api/saude` | status, versão, banco, horário |
| GET | `/api/dashboard` | contagem regressiva, urgência, fase pedagógica, metas, resumo de aulas e progresso por matéria |
| GET | `/api/materias` | matérias com contagem de tópicos/aulas e percentual concluído |
| GET | `/api/topicos?materia=slug` | tópicos filtráveis com matéria e contagens |
| GET | `/api/topicos/{slug}` | tópico + aulas (Markdown) + flashcards + contagem de questões |
| GET | `/api/questoes?materia=&dificuldade=&banca=&limite=` | questões com alternativas, gabarito e comentário |
| GET | `/api/flashcards?materia=` | baralho completo (a mecânica SRS chega na Fase 3) |
| GET | `/api/redacao/repertorios` | citações por área |
| GET | `/api/redacao/temas` | propostas de redação |
| GET | `/api/redacao/competencias` | 5 competências e critérios |
| GET | `/api/calendario?mes=AAAA-MM` | grade do mês, marcos e fases (limitado do mês atual ao mês da prova) |
| GET | `/api/cronograma` | plano rolling dos próximos 7 dias, com tarefas e bloqueio da fase 3 |
| POST | `/api/aulas/{id}/concluir` | marca aula como concluída e devolve o total do tópico |
| DELETE | `/api/aulas/{id}/concluir` | desmarca a aula |
| POST | `/api/sessoes` | registra minutos estudados (`{ minutos, tipo?, descricao? }`) |
| GET | `/api/configuracoes` | data alvo, metas, nome |
| PUT | `/api/configuracoes` | atualiza data alvo/metas com validação |

Respostas em JSON `camelCase`; datas em ISO-8601 (`YYYY-MM-DD`). Erros retornam `{ "erro": "..." }` com
status 400/404/500. Sem autenticação: é um app local de usuário único; nenhuma porta é exposta além de
`127.0.0.1:7077`.

---

## 4. Contagem regressiva e CSS dinâmico

`Calendario.java` lê a data do sistema e a data alvo (padrão **2027-11-07**, editável):

```
diasRestantes = ChronoUnit.DAYS.between(hoje, alvo)      // mínimo 0
urgencia      = clamp( round(100 * (1 - diasRestantes / 730.0)), 0, 100 )
```

- **730 dias** é a janela de referência (dois anos). Hoje (~347 dias restantes) a urgência fica perto de 52%:
  o vermelho já respira, mas ainda não domina a tela.
- Fase pedagógica: **1 — Base** enquanto faltar mais de 3 meses; **2 — Aprofundamento** nos 3 meses
  anteriores; **3 — Reta final** no último mês. Na fase 3 o cronograma não agenda aulas novas: bloqueia
  tópicos sem nenhuma aula concluída e monta o dia com revisão, flashcards e erros anteriores.

O `dashboard` devolve `urgencia`; o front escreve `--urgencia` no `:root` e o CSS deriva tudo:

```css
:root { --urgencia: 0; }
body {
  background:
    radial-gradient(120% 80% at 50% -10%,
      color-mix(in srgb, var(--vermelho) calc(4% + var(--urgencia) * 0.16%), transparent), transparent 60%),
    var(--preto);
}
.cartao { border-color: color-mix(in srgb, var(--vermelho) calc(18% + var(--urgencia) * 0.55%), #2a2a2a); }
.medidor { box-shadow: 0 0 calc(8px + var(--urgencia) * 0.22px) color-mix(in srgb, var(--vermelho) 70%, transparent); }
```

Além do gradiente, o JS aplica `body.nivel-2` / `body.nivel-3` para intensificar bordas, brilho e pulsação
(respeitando `prefers-reduced-motion`). A data alvo é editável na engrenagem do topo.

---

## 5. Cronograma, meta e progresso (Fase 2)

O cronograma é **rolling**: sempre os próximos 7 dias a partir de hoje, recalculado a cada requisição.

- **Fases 1 e 2:** cada matéria tem uma fila de aulas pendentes; a escolhida é a de maior
  `peso / (agendados + 1)`, com peso **3** para História, Geografia e Redação, **2** para Matemática e
  **1** para Linguagens e Ciências da Natureza. Isso intercala prioridades sem abandonar as demais.
- **Fase 2:** dentro de cada fila, temas difíceis e médios vêm primeiro; o sábado ganha um simulado
  cronometrado de 20 questões, mesmo que não haja aula pendente na fila.
- **Domingo:** revisão de flashcards/resumos da semana e refaça de questões erradas.
- **Fase 3:** nenhuma aula nova; o plano reveza apenas tópicos com aula concluída (resumo + flashcards)
  e questões. Sem histórico, cai em revisão geral de resumos e erros.
- **Virada de fase:** a fase é recalculada para cada um dos 7 dias. Se a semana atravessar a reta final,
  os dias seguintes já entram como revisão e não recebem aula nova.
- **Progresso:** `aula_concluida` é a fonte da verdade; o dashboard calcula `concluidas/total` e o
  percentual de cada matéria direto do banco.
- **Meta:** cada sessão grava `data`, `minutos` e `tipo` em `sessao_estudo`; a meta do dia soma as
  sessões de hoje. Os atalhos +15/+30/+60 min usam o mesmo endpoint.
- **Marcos:** `marco` guarda 10 datas pedagógicas (15/12/2026 a 07/11/2027), carregadas de
  `seed/calendario.json` de forma idempotente.

---

## 6. Como rodar

```bash
cd enem-2027-timao
./rodar.sh                 # http://127.0.0.1:7077
```

- Requisitos já instalados nesta máquina: `~/.local/opt/jdk-21`, `~/.local/opt/maven`.
- `./rodar.sh` já recompila quando há fonte mais novo que o jar. Para forçar: `mvn -q -DskipTests package`.
- Banco: `data/enem2027.mv.db`. Para recriar do zero, pare o servidor e apague a pasta `data/`.
- Porta alternativa: `PORT=8080 ./rodar.sh`.

---

## 7. Limites conhecidos da Fase 2

- Quiz é **modo leitura**: revela gabarito/comentário, mas ainda não grava tentativa (Fase 4).
- Flashcards são exibidos como baralho; repetição espaçada e botões Errei/Difícil/Fácil chegam na Fase 3.
- O cronograma é automático: ainda não permite arrastar, trocar ou pular uma tarefa manualmente.
- A navegação do calendário vai do mês atual ao mês da prova; meses passados não são editáveis.
- Vídeos e editor de redação entram na Fase 6; o schema já reserva as tabelas.
- Sem login multiusuário por decisão de escopo (plataforma local e pessoal).

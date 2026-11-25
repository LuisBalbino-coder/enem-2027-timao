# Roadmap — ENEM 2027 Timão

Meta: **600–650+** no ENEM 2027 para Jogos Digitais (UFLA), com prioridade em Humanas + Redação,
fundamentos de alto retorno em Matemática e revisão inteligente na reta final.

Cada fase termina com build, teste local, banco populado e commit próprio.

---

## Fase 1 — Fundação: backend, banco, schema e conteúdo inicial ✅

**Status: concluída em 25/11/2026.**

- [x] Repositório Git, `ARQUITETURA.md`, `ROADMAP.md`, `.gitignore`, `rodar.sh`
- [x] Backend Java 21 + Javalin + H2 em arquivo, jar executável
- [x] Schema completo (17 tabelas) + índices + carga idempotente de seeds
- [x] Conteúdo inicial real: 6 matérias, 23 tópicos, 24 aulas, 36 questões, 47 flashcards, 12 repertórios, 5 temas e 5 competências
- [x] Banco de questões comentadas (ENEM e outras bancas, marcadas como autorais)
- [x] Dashboard com contagem regressiva dinâmica, índice de urgência e tema Corinthians
- [x] Trilhas de leitura, banco de questões em modo leitura, central de redação e configurações
- [x] Verificação: `mvn package`, boot do servidor, endpoints e telas respondendo

**Aceite:** `./rodar.sh` sobe em `127.0.0.1:7077`; dashboard mostra dias restantes e urgência; trilhas e
questões vêm do banco (nada fixo no HTML).

**Verificado em 25/11/2026:** build Maven sem erros, API e arquivos estáticos respondendo 200, seed
idempotente após reinício, dashboard/trilhas/questões/redação renderizando sem erros no console.

---

## Fase 2 — Cronograma inteligente e progresso persistido ✅

**Status: concluída em 25/11/2026.**

- [x] Calendário mensal navegável até novembro/2027, com marcos por mês (tabela `marco`)
- [x] Cronograma semanal gerado a partir da prioridade das matérias e da fase pedagógica
- [x] Meta diária com anel de progresso e registro de minutos (`sessao_estudo`)
- [x] Marcar aula como concluída e progresso por matéria calculado de `aula_concluida`
- [x] Curva adaptativa: Fase 1 (base) → Fase 2 (médias/difíceis e simulados) → Fase 3 (revisão)
- [x] Bloqueio automático de conteúdo novo pesado na Fase 3
- [x] `rodar.sh` recompila quando `src/` ou `pom.xml` ficam mais novos que o jar

**Aceite:** recarregar o navegador mantém o progresso; mudar a data do sistema move a fase pedagógica.

**Verificado em 25/11/2026:** build Maven sem erros; calendário de nov/2026 a nov/2027 com 10 marcos;
cronograma de 7 dias intercalando História, Geografia, Redação, Matemática e demais matérias; conclusão
de aula persistida e refletida no dashboard (0% → 20% em História); +15 min gravado e mantido após reload;
fase 3 (data alvo simulada) bloqueou 22 de 23 tópicos, liberando apenas o tema já estudado; console sem erros.

**Revisão em 25/11/2026:** em banco temporário, a fase 2 escolheu 2 temas difíceis e 4 médios, nenhum
fácil; o sábado recebeu um simulado de 20 questões tipado como `simulado`, inclusive com 24/24 aulas
concluídas; a virada para a fase 3 no meio da semana não agendou aula nova. O banco principal não foi tocado.

---

## Fase 3 — Flashcards com repetição espaçada 🔜

- [ ] Motor SRS simplificado (Errei / Difícil / Fácil → intervalo 1, 3, 7, 15, 30 dias)
- [ ] Tela de revisão diária com fila vencida e animação de virar cartão
- [ ] Persistência em `flashcard_revisao` e estatísticas de acerto
- [ ] Cartões de História, Geografia, fórmulas e conectivos de redação

**Aceite:** responder “Fácil” reagenda o cartão e ele some da fila do dia; “Errei” o traz de volta amanhã.

---

## Fase 4 — Quiz, simulados e banco oficial de provas 🔜

- [ ] Responder questões com correção imediata e gravação em `tentativa_questao`
- [ ] Filtros por matéria, dificuldade, banca e ano; modo simulado cronometrado
- [ ] Estatísticas por tópico (acerto, tempo médio, pontos fracos)
- [ ] Importador de provas oficiais (INEP/FUVEST/UNICAMP/UNESP/UFLA/UEL) com `autoral = false`
- [ ] Explicação do Tri: acertar fáceis/médias vale mais que arriscar difíceis

**Aceite:** simulado de 20 questões gera resultado persistido e lista de erros para revisão.

---

## Fase 5 — Desafio de Retenção programado 🔜

- [ ] Registrar data de estudo por tópico (`topico_progresso.ultima_estudo`)
- [ ] Desbloquear na home desafios de 7, 15 e 30 dias sobre temas já estudados
- [ ] Misturar questões de bancas diferentes para o mesmo tema
- [ ] Repetir erros anteriores antes de liberar conteúdo novo

**Aceite:** tópico estudado há 7 dias aparece automaticamente como desafio com questões daquele tema.

---

## Fase 6 — Videoaulas e Laboratório de Redação 🔜

- [ ] Central de videoaulas por matéria/tópico, com embeds gratuitos cadastrados no banco
- [ ] Player local para `.mp4` da pasta `videos/` e marcação de aula assistida
- [ ] Editor de redação com contador de linhas (máx. 30) e caracteres
- [ ] Checklist das 5 competências, banco de repertórios e salvamento de rascunhos
- [ ] Temas de treino com textos motivadores

**Aceite:** rascunho salvo reabre com o texto; aula em vídeo local marca progresso ao concluir.

---

## Fase 7 — Reta final, Modo Revisão Rápida e polimento 🔜

- [ ] Modo Revisão Rápida automático no último mês (resumos, flashcards, mapas mentais)
- [ ] Revisão só de temas estudados e erros anteriores
- [ ] Exportar/importar progresso (backup local)
- [ ] Acessibilidade, atalhos de teclado e revisão de contraste
- [ ] Testes de ponta a ponta do fluxo diário e revisão final de conteúdo

**Aceite:** simular data próxima da prova ativa modo revisão, esconde conteúdo novo e prioriza erros.

---

## Mapa pedagógico (constante nas fases)

| Período | Fase | Foco |
|---|---|---|
| até jul/2027 | 1 — Base | Teoria completa de História e Geografia, repertório de redação, fundamentos de Matemática |
| ago–set/2027 | 2 — Aprofundamento | Questões médias/difíceis, simulados cronometrados, redação intensiva |
| out–nov/2027 | 3 — Reta final | Revisão rápida, flashcards, erros anteriores, nada de conteúdo novo pesado |

Prioridade de estudo: **História + Geografia + Redação** → **Matemática (alto retorno)** → **Linguagens** →
**Ciências da Natureza**.

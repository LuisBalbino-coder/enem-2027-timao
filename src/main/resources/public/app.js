(() => {
  "use strict";

  const estado = {
    dashboard: null,
    materias: [],
    topicos: [],
    questoes: [],
    repertorios: [],
    temas: [],
    competencias: [],
    config: null,
    materiaAtiva: "",
    detalheTopico: null,
    filtros: { materia: "", dificuldade: "", banca: "" },
    respostas: new Map(),
    acertos: 0,
    respondidas: 0,
    abaRedacao: "competencias",
    calendario: null,
    cronograma: null,
    mesCalendario: "",
    aulasAbertas: new Set(),
    view: "painel"
  };

  const $ = (seletor, raiz = document) => raiz.querySelector(seletor);
  const $$ = (seletor, raiz = document) => Array.from(raiz.querySelectorAll(seletor));

  function escapar(valor) {
    return String(valor ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  async function api(caminho, opcoes = {}) {
    const resposta = await fetch(caminho, {
      headers: { "Content-Type": "application/json" },
      ...opcoes
    });
    if (!resposta.ok) {
      let mensagem = "Erro ao falar com o servidor.";
      try {
        const corpo = await resposta.json();
        if (corpo && corpo.erro) mensagem = corpo.erro;
      } catch (_) {
        /* resposta sem JSON */
      }
      throw new Error(mensagem);
    }
    return resposta.json();
  }

  function formatarData(iso) {
    if (!iso) return "";
    const [ano, mes, dia] = iso.split("-").map(Number);
    return new Intl.DateTimeFormat("pt-BR", {
      day: "numeric",
      month: "long",
      year: "numeric"
    }).format(new Date(ano, mes - 1, dia));
  }

  function percentual(parte, total) {
    if (!total) return 0;
    return Math.max(0, Math.min(100, Math.round((parte / total) * 100)));
  }

  function rotuloDificuldade(valor) {
    return { facil: "Fácil", medio: "Médio", dificil: "Difícil" }[valor] || valor;
  }

  function classeDificuldade(valor) {
    return valor === "dificil" ? "vermelho" : valor === "facil" ? "verde" : "";
  }

  // ------------------------------------------------------------ markdown

  function inline(texto) {
    return escapar(texto)
      .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
      .replace(/`(.+?)`/g, "<code>$1</code>");
  }

  function markdown(texto) {
    if (!texto) return "";
    const linhas = String(texto).replace(/\r\n/g, "\n").split("\n");
    const saida = [];
    const paragrafo = [];
    let lista = null;
    let i = 0;

    const fecharParagrafo = () => {
      if (paragrafo.length) {
        saida.push(`<p>${inline(paragrafo.join(" "))}</p>`);
        paragrafo.length = 0;
      }
    };
    const fecharLista = () => {
      if (lista) {
        saida.push(`</${lista}>`);
        lista = null;
      }
    };
    const fecharBlocos = () => {
      fecharParagrafo();
      fecharLista();
    };

    while (i < linhas.length) {
      const linha = linhas[i].trim();

      if (!linha) {
        fecharBlocos();
        i += 1;
        continue;
      }

      if (linha.startsWith("|")) {
        fecharBlocos();
        const bloco = [];
        while (i < linhas.length && linhas[i].trim().startsWith("|")) {
          bloco.push(linhas[i].trim());
          i += 1;
        }
        const uteis = bloco.filter((l) => !/^\|[\s:|-]+\|$/.test(l));
        const celulas = uteis.map((l) => l.split("|").slice(1, -1).map((c) => c.trim()));
        if (celulas.length) {
          const cabecalho = celulas[0];
          const corpo = celulas.slice(1);
          saida.push(
            '<div class="tabela-rolagem"><table><thead><tr>' +
              cabecalho.map((c) => `<th>${inline(c)}</th>`).join("") +
              "</tr></thead><tbody>" +
              corpo
                .map((linhaTabela) => "<tr>" + linhaTabela.map((c) => `<td>${inline(c)}</td>`).join("") + "</tr>")
                .join("") +
              "</tbody></table></div>"
          );
        }
        continue;
      }

      if (linha.startsWith("### ")) {
        fecharBlocos();
        saida.push(`<h3>${inline(linha.slice(4))}</h3>`);
        i += 1;
        continue;
      }
      if (linha.startsWith("## ")) {
        fecharBlocos();
        saida.push(`<h2>${inline(linha.slice(3))}</h2>`);
        i += 1;
        continue;
      }
      if (linha.startsWith("# ")) {
        fecharBlocos();
        saida.push(`<h2>${inline(linha.slice(2))}</h2>`);
        i += 1;
        continue;
      }
      if (linha === "---") {
        fecharBlocos();
        saida.push("<hr>");
        i += 1;
        continue;
      }
      if (linha.startsWith("> ")) {
        fecharBlocos();
        const citacao = [];
        while (i < linhas.length && linhas[i].trim().startsWith("> ")) {
          citacao.push(linhas[i].trim().slice(2));
          i += 1;
        }
        saida.push(`<blockquote>${inline(citacao.join(" "))}</blockquote>`);
        continue;
      }

      const ordenado = linha.match(/^(\d+)\.\s+(.*)$/);
      if (linha.startsWith("- ") || ordenado) {
        fecharParagrafo();
        const tipo = ordenado ? "ol" : "ul";
        if (lista !== tipo) {
          fecharLista();
          lista = tipo;
          saida.push(`<${tipo}>`);
        }
        saida.push(`<li>${inline(ordenado ? ordenado[2] : linha.slice(2))}</li>`);
        i += 1;
        continue;
      }

      fecharLista();
      paragrafo.push(linha);
      i += 1;
    }

    fecharBlocos();
    return saida.join("");
  }

  // ------------------------------------------------------------- eventos

  function ligarEventos() {
    document.addEventListener("click", (evento) => {
      const concluir = evento.target.closest("[data-concluir-aula]");
      if (concluir) {
        evento.preventDefault();
        alternarAula(Number(concluir.dataset.concluirAula));
        return;
      }

      const sessao = evento.target.closest("[data-sessao]");
      if (sessao) {
        evento.preventDefault();
        registrarMinutos(Number(sessao.dataset.sessao));
        return;
      }

      const mes = evento.target.closest("[data-mes]");
      if (mes) {
        evento.preventDefault();
        if (mes.dataset.mes === "hoje") {
          carregarCalendario(estado.dashboard.hoje.slice(0, 7));
        } else {
          const [ano, numeroMes] = estado.mesCalendario.split("-").map(Number);
          carregarCalendario(novoMes(ano, numeroMes, mes.dataset.mes === "proximo" ? 1 : -1));
        }
        return;
      }

      const treinar = evento.target.closest("[data-treinar-materia]");
      if (treinar) {
        evento.preventDefault();
        estado.filtros.materia = treinar.dataset.treinarMateria || "";
        mostrarView("questoes");
        renderQuestoes();
        return;
      }

      const view = evento.target.closest("[data-view]");
      if (view) {
        evento.preventDefault();
        mostrarView(view.dataset.view);
        return;
      }

      const materia = evento.target.closest("[data-materia]");
      if (materia) {
        evento.preventDefault();
        irParaMateria(materia.dataset.materia);
        return;
      }

      const topico = evento.target.closest("[data-topico]");
      if (topico) {
        evento.preventDefault();
        abrirTopico(topico.dataset.topico);
        return;
      }

      const filtroMateria = evento.target.closest("[data-filtro-materia]");
      if (filtroMateria) {
        estado.materiaAtiva = filtroMateria.dataset.filtroMateria;
        estado.detalheTopico = null;
        renderTrilhas();
        return;
      }

      if (evento.target.closest("[data-voltar-trilhas]")) {
        estado.detalheTopico = null;
        renderConteudoTrilhas();
        return;
      }

      const alternativa = evento.target.closest("[data-questao]");
      if (alternativa) {
        responder(
          Number(alternativa.dataset.questao),
          alternativa.dataset.letra
        );
        return;
      }

      const abaRedacao = evento.target.closest("[data-redacao-aba]");
      if (abaRedacao) {
        estado.abaRedacao = abaRedacao.dataset.redacaoAba;
        renderRedacao();
        return;
      }

      if (evento.target.closest("[data-recarregar]")) {
        window.location.reload();
        return;
      }

      if (evento.target.closest("#abrir-config")) {
        abrirConfig();
        return;
      }

      if (evento.target.closest("[data-fechar-config]")) {
        fecharConfig();
      }
    });

    document.addEventListener("change", (evento) => {
      const filtro = evento.target.closest("[data-filtro]");
      if (!filtro) return;
      estado.filtros[filtro.dataset.filtro] = filtro.value;
      renderListaQuestoes();
    });

    $("#form-config").addEventListener("submit", salvarConfig);
    $("#modal-config").addEventListener("click", (evento) => {
      if (evento.target.id === "modal-config") fecharConfig();
    });
    document.addEventListener("keydown", (evento) => {
      if (evento.key === "Escape" && !$("#modal-config").classList.contains("oculto")) {
        fecharConfig();
      }
    });

    document.addEventListener("toggle", (evento) => {
      const detalhes = evento.target.closest("details.aula");
      if (!detalhes) return;
      const id = Number(detalhes.dataset.aulaId);
      if (detalhes.open) estado.aulasAbertas.add(id);
      else estado.aulasAbertas.delete(id);
    }, true);
  }

  function mostrarView(nome) {
    estado.view = nome;
    $$(".view").forEach((view) => view.classList.toggle("ativa", view.id === `view-${nome}`));
    $$(".aba").forEach((aba) => {
      const ativa = aba.dataset.view === nome;
      aba.classList.toggle("ativa", ativa);
      if (ativa) aba.setAttribute("aria-current", "page");
      else aba.removeAttribute("aria-current");
    });
    window.scrollTo({ top: 0, behavior: "smooth" });

    if (nome === "calendario" && !estado.calendario) {
      const mes = estado.mesCalendario || (estado.dashboard ? estado.dashboard.hoje.slice(0, 7) : "");
      carregarCalendario(mes);
    }
  }

  function irParaMateria(slug) {
    estado.materiaAtiva = slug || "";
    estado.detalheTopico = null;
    mostrarView("trilhas");
    renderTrilhas();
  }

  // -------------------------------------------------------------- painel

  function aplicarUrgencia(urgencia) {
    document.documentElement.style.setProperty("--urgencia", String(urgencia));
    document.body.classList.toggle("nivel-2", urgencia >= 60);
    document.body.classList.toggle("nivel-3", urgencia >= 80);
  }

  function renderPainel() {
    const d = estado.dashboard;
    const pctMin = percentual(d.meta.minutosHoje, d.meta.minutosMeta);
    const pctQuestoes = percentual(d.meta.questoesHoje, d.meta.questoesMeta);
    const planoHoje = estado.cronograma && estado.cronograma.dias.length ? estado.cronograma.dias[0] : null;
    const aulasConcluidas = d.resumo ? d.resumo.concluidas : 0;
    const aulasTotal = d.resumo ? d.resumo.total : 0;
    const pctAulas = percentual(aulasConcluidas, aulasTotal);

    const materias = d.materias
      .map(
        (m) => `
        <button class="materia-card" style="--cor:${escapar(m.cor)}" data-materia="${escapar(m.slug)}">
          <div class="materia-cabecalho">
            <span class="materia-letra" aria-hidden="true">${escapar(m.nome.charAt(0))}</span>
            <div>
              <h3>${escapar(m.nome)}</h3>
              <span class="area">${escapar(m.area)}</span>
            </div>
          </div>
          <p class="descricao">${escapar(m.descricao)}</p>
          <div class="materia-rodape">
            <span>${m.topicos} tópicos · ${m.aulas} aulas</span>
            <span>${m.percentual}%</span>
          </div>
          <div class="barra" role="progressbar" aria-valuenow="${m.percentual}" aria-valuemin="0" aria-valuemax="100" aria-label="Progresso em ${escapar(m.nome)}">
            <i style="width:${m.percentual}%"></i>
          </div>
        </button>`
      )
      .join("");

    const proximos = (d.proximos || [])
      .map(
        (t) => `
        <button class="proximo-card" data-topico="${escapar(t.slug)}">
          <small>${escapar(t.materiaNome)}</small>
          <strong>${escapar(t.titulo)}</strong>
          <span>${t.aulas} aula(s) · ${t.questoes} questão(ões)</span>
        </button>`
      )
      .join("");

    $("#view-painel").innerHTML = `
      <section class="hero">
        <div class="hero-texto">
          <span class="selo">Fase ${d.fase.numero} · ${escapar(d.fase.nome)}</span>
          <p class="hero-kicker">Contagem regressiva para o ENEM 2027</p>
          <p class="dias"><strong>${d.diasRestantes}</strong><span>dias</span></p>
          <p class="alvo">${escapar(formatarData(d.dataAlvo))} · ${escapar(d.fase.descricao)}</p>
          <div class="acoes">
            <button class="botao" data-view="trilhas">Começar a estudar</button>
            <button class="botao-fantasma" data-view="questoes">Treinar questões</button>
          </div>
        </div>
        <div class="medidor">
          <div class="anel">
            <svg viewBox="0 0 120 120" aria-hidden="true">
              <circle class="trilha" cx="60" cy="60" r="52"></circle>
              <circle class="progresso" cx="60" cy="60" r="52"></circle>
            </svg>
            <div class="medidor-centro">
              <strong>${d.urgencia}%</strong>
              <span>urgência</span>
            </div>
          </div>
          <p class="medidor-legenda">Quanto mais perto de novembro de 2027, mais intenso fica o vermelho na tela.</p>
        </div>
      </section>

      <div class="grade-metricas">
        <div class="metrica">
          <small>Meta de hoje</small>
          <strong>${d.meta.minutosHoje}<span class="alvo-meta"> / ${d.meta.minutosMeta} min</span></strong>
          <div class="barra" aria-hidden="true"><i style="width:${pctMin}%"></i></div>
        </div>
        <div class="metrica">
          <small>Questões hoje</small>
          <strong>${d.meta.questoesHoje}<span class="alvo-meta"> / ${d.meta.questoesMeta}</span></strong>
          <div class="barra" aria-hidden="true"><i style="width:${pctQuestoes}%"></i></div>
        </div>
        <div class="metrica">
          <small>Progresso do curso</small>
          <strong>${aulasConcluidas}<span class="alvo-meta"> / ${aulasTotal} aulas</span></strong>
          <div class="barra" aria-hidden="true"><i style="width:${pctAulas}%"></i></div>
        </div>
        <div class="metrica">
          <small>Questões no banco</small>
          <strong>${d.contadores.questoes}</strong>
          <div class="barra" aria-hidden="true"><i style="width:100%"></i></div>
        </div>
      </div>

      <section class="secao">
        <div class="secao-topo">
          <h2>Meta de hoje</h2>
          <p>${d.meta.minutosHoje} de ${d.meta.minutosMeta} min registrados</p>
        </div>
        <div class="meta-painel">
          <div class="anel-meta" style="--pct:${pctMin}" role="img"
               aria-label="${pctMin}% da meta diária de estudo">
            <div class="anel-meta-centro">
              <strong>${pctMin}%</strong>
              <small>${d.meta.minutosHoje} / ${d.meta.minutosMeta} min</small>
            </div>
          </div>
          <div class="meta-info">
            <h3>Registre o que já estudou</h3>
            <p>Cada sessão é salva no banco e atualiza a meta do dia.</p>
            <div class="presets">
              <button class="preset" data-sessao="15">+15 min</button>
              <button class="preset" data-sessao="30">+30 min</button>
              <button class="preset" data-sessao="60">+60 min</button>
            </div>
          </div>
        </div>
      </section>

      <section class="secao">
        <div class="secao-topo">
          <h2>Suas matérias</h2>
          <p>${d.contadores.materias} matérias · ${d.contadores.aulas} aulas · ${d.contadores.flashcards} flashcards</p>
        </div>
        <div class="materias-grid">${materias}</div>
      </section>

      <section class="secao">
        <div class="secao-topo">
          <h2>Comece por aqui</h2>
          <button class="link-secao" data-view="trilhas">Ver todas as trilhas →</button>
        </div>
        <div class="proximos">${proximos}</div>
      </section>

      ${planoHoje ? `
      <section class="secao">
        <div class="secao-topo">
          <h2>Plano de hoje</h2>
          <button class="link-secao" data-view="calendario">Ver a semana →</button>
        </div>
        <div class="cartao plano-hoje">
          <div class="plano-cabecalho">
            <span class="badge vermelho">${escapar(rotuloTipo(planoHoje.tipo))}</span>
            <strong>${escapar(planoHoje.titulo)}</strong>
            <span class="plano-minutos">${planoHoje.minutos} min</span>
          </div>
          <ul class="tarefas">${tarefasHtml(planoHoje.tarefas)}</ul>
        </div>
      </section>` : ""}

      <section class="secao">
        <div class="secao-topo">
          <h2>Fase pedagógica</h2>
          <p>Até ${escapar(formatarData(d.fase.ate))}</p>
        </div>
        <div class="cartao">
          <p><strong style="color:var(--branco)">${escapar(d.fase.nome)}.</strong> ${escapar(d.fase.descricao)}</p>
          <p style="margin-top:.5rem;color:var(--cinza-2);font-size:.85rem">
            Meta 600–650+ para Jogos Digitais na UFLA: priorize História, Geografia e Redação,
            garanta os fundamentos de Matemática e revise sempre os erros.
          </p>
        </div>
      </section>
    `;
  }

  // -------------------------------------------------------------- trilhas

  function renderTrilhas() {
    const filtros = estado.materias
      .map(
        (m) => `
        <button class="filtro ${estado.materiaAtiva === m.slug ? "ativo" : ""}"
                data-filtro-materia="${escapar(m.slug)}" style="--cor:${escapar(m.cor)}">
          <span class="ponto" aria-hidden="true"></span>${escapar(m.nome)}
        </button>`
      )
      .join("");

    $("#view-trilhas").innerHTML = `
      <div class="trilhas-layout">
        <aside class="filtro-materias" aria-label="Filtrar por matéria">
          <button class="filtro ${estado.materiaAtiva === "" ? "ativo" : ""}" data-filtro-materia="">
            <span class="ponto" aria-hidden="true" style="--cor:#D90429"></span>Todas
          </button>
          ${filtros}
        </aside>
        <div id="conteudo-trilhas"></div>
      </div>
    `;
    renderConteudoTrilhas();
  }

  function renderConteudoTrilhas() {
    const alvo = $("#conteudo-trilhas");
    if (!alvo) return;

    if (estado.detalheTopico) {
      alvo.innerHTML = montarDetalheTopico(estado.detalheTopico);
      return;
    }

    const topicos = estado.materiaAtiva
      ? estado.topicos.filter((t) => t.materiaSlug === estado.materiaAtiva)
      : estado.topicos;
    const nomeMateria = estado.materiaAtiva
      ? (estado.materias.find((m) => m.slug === estado.materiaAtiva) || {}).nome || ""
      : "Todas as matérias";

    const retaFinal = estado.dashboard && estado.dashboard.fase.numero === 3;
    const cartoes = topicos.length
      ? topicos
          .map((t) => {
            const bloqueado = retaFinal && t.aulasConcluidas === 0;
            return `
          <button class="topico-card ${bloqueado ? "bloqueado" : ""}" data-topico="${escapar(t.slug)}" ${bloqueado ? "disabled" : ""}>
            <div class="badges">
              <span class="badge vermelho">${escapar(t.materiaNome)}</span>
              <span class="badge ${classeDificuldade(t.dificuldade)}">${escapar(rotuloDificuldade(t.dificuldade))}</span>
              <span class="badge">Fase ${t.fase}</span>
              ${bloqueado ? '<span class="badge vermelho">Bloqueado na reta final</span>' : ""}
            </div>
            <h3>${escapar(t.titulo)}</h3>
            <p>${escapar(t.resumo)}</p>
            <div class="materia-rodape">
              <span>${t.aulasConcluidas}/${t.aulas} aulas · ${t.flashcards} flashcards</span>
              <span>${t.questoes} questão(ões)</span>
            </div>
          </button>`;
          })
          .join("")
      : '<p class="filtro-vazio">Nenhum tópico nesta matéria ainda.</p>';

    const avisoReta = retaFinal
      ? '<div class="aviso-fase"><span aria-hidden="true">✦</span><span><strong>Reta final:</strong> conteúdo novo bloqueado. Priorize revisar os temas já estudados, os flashcards e os erros anteriores.</span></div>'
      : "";

    alvo.innerHTML = `
      <div class="cabecalho-lista">
        <h2>${escapar(nomeMateria)}</h2>
        <span>${topicos.length} tópico(s)</span>
      </div>
      ${avisoReta}
      <div class="lista-topicos">${cartoes}</div>
    `;
  }

  function montarDetalheTopico(detalhe) {
    const t = detalhe.topico;
    const aulas = (detalhe.aulas || [])
      .map(
        (aula) => `
        <details class="aula" data-aula-id="${aula.id}" ${estado.aulasAbertas.has(aula.id) ? "open" : ""}>
          <summary>
            <span>${escapar(aula.titulo)}</span>
            <span class="badge">${aula.duracaoMin ? aula.duracaoMin + " min" : "leitura"}</span>
          </summary>
          <div class="aula-corpo">
            ${aula.conteudo ? `<div class="markdown">${markdown(aula.conteudo)}</div>` : "<p>Conteúdo em preparação.</p>"}
            <div class="aula-rodape">
              <button class="botao-concluir ${aula.concluida ? "concluida" : ""}"
                      data-concluir-aula="${aula.id}" data-concluida="${aula.concluida}">
                ${aula.concluida ? "✓ Aula concluída" : "Marcar como concluída"}
              </button>
            </div>
          </div>
        </details>`
      )
      .join("");

    const flashcards = (detalhe.flashcards || [])
      .map(
        (f) => `
        <details class="flashcard">
          <summary>${escapar(f.frente)}</summary>
          <p>${escapar(f.verso)}${f.dica ? `<span class="dica">Dica: ${escapar(f.dica)}</span>` : ""}</p>
        </details>`
      )
      .join("");

    return `
      <button class="voltar" data-voltar-trilhas>← Voltar para os temas</button>
      <article class="topico-detalhe">
        <header>
          <div class="badges">
            <span class="badge vermelho">${escapar(t.materiaNome)}</span>
            <span class="badge ${classeDificuldade(t.dificuldade)}">${escapar(rotuloDificuldade(t.dificuldade))}</span>
            <span class="badge">Fase ${t.fase}</span>
            <span class="badge">${t.questoes} questões</span>
          </div>
          <h2>${escapar(t.titulo)}</h2>
          <p class="resumo">${escapar(t.resumo)}</p>
        </header>

        <div class="bloco-titulo">
          <h3>Aulas</h3>
          <span>${(detalhe.aulas || []).length} aula(s)</span>
        </div>
        ${aulas || '<p class="filtro-vazio">Aulas em preparação.</p>'}

        ${
          flashcards
            ? `<div class="bloco-titulo"><h3>Memorização rápida</h3><span>${detalhe.flashcards.length} cartão(ões)</span></div>
               <div class="flashcards">${flashcards}</div>`
            : ""
        }

        <div class="aviso-fase">
          <span aria-hidden="true">✦</span>
          <span><strong>Próximo passo:</strong> leia a aula, feche o resumo e treine as questões desta matéria.
          A repetição espaçada dos flashcards chega na Fase 3 do projeto.</span>
        </div>
      </article>
    `;
  }

  async function abrirTopico(slug) {
    estado.materiaAtiva = "";
    estado.aulasAbertas.clear();
    mostrarView("trilhas");
    const alvo = $("#conteudo-trilhas");
    if (alvo) alvo.innerHTML = '<div class="carregando">Carregando tema…</div>';
    try {
      const detalhe = await api(`/api/topicos/${encodeURIComponent(slug)}`);
      estado.detalheTopico = detalhe;
      renderConteudoTrilhas();
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (erro) {
      if (alvo) alvo.innerHTML = erroHtml(erro.message);
    }
  }

  async function alternarAula(aulaId) {
    const detalhe = estado.detalheTopico;
    if (!detalhe) return;
    const aula = detalhe.aulas.find((item) => item.id === aulaId);
    if (!aula) return;
    const concluir = !aula.concluida;

    try {
      const resultado = await api(`/api/aulas/${aulaId}/concluir`, {
        method: concluir ? "POST" : "DELETE"
      });
      aula.concluida = concluir;
      detalhe.topico.aulasConcluidas = resultado.aulasConcluidasTopico;
      const resumo = estado.topicos.find((t) => t.slug === detalhe.topico.slug);
      if (resumo) resumo.aulasConcluidas = resultado.aulasConcluidasTopico;
      renderConteudoTrilhas();
      await recarregarPainel();
      if (estado.calendario) renderCalendario();
      toast(concluir ? "Aula concluída. Progresso salvo." : "Aula desmarcada.");
    } catch (erro) {
      toast(erro.message);
    }
  }

  async function registrarMinutos(minutos) {
    try {
      await api("/api/sessoes", {
        method: "POST",
        body: JSON.stringify({ minutos, tipo: "estudo" })
      });
      await recarregarPainel();
      toast(`${minutos} min registrados hoje.`);
    } catch (erro) {
      toast(erro.message);
    }
  }

  // ------------------------------------------------------------- questões

  async function carregarQuestoes() {
    estado.questoes = await api("/api/questoes?limite=200");
    renderQuestoes();
  }

  function renderQuestoes() {
    const bancas = [...new Set(estado.questoes.map((q) => q.banca))].sort();
    const opcoesMateria = estado.materias
      .map(
        (m) =>
          `<option value="${escapar(m.slug)}" ${estado.filtros.materia === m.slug ? "selected" : ""}>${escapar(m.nome)}</option>`
      )
      .join("");

    $("#view-questoes").innerHTML = `
      <div class="questoes-topo">
        <div>
          <h2>Banco de questões</h2>
          <p>${estado.questoes.length} questões comentadas de ENEM e outras bancas, no estilo das provas.
          Nesta fase o modo é leitura: responda, veja o gabarito e o comentário.</p>
        </div>
        <span class="placar" id="placar">0 acertos / 0 respondidas</span>
      </div>

      <div class="filtros" id="filtros-questoes">
        <label class="campo-filtro">Matéria
          <select data-filtro="materia">
            <option value="">Todas</option>
            ${opcoesMateria}
          </select>
        </label>
        <label class="campo-filtro">Dificuldade
          <select data-filtro="dificuldade">
            <option value="">Todas</option>
            <option value="facil" ${estado.filtros.dificuldade === "facil" ? "selected" : ""}>Fácil</option>
            <option value="medio" ${estado.filtros.dificuldade === "medio" ? "selected" : ""}>Médio</option>
            <option value="dificil" ${estado.filtros.dificuldade === "dificil" ? "selected" : ""}>Difícil</option>
          </select>
        </label>
        <label class="campo-filtro">Banca
          <select data-filtro="banca">
            <option value="">Todas</option>
            ${bancas
              .map(
                (b) => `<option value="${escapar(b)}" ${estado.filtros.banca === b ? "selected" : ""}>${escapar(b)}</option>`
              )
              .join("")}
          </select>
        </label>
      </div>

      <div class="lista-questoes" id="lista-questoes"></div>
      <div class="aviso-fase">
        <span aria-hidden="true">✦</span>
        <span><strong>Fase 4 do projeto:</strong> gravação de tentativas, simulados cronometrados e estatísticas por tópico.
        Aqui você já treina com o gabarito comentado.</span>
      </div>
    `;
    renderListaQuestoes();
  }

  function renderListaQuestoes() {
    const alvo = $("#lista-questoes");
    if (!alvo) return;

    const materiaSelecionada = estado.filtros.materia
      ? (estado.materias.find((m) => m.slug === estado.filtros.materia) || {}).nome
      : null;

    const filtradas = estado.questoes.filter((q) => {
      if (materiaSelecionada && q.materia !== materiaSelecionada) return false;
      if (estado.filtros.dificuldade && q.dificuldade !== estado.filtros.dificuldade) return false;
      if (estado.filtros.banca && q.banca !== estado.filtros.banca) return false;
      return true;
    });

    alvo.innerHTML = filtradas.length
      ? filtradas.map(questaoHtml).join("")
      : '<p class="filtro-vazio">Nenhuma questão com esses filtros.</p>';

    atualizarPlacar();
  }

  function questaoHtml(q) {
    const resposta = estado.respostas.get(q.id);
    const respondida = resposta !== undefined;
    const alternativas = (q.alternativas || [])
      .map((a) => {
        let classe = "";
        if (respondida) {
          if (a.letra === q.gabarito) classe = "correta";
          else if (a.letra === resposta) classe = "errada";
        }
        return `
          <button class="alternativa ${classe}" data-questao="${q.id}" data-letra="${a.letra}" ${respondida ? "disabled" : ""}>
            <span class="letra" aria-hidden="true">${a.letra}</span>
            <span>${escapar(a.texto)}</span>
          </button>`;
      })
      .join("");

    return `
      <article class="questao" id="q-${q.id}">
        <div class="questao-cabecalho">
          <span class="badge vermelho">${escapar(q.materia)}</span>
          <span class="badge">${escapar(q.banca)}</span>
          <span class="badge ${classeDificuldade(q.dificuldade)}">${escapar(rotuloDificuldade(q.dificuldade))}</span>
          ${q.autoral ? '<span class="badge">autoral</span>' : ""}
        </div>
        <p class="enunciado">${escapar(q.enunciado)}</p>
        <div class="alternativas">${alternativas}</div>
      </article>
    `;
  }

  function responder(id, letra) {
    const questao = estado.questoes.find((q) => q.id === id);
    if (!questao || estado.respostas.has(id)) return;

    estado.respostas.set(id, letra);
    estado.respondidas += 1;
    if (letra === questao.gabarito) estado.acertos += 1;

    const cartao = document.getElementById(`q-${id}`);
    if (!cartao) return;

    $$(".alternativa", cartao).forEach((botao) => {
      botao.disabled = true;
      if (botao.dataset.letra === questao.gabarito) botao.classList.add("correta");
      else if (botao.dataset.letra === letra) botao.classList.add("errada");
    });

    cartao.insertAdjacentHTML(
      "beforeend",
      `<div class="comentario"><strong>Gabarito: ${questao.gabarito}.</strong> ${escapar(questao.comentario)}</div>`
    );
    atualizarPlacar();
  }

  function atualizarPlacar() {
    const placar = $("#placar");
    if (placar) {
      placar.textContent = `${estado.acertos} acerto(s) / ${estado.respondidas} respondida(s)`;
    }
  }

  // -------------------------------------------------------------- redação

  async function carregarRedacao() {
    const [repertorios, temas, competencias] = await Promise.all([
      api("/api/redacao/repertorios"),
      api("/api/redacao/temas"),
      api("/api/redacao/competencias")
    ]);
    estado.repertorios = repertorios;
    estado.temas = temas;
    estado.competencias = competencias;
    renderRedacao();
  }

  function renderRedacao() {
    const conteudo = {
      competencias: competenciasHtml,
      repertorios: repertoriosHtml,
      temas: temasHtml
    }[estado.abaRedacao] || competenciasHtml;

    $("#view-redacao").innerHTML = `
      <div class="redacao-topo">
        <h2>Laboratório de redação</h2>
        <p>As 5 competências, repertórios de História e Geografia e temas para treinar.
        A meta é chegar à prova com estrutura decorada e repertório aplicável a qualquer tema.</p>
      </div>

      <div class="redacao-abas" role="tablist" aria-label="Seções de redação">
        <button class="redacao-aba ${estado.abaRedacao === "competencias" ? "ativa" : ""}" data-redacao-aba="competencias">Competências</button>
        <button class="redacao-aba ${estado.abaRedacao === "repertorios" ? "ativa" : ""}" data-redacao-aba="repertorios">Repertórios</button>
        <button class="redacao-aba ${estado.abaRedacao === "temas" ? "ativa" : ""}" data-redacao-aba="temas">Temas</button>
      </div>

      <div id="conteudo-redacao">${conteudo()}</div>

      <div class="aviso-fase">
        <span aria-hidden="true">✦</span>
        <span><strong>Fase 6 do projeto:</strong> editor com contador de linhas, checklist de autoavaliação e salvamento de rascunhos.
        Use os temas abaixo para treinar em papel ou no editor de sua preferência.</span>
      </div>
    `;
  }

  function competenciasHtml() {
    return `
      <div class="competencias-grid">
        ${estado.competencias
          .map(
            (c) => `
          <article class="competencia">
            <span class="competencia-numero" aria-hidden="true">${c.numero}</span>
            <div>
              <h3>${escapar(c.titulo)}</h3>
              <p>${escapar(c.descricao)}</p>
              <p class="criterios"><strong style="color:var(--branco)">Na prática:</strong> ${escapar(c.criterios)}</p>
            </div>
          </article>`
          )
          .join("")}
      </div>
    `;
  }

  function repertoriosHtml() {
    return `
      <div class="repertorios-grid">
        ${estado.repertorios
          .map(
            (r) => `
          <article class="repertorio">
            <div class="badges">
              <span class="badge vermelho">${escapar(r.area)}</span>
              ${r.tags ? `<span class="badge">${escapar(r.tags)}</span>` : ""}
            </div>
            <h3>${escapar(r.titulo)}</h3>
            <span class="autor">${escapar(r.autor || "Autoral")}</span>
            <p class="citacao">${escapar(r.citacao)}</p>
            <p class="aplicacao"><strong>Aplicação:</strong> ${escapar(r.aplicacao)}</p>
          </article>`
          )
          .join("")}
      </div>
    `;
  }

  function temasHtml() {
    return `
      <div class="temas-grid">
        ${estado.temas
          .map(
            (t) => `
          <article class="tema-card">
            <div class="badges">
              <span class="badge vermelho">Tema de treino</span>
              ${t.ano ? `<span class="badge">${t.ano}</span>` : ""}
            </div>
            <h3>${escapar(t.titulo)}</h3>
            <p>${escapar(t.proposta)}</p>
            <div class="motivadores"><strong style="color:var(--branco)">Repertório sugerido:</strong> ${escapar(t.textosMotivadores)}</div>
          </article>`
          )
          .join("")}
      </div>
    `;
  }

  // ------------------------------------------------------------ calendário

  async function carregarCalendario(mes) {
    const alvo = $("#view-calendario");
    if (alvo) alvo.innerHTML = '<div class="carregando">Carregando calendário…</div>';
    try {
      const [calendario, cronograma] = await Promise.all([
        api(`/api/calendario?mes=${encodeURIComponent(mes)}`),
        estado.cronograma ? Promise.resolve(estado.cronograma) : api("/api/cronograma")
      ]);
      estado.calendario = calendario;
      estado.cronograma = cronograma;
      estado.mesCalendario = calendario.mes;
      renderCalendario();
    } catch (erro) {
      if (alvo) alvo.innerHTML = erroHtml(erro.message);
    }
  }

  function renderCalendario() {
    const cal = estado.calendario;
    if (!cal) return;
    const minimo = estado.dashboard.hoje.slice(0, 7);
    const maximo = estado.dashboard.dataAlvo.slice(0, 7);
    const vazios = Array.from({ length: cal.vazios },
      () => '<div class="dia vazio" aria-hidden="true"></div>').join("");
    const dias = cal.dias.map((dia) => `
      <div class="dia fase-${dia.fase} ${dia.hoje ? "hoje" : ""} ${dia.alvo ? "alvo" : ""}"
           title="${escapar(rotuloDia(dia))}">
        <span class="dia-numero">${dia.dia}</span>
        ${dia.marcos.map((marco) => `<span class="dia-marco">${escapar(marco.titulo)}</span>`).join("")}
      </div>`).join("");

    const marcos = cal.marcos.length
      ? cal.marcos.map((marco) => `
        <article class="marco-card">
          <span class="marco-data">${escapar(formatarDiaCurto(marco.data))}</span>
          <div>
            <h3>${escapar(marco.titulo)}</h3>
            <p>${escapar(marco.descricao)}</p>
          </div>
          <span class="badge">${escapar(rotuloTipo(marco.tipo))}</span>
        </article>`).join("")
      : '<p class="filtro-vazio">Nenhum marco neste mês. Veja agosto a novembro de 2027 para simulado e reta final.</p>';

    const semana = estado.cronograma
      ? `<div class="semana">${estado.cronograma.dias.map(diaPlanoHtml).join("")}</div>
         <p class="observacao-plano">${escapar(estado.cronograma.observacao)}</p>`
      : '<p class="filtro-vazio">Cronograma indisponível no momento.</p>';

    $("#view-calendario").innerHTML = `
      <div class="calendario-topo">
        <div>
          <h2>Calendário até o ENEM</h2>
          <p>Navegue pelos meses, acompanhe os marcos e veja o plano dos próximos 7 dias.</p>
        </div>
        <div class="mes-nav">
          <button class="icone-botao" data-mes="anterior" aria-label="Mês anterior" ${cal.mes <= minimo ? "disabled" : ""}>←</button>
          <strong>${escapar(cal.titulo)}</strong>
          <button class="icone-botao" data-mes="proximo" aria-label="Próximo mês" ${cal.mes >= maximo ? "disabled" : ""}>→</button>
          <button class="botao-fantasma" data-mes="hoje">Hoje</button>
        </div>
      </div>

      <div class="calendario">
        <div class="calendario-semana" aria-hidden="true">
          <span>D</span><span>S</span><span>T</span><span>Q</span><span>Q</span><span>S</span><span>S</span>
        </div>
        <div class="calendario-grade">${vazios}${dias}</div>
      </div>

      <div class="legenda-fases">
        <span class="legenda-item base">Fase 1 · Base</span>
        <span class="legenda-item aprofundamento">Fase 2 · Aprofundamento</span>
        <span class="legenda-item reta">Fase 3 · Reta final</span>
        <span class="legenda-item alvo">Dia do ENEM</span>
      </div>

      <section class="secao">
        <div class="secao-topo">
          <h2>Marcos do mês</h2>
          <p>${cal.marcos.length} marco(s)</p>
        </div>
        <div class="marcos-lista">${marcos}</div>
      </section>

      <section class="secao">
        <div class="secao-topo">
          <h2>Seu plano para os próximos 7 dias</h2>
          <p>${estado.cronograma && estado.cronograma.bloqueioNovo ? "Conteúdo novo bloqueado" : "Uma aula por dia + questões"}</p>
        </div>
        ${semana}
      </section>
    `;
  }

  function diaPlanoHtml(dia) {
    const classeTipo = dia.tipo === "estudo" ? "vermelho" : dia.tipo === "revisao" ? "verde" : "";
    return `
      <article class="dia-plano ${dia.hoje ? "hoje" : ""}">
        <div class="dia-plano-topo">
          <span>${escapar(dia.diaSemana)}</span>
          <strong>${escapar(formatarDiaCurto(dia.data))}</strong>
        </div>
        <div class="badges">
          <span class="badge ${classeTipo}">${escapar(rotuloTipo(dia.tipo))}</span>
          <span class="badge">${dia.minutos} min</span>
        </div>
        <h3>${escapar(dia.titulo)}</h3>
        <p class="foco">${escapar(dia.foco)}</p>
        <ul class="tarefas">${tarefasHtml(dia.tarefas)}</ul>
      </article>`;
  }

  function tarefasHtml(tarefas) {
    return (tarefas || []).map((tarefa) => {
      if (tarefa.tipo === "aula") {
        return `<li><button class="tarefa" data-topico="${escapar(tarefa.topicoSlug)}">
          <span class="tarefa-tipo">Aula</span>
          <span>${escapar(tarefa.titulo)}<small>${escapar(tarefa.materia)}${tarefa.duracaoMin ? ` · ${tarefa.duracaoMin} min` : ""}</small></span>
        </button></li>`;
      }
      if (tarefa.tipo === "questoes" || tarefa.tipo === "simulado") {
        return `<li><button class="tarefa" data-treinar-materia="${escapar(tarefa.materiaSlug || "")}">
          <span class="tarefa-tipo">${tarefa.tipo === "simulado" ? "Simulado" : "Questões"}</span>
          <span>${escapar(tarefa.titulo)}<small>${tarefa.questoes || 0} questões</small></span>
        </button></li>`;
      }
      return `<li><span class="tarefa">
        <span class="tarefa-tipo">Revisão</span>
        <span>${escapar(tarefa.titulo)}${tarefa.topico ? `<small>${escapar(tarefa.topico)}</small>` : ""}</span>
      </span></li>`;
    }).join("");
  }

  function rotuloDia(dia) {
    const rotulo = formatarData(dia.data);
    if (!dia.marcos.length) return rotulo;
    return rotulo + " · " + dia.marcos.map((marco) => marco.titulo).join(" · ");
  }

  function rotuloTipo(tipo) {
    const rotulos = {
      estudo: "Estudo",
      revisao: "Revisão",
      simulado: "Simulado",
      prova: "Prova",
      conteudo: "Conteúdo"
    };
    return rotulos[tipo] || tipo;
  }

  function novoMes(ano, mes, delta) {
    const data = new Date(ano, mes - 1 + delta, 1);
    return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, "0")}`;
  }

  function formatarDiaCurto(iso) {
    const [ano, mes, dia] = iso.split("-").map(Number);
    return new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short" })
      .format(new Date(ano, mes - 1, dia));
  }

  // ---------------------------------------------------------- configuração

  function abrirConfig() {
    const config = estado.config;
    if (!config) return;
    $("#config-data").value = config.dataAlvo;
    $("#config-minutos").value = config.metaDiariaMinutos;
    $("#config-questoes").value = config.metaQuestoesDia;
    $("#config-erro").classList.add("oculto");
    $("#modal-config").classList.remove("oculto");
    $("#config-data").focus();
  }

  function fecharConfig() {
    $("#modal-config").classList.add("oculto");
  }

  async function salvarConfig(evento) {
    evento.preventDefault();
    const erro = $("#config-erro");
    erro.classList.add("oculto");

    const corpo = {
      dataAlvo: $("#config-data").value,
      metaDiariaMinutos: Number($("#config-minutos").value),
      metaQuestoesDia: Number($("#config-questoes").value)
    };

    try {
      const atualizada = await api("/api/configuracoes", {
        method: "PUT",
        body: JSON.stringify(corpo)
      });
      estado.config = atualizada;
      fecharConfig();
      toast("Configurações salvas.");
      await recarregarPainel();
    } catch (falha) {
      erro.textContent = falha.message;
      erro.classList.remove("oculto");
    }
  }

  async function recarregarPainel() {
    const [dashboard, materias] = await Promise.all([
      api("/api/dashboard"),
      api("/api/materias")
    ]);
    estado.dashboard = dashboard;
    estado.materias = materias;
    try {
      estado.cronograma = await api("/api/cronograma");
    } catch (_) {
      /* o painel continua sem o plano */
    }
    aplicarUrgencia(dashboard.urgencia);
    renderPainel();
  }

  // ------------------------------------------------------------ utilidades

  let timerToast = null;

  function toast(mensagem) {
    const elemento = $("#toast");
    elemento.textContent = mensagem;
    elemento.classList.remove("oculto");
    clearTimeout(timerToast);
    timerToast = setTimeout(() => elemento.classList.add("oculto"), 3200);
  }

  function erroHtml(mensagem) {
    return `
      <div class="filtro-vazio">
        <p>Não foi possível carregar o conteúdo.</p>
        <p style="margin-top:.4rem">${escapar(mensagem)}</p>
        <button class="botao" data-recarregar style="margin-top:1rem">Recarregar</button>
      </div>
    `;
  }

  // ----------------------------------------------------------------- boot

  async function iniciar() {
    ligarEventos();

    try {
      const [dashboard, materias, topicos] = await Promise.all([
        api("/api/dashboard"),
        api("/api/materias"),
        api("/api/topicos")
      ]);

      estado.dashboard = dashboard;
      estado.materias = materias;
      estado.topicos = topicos;
      estado.mesCalendario = dashboard.hoje.slice(0, 7);
      estado.config = {
        dataAlvo: dashboard.dataAlvo,
        metaDiariaMinutos: dashboard.meta.minutosMeta,
        metaQuestoesDia: dashboard.meta.questoesMeta
      };

      try {
        estado.cronograma = await api("/api/cronograma");
      } catch (_) {
        estado.cronograma = null;
      }

      aplicarUrgencia(dashboard.urgencia);
      renderPainel();
      renderTrilhas();
    } catch (erro) {
      $("#view-painel").innerHTML = erroHtml(erro.message);
      return;
    }

    try {
      await carregarQuestoes();
    } catch (erro) {
      $("#view-questoes").innerHTML = erroHtml(erro.message);
    }

    try {
      await carregarRedacao();
    } catch (erro) {
      $("#view-redacao").innerHTML = erroHtml(erro.message);
    }
  }

  document.addEventListener("DOMContentLoaded", iniciar);
})();

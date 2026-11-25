(() => {
  "use strict";

  if (window.ENEM_ESTATICO !== true) return;

  const CHAVE = "enem2027:estado:v1";
  const MESES = [
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
  ];

  let base = null;
  let estadoLocal = null;
  let carregando = null;

  function iso(data) {
    return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, "0")}-${String(data.getDate()).padStart(2, "0")}`;
  }

  function parseISO(texto) {
    const [ano, mes, dia] = String(texto).split("-").map(Number);
    return new Date(ano, mes - 1, dia);
  }

  function hojeLocal() {
    const agora = new Date();
    return new Date(agora.getFullYear(), agora.getMonth(), agora.getDate());
  }

  function addDias(data, quantidade) {
    return new Date(data.getFullYear(), data.getMonth(), data.getDate() + quantidade);
  }

  function primeiroDiaMenosMeses(data, meses) {
    return new Date(data.getFullYear(), data.getMonth() - meses, 1);
  }

  function inicioAprofundamento(alvo) {
    return primeiroDiaMenosMeses(alvo, 3);
  }

  function inicioRetaFinal(alvo) {
    return primeiroDiaMenosMeses(alvo, 1);
  }

  function numeroFase(data, alvo) {
    const dia = new Date(data.getFullYear(), data.getMonth(), data.getDate());
    if (dia >= inicioRetaFinal(alvo)) return 3;
    if (dia >= inicioAprofundamento(alvo)) return 2;
    return 1;
  }

  function faseInfo(hoje, alvo) {
    const numero = numeroFase(hoje, alvo);
    if (numero === 3) {
      return {
        numero,
        nome: "Reta final",
        descricao: "Revisão rápida: apenas temas já estudados, flashcards e erros anteriores.",
        ate: iso(alvo)
      };
    }
    if (numero === 2) {
      return {
        numero,
        nome: "Aprofundamento",
        descricao: "Questões médias e difíceis, simulados cronometrados e redação intensiva.",
        ate: iso(addDias(inicioRetaFinal(alvo), -1))
      };
    }
    return {
      numero,
      nome: "Base e construção",
      descricao: "Teoria completa de História e Geografia, repertório de redação e fundamentos de Matemática.",
      ate: iso(addDias(inicioAprofundamento(alvo), -1))
    };
  }

  function diasRestantes(hoje, alvo) {
    return Math.max(0, Math.round((alvo.getTime() - hoje.getTime()) / 86400000));
  }

  function urgencia(dias) {
    return Math.max(0, Math.min(100, Math.round(100 * (1 - dias / 730))));
  }

  function percentual(parte, total) {
    if (!total) return 0;
    return Math.max(0, Math.min(100, Math.round((parte / total) * 100)));
  }

  function diaSemana(data) {
    return new Intl.DateTimeFormat("pt-BR", { weekday: "long" }).format(data);
  }

  function copia(valor) {
    return JSON.parse(JSON.stringify(valor));
  }

  function lerEstado(dados) {
    const config = dados.config || {};
    const padrao = {
      concluidas: [],
      sessoes: [],
      config: {
        dataAlvo: config.dataAlvo,
        metaDiariaMinutos: Number(config.metaDiariaMinutos || 90),
        metaQuestoesDia: Number(config.metaQuestoesDia || 20)
      }
    };
    let salvo = null;
    try {
      salvo = JSON.parse(localStorage.getItem(CHAVE) || "null");
    } catch (_) {
      salvo = null;
    }
    if (!salvo || typeof salvo !== "object") return padrao;
    return {
      concluidas: Array.isArray(salvo.concluidas)
        ? salvo.concluidas.map(Number).filter(Number.isFinite)
        : [],
      sessoes: Array.isArray(salvo.sessoes) ? salvo.sessoes : [],
      config: { ...padrao.config, ...(salvo.config || {}) }
    };
  }

  function persistir() {
    try {
      localStorage.setItem(CHAVE, JSON.stringify(estadoLocal));
    } catch (_) {
      /* armazenamento cheio ou bloqueado */
    }
  }

  function carregarBase() {
    if (base) return Promise.resolve(base);
    if (!carregando) {
      carregando = fetch("data/snapshot.json")
        .then((resposta) => {
          if (!resposta.ok) throw new Error("Não foi possível carregar os dados offline.");
          return resposta.json();
        })
        .then((dados) => {
          base = dados;
          estadoLocal = lerEstado(dados);
          return base;
        })
        .catch((erro) => {
          carregando = null;
          throw erro;
        });
    }
    return carregando;
  }

  function configAtual() {
    const padrao = base.config || {};
    const local = estadoLocal.config || {};
    return {
      nome: padrao.nome || "Estudante",
      dataAlvo: local.dataAlvo || padrao.dataAlvo,
      metaDiariaMinutos: Number(local.metaDiariaMinutos || padrao.metaDiariaMinutos || 90),
      metaQuestoesDia: Number(local.metaQuestoesDia || padrao.metaQuestoesDia || 20)
    };
  }

  function aulasPlano() {
    const prioridades = new Map(base.materias.map((materia) => [materia.slug, materia.prioridade]));
    const ordemMateria = new Map(base.materias.map((materia, indice) => [materia.slug, indice]));
    const ordemTopico = new Map(base.topicos.map((topico, indice) => [topico.slug, indice]));
    const concluidas = new Set(estadoLocal.concluidas);

    return base.detalhes.flatMap((detalhe) => {
      const topico = detalhe.topico;
      return (detalhe.aulas || []).map((aula) => ({
        aulaId: aula.id,
        topicoId: topico.id,
        topico: topico.titulo,
        topicoSlug: topico.slug,
        materia: topico.materiaNome,
        materiaSlug: topico.materiaSlug,
        prioridade: prioridades.get(topico.materiaSlug) || 3,
        faseTopico: topico.fase,
        dificuldade: topico.dificuldade || "medio",
        titulo: aula.titulo,
        duracaoMin: aula.duracaoMin,
        ordem: aula.ordem,
        concluida: concluidas.has(aula.id)
      }));
    }).sort((a, b) =>
      a.prioridade - b.prioridade ||
      (ordemMateria.get(a.materiaSlug) || 0) - (ordemMateria.get(b.materiaSlug) || 0) ||
      (a.faseTopico || 1) - (b.faseTopico || 1) ||
      (ordemTopico.get(a.topicoSlug) || 0) - (ordemTopico.get(b.topicoSlug) || 0) ||
      (a.ordem || 0) - (b.ordem || 0)
    );
  }

  function pesoFila(fila) {
    if (fila.prioridade <= 1) return 3;
    return fila.prioridade === 2 ? 2 : 1;
  }

  function pesoDificuldade(dificuldade) {
    if (dificuldade === "dificil") return 0;
    if (dificuldade === "medio" || !dificuldade) return 1;
    return 2;
  }

  function montarFilas(pendentes, fase) {
    const porMateria = new Map();
    for (const aula of pendentes) {
      if (!porMateria.has(aula.materiaSlug)) {
        porMateria.set(aula.materiaSlug, {
          materia: aula.materia,
          slug: aula.materiaSlug,
          prioridade: aula.prioridade,
          lista: [],
          agendados: 0
        });
      }
      porMateria.get(aula.materiaSlug).lista.push(aula);
    }

    const filas = [...porMateria.values()];
    if (fase === 2) {
      filas.forEach((fila) => {
        fila.lista.sort((a, b) => pesoDificuldade(a.dificuldade) - pesoDificuldade(b.dificuldade));
      });
    }
    return filas;
  }

  function proxima(filas) {
    let escolhida = null;
    for (const fila of filas) {
      if (!fila.lista.length) continue;
      if (!escolhida) {
        escolhida = fila;
        continue;
      }
      const necessidade = pesoFila(fila) / (fila.agendados + 1);
      const atual = pesoFila(escolhida) / (escolhida.agendados + 1);
      if (necessidade > atual + 1e-9 ||
          (Math.abs(necessidade - atual) <= 1e-9 && fila.prioridade < escolhida.prioridade)) {
        escolhida = fila;
      }
    }
    if (!escolhida) return null;
    escolhida.agendados += 1;
    return escolhida.lista.shift();
  }

  function tarefa(tipo, aulaId, titulo, topico, topicoSlug, materia, materiaSlug, duracaoMin, questoes) {
    return { tipo, aulaId, titulo, topico, topicoSlug, materia, materiaSlug, duracaoMin, questoes };
  }

  function tarefaQuestoes(titulo, materia, materiaSlug, quantidade) {
    return tarefa("questoes", null, titulo, null, null, materia, materiaSlug, null, quantidade);
  }

  function tarefaSimulado(quantidade) {
    return tarefa("simulado", null, "Simulado cronometrado", null, null, "Geral", null, null, quantidade);
  }

  function minutosDaTarefa(item) {
    if (item.duracaoMin != null) return item.duracaoMin;
    if (item.questoes != null) return Math.max(10, item.questoes * 2);
    return 15;
  }

  function observacaoFase(fase) {
    if (fase === 3) {
      return "Reta final: conteúdo novo bloqueado. O plano prioriza revisão de temas já estudados, flashcards e erros anteriores.";
    }
    if (fase === 2) {
      return "Aprofundamento: temas médios e difíceis, simulado cronometrado no sábado e revisão no domingo.";
    }
    return "Base: prioridade para História, Geografia e Redação, intercalando Matemática, Linguagens e Ciências da Natureza.";
  }

  function gerarCronograma() {
    const hoje = hojeLocal();
    const config = configAtual();
    const alvo = parseISO(config.dataAlvo);
    const fase = numeroFase(hoje, alvo);
    const bloqueio = fase >= 3;
    const aulas = aulasPlano();
    const pendentes = aulas.filter((aula) => !aula.concluida);
    const filas = montarFilas(pendentes, fase);
    const questoesPorDia = Math.max(5, Math.min(20, Math.floor(config.metaQuestoesDia / 2)));
    const vistos = new Map();
    for (const aula of aulas) {
      if (aula.concluida && !vistos.has(aula.topicoId)) vistos.set(aula.topicoId, aula);
    }
    const revisar = [...vistos.values()];
    const dias = [];

    for (let i = 0; i < 7; i += 1) {
      const data = addDias(hoje, i);
      const faseDia = numeroFase(data, alvo);
      const bloqueioDia = faseDia >= 3;
      const tarefas = [];
      let tipo;
      let titulo;
      let foco = "Geral";
      let aula = null;

      if (bloqueioDia) {
        tipo = "revisao";
        if (!revisar.length) {
          titulo = "Revisão dos seus resumos";
          tarefas.push(tarefa("revisao", null, "Revisar resumos e mapas mentais já estudados",
            null, null, "Geral", null, null, null));
        } else {
          const tema = revisar[i % revisar.length];
          titulo = tema.topico;
          foco = tema.materia;
          tarefas.push(tarefa("revisao", null, "Revisar o resumo de " + tema.topico,
            tema.topico, tema.topicoSlug, tema.materia, tema.materiaSlug, null, null));
          tarefas.push(tarefa("revisao", null, "Flashcards de " + tema.materia,
            tema.topico, tema.topicoSlug, tema.materia, tema.materiaSlug, null, null));
        }
        tarefas.push(tarefaQuestoes("Refazer questões erradas", "Geral", null, config.metaQuestoesDia));
      } else if (data.getDay() === 0) {
        tipo = "revisao";
        titulo = "Fechar a semana";
        foco = "Revisão";
        tarefas.push(tarefa("revisao", null, "Revisar flashcards dos temas da semana",
          null, null, "Geral", null, null, null));
        if (vistos.size) {
          tarefas.push(tarefa("revisao", null, "Reler os resumos dos temas estudados",
            null, null, "Geral", null, null, null));
        }
        tarefas.push(tarefaQuestoes("Refazer questões erradas", "Geral", null, config.metaQuestoesDia));
      } else {
        aula = proxima(filas);
        if (!aula) {
          tipo = "revisao";
          titulo = "Revisão e questões";
          foco = "Revisão";
          tarefas.push(tarefa("revisao", null, "Revisar os temas já estudados",
            null, null, "Geral", null, null, null));
          tarefas.push(tarefaQuestoes("Treinar questões", "Geral", null, config.metaQuestoesDia));
        } else {
          tipo = "estudo";
          titulo = aula.topico;
          foco = aula.materia;
          tarefas.push(tarefa("aula", aula.aulaId, aula.titulo, aula.topico, aula.topicoSlug,
            aula.materia, aula.materiaSlug, aula.duracaoMin, null));
          tarefas.push(tarefaQuestoes("Questões de " + aula.materia, aula.materia,
            aula.materiaSlug, questoesPorDia));
        }
      }

      if (faseDia === 2 && data.getDay() === 6) {
        tipo = "simulado";
        if (!aula) {
          titulo = "Simulado e revisão";
          foco = "Simulado";
        }
        tarefas.push(tarefaSimulado(20));
      }

      const minutos = Math.max(20, Math.min(240,
        tarefas.reduce((soma, item) => soma + minutosDaTarefa(item), 0)));

      dias.push({
        data: iso(data),
        diaSemana: diaSemana(data),
        hoje: iso(data) === iso(hoje),
        tipo,
        titulo,
        foco,
        minutos,
        tarefas
      });
    }

    return {
      inicio: iso(hoje),
      fim: iso(addDias(hoje, 6)),
      fase,
      bloqueioNovo: bloqueio,
      observacao: observacaoFase(fase),
      dias
    };
  }

  function aulasPorMateria(aulas, slug) {
    return aulas.filter((aula) => aula.materiaSlug === slug);
  }

  function materiasAtualizadas(aulas) {
    return base.materias.map((materia) => {
      const daMateria = aulasPorMateria(aulas, materia.slug);
      return {
        ...materia,
        percentual: percentual(daMateria.filter((aula) => aula.concluida).length, materia.aulas)
      };
    });
  }

  function topicosAtualizados(aulas) {
    return base.topicos.map((topico) => ({
      ...topico,
      aulasConcluidas: aulas.filter((aula) => aula.topicoSlug === topico.slug && aula.concluida).length
    }));
  }

  function dashboard() {
    const hoje = hojeLocal();
    const config = configAtual();
    const alvo = parseISO(config.dataAlvo);
    const aulas = aulasPlano();
    const concluidas = aulas.filter((aula) => aula.concluida).length;
    const minutosHoje = estadoLocal.sessoes
      .filter((sessao) => sessao.data === iso(hoje))
      .reduce((soma, sessao) => soma + Number(sessao.minutos || 0), 0);
    const materias = materiasAtualizadas(aulas);
    const topicos = topicosAtualizados(aulas);
    const prioridades = new Map(base.materias.map((materia) => [materia.slug, materia.prioridade]));
    const proximos = topicos
      .filter((topico) => prioridades.get(topico.materiaSlug) === 1)
      .slice(0, 3);
    const dias = diasRestantes(hoje, alvo);

    return {
      hoje: iso(hoje),
      dataAlvo: config.dataAlvo,
      diasRestantes: dias,
      urgencia: urgencia(dias),
      fase: faseInfo(hoje, alvo),
      meta: {
        minutosHoje,
        minutosMeta: config.metaDiariaMinutos,
        questoesHoje: 0,
        questoesMeta: config.metaQuestoesDia
      },
      contadores: base.dashboard.contadores,
      resumo: { concluidas, total: aulas.length },
      materias,
      proximos
    };
  }

  function calendario(mesPedido) {
    const hoje = hojeLocal();
    const config = configAtual();
    const alvo = parseISO(config.dataAlvo);
    const mes = mesPedido && mesPedido.trim() ? mesPedido.trim() : iso(hoje).slice(0, 7);
    const partes = /^(\d{4})-(\d{2})$/.exec(mes);
    if (!partes || Number(partes[2]) < 1 || Number(partes[2]) > 12) {
      throw new Error("Mês inválido. Use o formato AAAA-MM.");
    }

    const ano = Number(partes[1]);
    const numeroMes = Number(partes[2]);
    const atual = hoje.getFullYear() * 12 + hoje.getMonth();
    const maximo = alvo.getFullYear() * 12 + alvo.getMonth();
    const solicitado = ano * 12 + (numeroMes - 1);

    if (solicitado < atual || solicitado > maximo) {
      throw new Error(`Navegue entre ${iso(hoje).slice(0, 7)} e ${iso(alvo).slice(0, 7)}.`);
    }

    const marcos = (base.marcos || [])
      .filter((marco) => marco.data.startsWith(mes))
      .sort((a, b) => a.data.localeCompare(b.data));
    const porDia = new Map();
    for (const marco of marcos) {
      if (!porDia.has(marco.data)) porDia.set(marco.data, []);
      porDia.get(marco.data).push(marco);
    }

    const primeiro = new Date(ano, numeroMes - 1, 1);
    const totalDias = new Date(ano, numeroMes, 0).getDate();
    const dias = [];
    for (let dia = 1; dia <= totalDias; dia += 1) {
      const data = new Date(ano, numeroMes - 1, dia);
      const texto = iso(data);
      dias.push({
        data: texto,
        dia,
        hoje: texto === iso(hoje),
        alvo: texto === iso(alvo),
        fase: numeroFase(data, alvo),
        marcos: porDia.get(texto) || []
      });
    }

    return {
      mes,
      titulo: `${MESES[numeroMes - 1]} de ${ano}`,
      hoje: iso(hoje),
      dataAlvo: config.dataAlvo,
      faseAtual: numeroFase(hoje, alvo),
      vazios: primeiro.getDay(),
      dias,
      marcos
    };
  }

  function topicoDetalhe(slug) {
    const detalhe = base.detalhes.find((item) => item.topico.slug === slug);
    if (!detalhe) throw new Error("Tópico não encontrado");
    const concluidas = new Set(estadoLocal.concluidas);
    const aulas = (detalhe.aulas || []).map((aula) => ({
      ...aula,
      concluida: concluidas.has(aula.id)
    }));
    return {
      topico: {
        ...detalhe.topico,
        aulasConcluidas: aulas.filter((aula) => aula.concluida).length
      },
      aulas,
      flashcards: detalhe.flashcards || []
    };
  }

  function filtrarQuestoes(params) {
    const materiaSlug = params.get("materia");
    const materiaNome = materiaSlug
      ? (base.materias.find((materia) => materia.slug === materiaSlug) || {}).nome
      : null;
    const dificuldade = params.get("dificuldade");
    const banca = params.get("banca");
    const limitePedido = Number.parseInt(params.get("limite") || "50", 10);
    const limite = Math.max(1, Math.min(200, Number.isFinite(limitePedido) ? limitePedido : 50));

    return base.questoes.filter((questao) => {
      if (materiaNome && questao.materia !== materiaNome) return false;
      if (dificuldade && questao.dificuldade !== dificuldade) return false;
      if (banca && questao.banca !== banca) return false;
      return true;
    }).slice(0, limite).map(copia);
  }

  function filtrarFlashcards(params) {
    const materiaSlug = params.get("materia");
    const materiaNome = materiaSlug
      ? (base.materias.find((materia) => materia.slug === materiaSlug) || {}).nome
      : null;
    return (base.flashcards || [])
      .filter((cartao) => !materiaNome || cartao.materia === materiaNome)
      .map(copia);
  }

  function concluirAula(aulaId, concluida) {
    const detalhe = base.detalhes.find((item) => item.aulas.some((aula) => aula.id === aulaId));
    if (!detalhe) throw new Error("Aula não encontrada");

    const marcadas = new Set(estadoLocal.concluidas);
    if (concluida) marcadas.add(aulaId);
    else marcadas.delete(aulaId);
    estadoLocal.concluidas = [...marcadas];
    persistir();

    return {
      aulaId,
      concluida,
      aulasConcluidasTopico: detalhe.aulas.filter((aula) => marcadas.has(aula.id)).length
    };
  }

  function corpoJson(opcoes) {
    try {
      return opcoes.body ? JSON.parse(opcoes.body) : {};
    } catch (_) {
      throw new Error("Requisição inválida.");
    }
  }

  function registrarSessao(opcoes) {
    const entrada = corpoJson(opcoes);
    const minutos = Number(entrada.minutos);
    if (!Number.isFinite(minutos) || minutos < 5 || minutos > 600) {
      throw new Error("Registre entre 5 e 600 minutos por sessão.");
    }
    const data = iso(hojeLocal());
    estadoLocal.sessoes.push({
      data,
      minutos,
      tipo: String(entrada.tipo || "estudo").trim() || "estudo",
      descricao: entrada.descricao ? String(entrada.descricao).trim() : null
    });
    persistir();
    const totalHoje = estadoLocal.sessoes
      .filter((sessao) => sessao.data === data)
      .reduce((soma, sessao) => soma + Number(sessao.minutos || 0), 0);
    return { data, minutos, totalHoje };
  }

  function salvarConfiguracoes(opcoes) {
    const entrada = corpoJson(opcoes);
    const dataAlvo = String(entrada.dataAlvo || "").trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dataAlvo)) {
      throw new Error("Informe a data alvo no formato AAAA-MM-DD.");
    }
    const alvo = parseISO(dataAlvo);
    if (iso(alvo) !== dataAlvo) {
      throw new Error("Data inválida. Use o formato AAAA-MM-DD.");
    }
    if (alvo < hojeLocal()) {
      throw new Error("A data alvo precisa estar no futuro.");
    }

    const config = configAtual();
    const minutos = entrada.metaDiariaMinutos == null
      ? config.metaDiariaMinutos
      : Number(entrada.metaDiariaMinutos);
    const questoes = entrada.metaQuestoesDia == null
      ? config.metaQuestoesDia
      : Number(entrada.metaQuestoesDia);

    if (!Number.isInteger(minutos) || minutos < 15 || minutos > 600) {
      throw new Error("A meta diária deve ficar entre 15 e 600 minutos.");
    }
    if (!Number.isInteger(questoes) || questoes < 5 || questoes > 200) {
      throw new Error("A meta de questões deve ficar entre 5 e 200.");
    }

    estadoLocal.config = {
      dataAlvo,
      metaDiariaMinutos: minutos,
      metaQuestoesDia: questoes
    };
    persistir();
    return configAtual();
  }

  async function apiOffline(caminho, opcoes = {}) {
    await carregarBase();

    const metodo = String(opcoes.method || "GET").toUpperCase();
    const partes = String(caminho).split("?");
    const rota = partes[0].replace(/\/+$/, "");
    const params = new URLSearchParams(partes[1] || "");

    if (metodo === "GET" && rota === "/api/saude") {
      return {
        status: "ok",
        versao: "estatico",
        banco: "dados embutidos (offline)",
        hora: new Date().toISOString().slice(0, 19),
        materias: base.materias.length
      };
    }
    if (metodo === "GET" && rota === "/api/dashboard") return dashboard();
    if (metodo === "GET" && rota === "/api/materias") return materiasAtualizadas(aulasPlano());
    if (metodo === "GET" && rota === "/api/topicos") {
      const materia = params.get("materia");
      return topicosAtualizados(aulasPlano())
        .filter((topico) => !materia || topico.materiaSlug === materia);
    }

    const detalhe = rota.match(/^\/api\/topicos\/([^/]+)$/);
    if (metodo === "GET" && detalhe) {
      return topicoDetalhe(decodeURIComponent(detalhe[1]));
    }
    if (metodo === "GET" && rota === "/api/questoes") return filtrarQuestoes(params);
    if (metodo === "GET" && rota === "/api/flashcards") return filtrarFlashcards(params);
    if (metodo === "GET" && rota === "/api/redacao/repertorios") return copia(base.repertorios || []);
    if (metodo === "GET" && rota === "/api/redacao/temas") return copia(base.temas || []);
    if (metodo === "GET" && rota === "/api/redacao/competencias") return copia(base.competencias || []);
    if (metodo === "GET" && rota === "/api/calendario") return calendario(params.get("mes"));
    if (metodo === "GET" && rota === "/api/cronograma") return gerarCronograma();
    if (metodo === "GET" && rota === "/api/configuracoes") return configAtual();
    if (metodo === "PUT" && rota === "/api/configuracoes") return salvarConfiguracoes(opcoes);

    const conclusao = rota.match(/^\/api\/aulas\/(\d+)\/concluir$/);
    if (conclusao && (metodo === "POST" || metodo === "DELETE")) {
      return concluirAula(Number(conclusao[1]), metodo === "POST");
    }
    if (metodo === "POST" && rota === "/api/sessoes") return registrarSessao(opcoes);

    throw new Error("Rota não encontrada");
  }

  window.apiOffline = apiOffline;

  if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("sw.js").catch(() => {
        /* offline ainda funciona com o cache do navegador */
      });
    });
  }
})();

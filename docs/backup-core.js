/* Lógica pura do backup, compartilhada pelo navegador e pelos testes Node. */
(function (raiz, fabrica) {
  const api = fabrica();
  if (typeof module === "object" && module.exports) {
    module.exports = api;
  }
  if (raiz) {
    raiz.BackupCore = api;
  }
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const FORMATO = "enem-2027-timao-backup";
  const VERSAO = 1;

  function dataIsoValida(texto) {
    if (typeof texto !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(texto)) return false;
    const [ano, mes, dia] = texto.split("-").map(Number);
    const data = new Date(ano, mes - 1, dia);
    return data.getFullYear() === ano && data.getMonth() === mes - 1 &&
      data.getDate() === dia && ano >= 2020 && ano <= 2100;
  }

  function totaisEstado(dados) {
    const concluidas = new Set(
      (Array.isArray(dados && dados.concluidas) ? dados.concluidas : [])
        .map(Number)
        .filter(Number.isInteger)
    );
    const sessoes = Array.isArray(dados && dados.sessoes) ? dados.sessoes : [];
    const minutos = sessoes.reduce((soma, sessao) => soma + (Number(sessao && sessao.minutos) || 0), 0);
    const datas = sessoes
      .map((sessao) => sessao && sessao.data)
      .filter(Boolean)
      .sort();
    return {
      aulas: concluidas.size,
      sessoes: sessoes.length,
      minutos,
      ultimaSessao: datas.length ? datas[datas.length - 1] : null
    };
  }

  function chaveSessao(sessao) {
    return [
      sessao.data || "",
      Number(sessao.minutos) || 0,
      sessao.tipo || "estudo",
      sessao.descricao || ""
    ].join("|");
  }

  function mapaSessoes(sessoes) {
    const mapa = new Map();
    for (const sessao of sessoes) {
      const chave = chaveSessao(sessao);
      if (!mapa.has(chave)) mapa.set(chave, { sessao, quantidade: 0 });
      mapa.get(chave).quantidade += 1;
    }
    return mapa;
  }

  function mesclarSessoes(atual, backup) {
    const doAtual = mapaSessoes(atual);
    const doBackup = mapaSessoes(backup);
    const resultado = [];

    for (const [chave, item] of doAtual) {
      const quantidade = Math.max(item.quantidade, (doBackup.get(chave) || {}).quantidade || 0);
      for (let i = 0; i < quantidade; i += 1) resultado.push(item.sessao);
      doBackup.delete(chave);
    }
    for (const item of doBackup.values()) {
      for (let i = 0; i < item.quantidade; i += 1) resultado.push(item.sessao);
    }
    return resultado;
  }

  function configValida(config) {
    return Boolean(config) &&
      typeof config === "object" &&
      !Array.isArray(config) &&
      dataIsoValida(config.dataAlvo) &&
      Number.isInteger(Number(config.metaDiariaMinutos)) &&
      Number(config.metaDiariaMinutos) >= 15 &&
      Number(config.metaDiariaMinutos) <= 600 &&
      Number.isInteger(Number(config.metaQuestoesDia)) &&
      Number(config.metaQuestoesDia) >= 5 &&
      Number(config.metaQuestoesDia) <= 200;
  }

  function normalizarConfig(config) {
    return {
      dataAlvo: config.dataAlvo,
      metaDiariaMinutos: Number(config.metaDiariaMinutos),
      metaQuestoesDia: Number(config.metaQuestoesDia)
    };
  }

  function mesclarEstados(atual, backup) {
    const base = atual && typeof atual === "object" ? atual : {};
    const ids = new Set([
      ...(Array.isArray(base.concluidas) ? base.concluidas : []).map(Number),
      ...(Array.isArray(backup.concluidas) ? backup.concluidas : []).map(Number)
    ]);
    return {
      concluidas: [...ids].filter(Number.isInteger),
      sessoes: mesclarSessoes(
        Array.isArray(base.sessoes) ? base.sessoes : [],
        Array.isArray(backup.sessoes) ? backup.sessoes : []
      ),
      config: configValida(base.config) ? normalizarConfig(base.config) : backup.config
    };
  }

  function validarBackup(backup) {
    if (!backup || typeof backup !== "object" || Array.isArray(backup)) {
      throw new Error("O arquivo não é um backup válido.");
    }
    if (backup.formato !== FORMATO) {
      throw new Error("Este arquivo não foi gerado pelo ENEM 2027 Timão.");
    }
    if (Number(backup.versao) !== VERSAO) {
      throw new Error("Versão de backup não reconhecida.");
    }

    const dados = backup.estado;
    if (!dados || typeof dados !== "object" || Array.isArray(dados)) {
      throw new Error("O backup não contém um estado válido.");
    }
    if (!Array.isArray(dados.concluidas) || dados.concluidas.length > 10000) {
      throw new Error("Lista de aulas concluídas inválida.");
    }
    const concluidas = [];
    for (const valor of dados.concluidas) {
      const id = Number(valor);
      if (!Number.isInteger(id) || id < 1 || id > 1000000) {
        throw new Error("O backup contém uma aula inválida.");
      }
      concluidas.push(id);
    }

    if (!Array.isArray(dados.sessoes) || dados.sessoes.length > 100000) {
      throw new Error("Lista de sessões inválida.");
    }
    const sessoes = dados.sessoes.map((sessao) => {
      if (!sessao || typeof sessao !== "object" || Array.isArray(sessao)) {
        throw new Error("O backup contém uma sessão inválida.");
      }
      if (!dataIsoValida(sessao.data)) {
        throw new Error("O backup contém uma data de sessão inválida.");
      }
      const minutos = Number(sessao.minutos);
      if (!Number.isInteger(minutos) || minutos < 5 || minutos > 600) {
        throw new Error("O backup contém minutos inválidos.");
      }
      const tipo = sessao.tipo == null ? "estudo" : String(sessao.tipo).trim();
      if (tipo.length > 80) {
        throw new Error("O backup contém um tipo de sessão inválido.");
      }
      const descricao = sessao.descricao == null ? null : String(sessao.descricao).trim();
      if (descricao && descricao.length > 500) {
        throw new Error("O backup contém uma descrição muito longa.");
      }
      return { data: sessao.data, minutos, tipo: tipo || "estudo", descricao: descricao || null };
    });

    const config = dados.config;
    if (!config || typeof config !== "object" || Array.isArray(config)) {
      throw new Error("O backup não contém configurações válidas.");
    }
    if (!dataIsoValida(config.dataAlvo)) {
      throw new Error("A data alvo do backup é inválida.");
    }
    const minutosMeta = Number(config.metaDiariaMinutos);
    const questoesMeta = Number(config.metaQuestoesDia);
    if (!Number.isInteger(minutosMeta) || minutosMeta < 15 || minutosMeta > 600) {
      throw new Error("A meta de minutos do backup é inválida.");
    }
    if (!Number.isInteger(questoesMeta) || questoesMeta < 5 || questoesMeta > 200) {
      throw new Error("A meta de questões do backup é inválida.");
    }

    const exportadoEm = typeof backup.exportadoEm === "string" && !Number.isNaN(Date.parse(backup.exportadoEm))
      ? backup.exportadoEm
      : null;

    return {
      estado: {
        concluidas: [...new Set(concluidas)],
        sessoes,
        config: { dataAlvo: config.dataAlvo, metaDiariaMinutos: minutosMeta, metaQuestoesDia: questoesMeta }
      },
      exportadoEm
    };
  }

  return {
    FORMATO,
    VERSAO,
    dataIsoValida,
    totaisEstado,
    mesclarEstados,
    validarBackup
  };
});

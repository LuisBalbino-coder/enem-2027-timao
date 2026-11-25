const test = require("node:test");
const assert = require("node:assert/strict");

const BackupCore = require("../src/main/resources/public/backup-core.js");

function estadoValido(alteracoes = {}) {
  return {
    concluidas: [1],
    sessoes: [
      { data: "2026-11-24", minutos: 30, tipo: "estudo", descricao: null }
    ],
    config: {
      dataAlvo: "2027-11-07",
      metaDiariaMinutos: 90,
      metaQuestoesDia: 20
    },
    ...alteracoes
  };
}

function backupValido(alteracoes = {}) {
  return {
    formato: BackupCore.FORMATO,
    versao: BackupCore.VERSAO,
    exportadoEm: "2026-11-24T18:00:00.000Z",
    estado: estadoValido(),
    ...alteracoes
  };
}

test("rejeita arquivo que não é um backup do aplicativo", () => {
  assert.throws(() => BackupCore.validarBackup(null), /não é um backup válido/);
  assert.throws(() => BackupCore.validarBackup([]), /não é um backup válido/);
  assert.throws(() => BackupCore.validarBackup({}), /não foi gerado pelo ENEM 2027 Timão/);
  assert.throws(
    () => BackupCore.validarBackup(backupValido({ formato: "outro-formato" })),
    /não foi gerado pelo ENEM 2027 Timão/
  );
  assert.throws(
    () => BackupCore.validarBackup(backupValido({ versao: 2 })),
    /Versão de backup não reconhecida/
  );
});

test("rejeita estado, aulas, sessões e configurações inválidas", () => {
  const invalidos = [
    backupValido({ estado: null }),
    backupValido({ estado: estadoValido({ concluidas: ["aula-1"] }) }),
    backupValido({
      estado: estadoValido({
        sessoes: [{ data: "2026-02-30", minutos: 30, tipo: "estudo" }]
      })
    }),
    backupValido({
      estado: estadoValido({
        sessoes: [{ data: "2026-11-24", minutos: 0, tipo: "estudo" }]
      })
    }),
    backupValido({
      estado: estadoValido({
        config: { dataAlvo: "2027-11-07", metaDiariaMinutos: 0, metaQuestoesDia: 20 }
      })
    }),
    backupValido({
      estado: estadoValido({
        config: { dataAlvo: "2027-11-07", metaDiariaMinutos: 90, metaQuestoesDia: 201 }
      })
    })
  ];

  for (const backup of invalidos) {
    assert.throws(() => BackupCore.validarBackup(backup), /válid/);
  }
});

test("normaliza um backup válido e devolve os totais para a prévia", () => {
  const resultado = BackupCore.validarBackup(
    backupValido({
      estado: estadoValido({
        concluidas: [1, 1, 2],
        sessoes: [
          { data: "2026-11-24", minutos: 30, tipo: "estudo", descricao: "  Revisão  " },
          { data: "2026-11-25", minutos: 45, tipo: null, descricao: null }
        ]
      })
    })
  );

  assert.equal(resultado.exportadoEm, "2026-11-24T18:00:00.000Z");
  assert.deepEqual(resultado.estado.concluidas, [1, 2]);
  assert.deepEqual(resultado.estado.sessoes, [
    { data: "2026-11-24", minutos: 30, tipo: "estudo", descricao: "Revisão" },
    { data: "2026-11-25", minutos: 45, tipo: "estudo", descricao: null }
  ]);
  assert.deepEqual(BackupCore.totaisEstado(resultado.estado), {
    aulas: 2,
    sessoes: 2,
    minutos: 75,
    ultimaSessao: "2026-11-25"
  });
});

test("mescla aulas novas sem apagar as já concluídas e sem duplicar ids", () => {
  const atual = estadoValido({ concluidas: [1, 2] });
  const backup = BackupCore.validarBackup(
    backupValido({ estado: estadoValido({ concluidas: [2, 3] }) })
  ).estado;

  const mesclado = BackupCore.mesclarEstados(atual, backup);

  assert.deepEqual(mesclado.concluidas, [1, 2, 3]);
  assert.deepEqual(atual.concluidas, [1, 2], "o estado atual não deve ser alterado");
});

test("não duplica sessões idênticas e preserva sessões diferentes", () => {
  const sessaoAtual = { data: "2026-11-24", minutos: 30, tipo: "estudo", descricao: null };
  const sessaoNova = { data: "2026-11-24", minutos: 45, tipo: "estudo", descricao: "Revisão" };
  const atual = estadoValido({ sessoes: [sessaoAtual] });
  const backup = BackupCore.validarBackup(
    backupValido({ estado: estadoValido({ sessoes: [sessaoAtual, sessaoNova] }) })
  ).estado;

  const mesclado = BackupCore.mesclarEstados(atual, backup);

  assert.equal(mesclado.sessoes.length, 2);
  assert.equal(BackupCore.totaisEstado(mesclado).minutos, 75);

  const reimportado = BackupCore.mesclarEstados(mesclado, backup);
  assert.equal(reimportado.sessoes.length, 2, "reimportar o mesmo backup não pode somar de novo");
  assert.equal(BackupCore.totaisEstado(reimportado).minutos, 75);
});

test("mantém as configurações atuais ao mesclar", () => {
  const atual = estadoValido({
    config: { dataAlvo: "2027-11-07", metaDiariaMinutos: 90, metaQuestoesDia: 20 }
  });
  const backup = BackupCore.validarBackup(
    backupValido({
      estado: estadoValido({
        config: { dataAlvo: "2027-10-01", metaDiariaMinutos: 120, metaQuestoesDia: 30 }
      })
    })
  ).estado;

  const mesclado = BackupCore.mesclarEstados(atual, backup);

  assert.deepEqual(mesclado.config, {
    dataAlvo: "2027-11-07",
    metaDiariaMinutos: 90,
    metaQuestoesDia: 20
  });
});

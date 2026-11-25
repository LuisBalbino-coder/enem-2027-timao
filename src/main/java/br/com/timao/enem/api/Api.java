package br.com.timao.enem.api;

import br.com.timao.enem.dao.ConfiguracaoDao;
import br.com.timao.enem.dao.ConteudoDao;
import br.com.timao.enem.dao.ProgressoDao;
import br.com.timao.enem.db.Db;
import br.com.timao.enem.model.Payloads;
import br.com.timao.enem.service.Calendario;
import br.com.timao.enem.service.PlanoEstudos;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Api {

    private static final Logger LOG = LoggerFactory.getLogger(Api.class);

    private Api() {
    }

    public static void registrar(Javalin app) {
        app.get("/", Api::paginaInicial);
        app.get("/api/saude", Api::saude);
        app.get("/api/dashboard", Api::dashboard);
        app.get("/api/materias", ctx -> ctx.json(ConteudoDao.materias()));
        app.get("/api/topicos", ctx -> ctx.json(ConteudoDao.topicos(ctx.queryParam("materia"))));
        app.get("/api/topicos/{slug}", Api::topico);
        app.get("/api/questoes", Api::questoes);
        app.get("/api/flashcards", ctx -> ctx.json(ConteudoDao.flashcards(ctx.queryParam("materia"))));
        app.get("/api/redacao/repertorios", ctx -> ctx.json(ConteudoDao.repertorios()));
        app.get("/api/redacao/temas", ctx -> ctx.json(ConteudoDao.temasRedacao()));
        app.get("/api/redacao/competencias", ctx -> ctx.json(ConteudoDao.competencias()));
        app.get("/api/calendario", Api::calendario);
        app.get("/api/cronograma", Api::cronograma);
        app.post("/api/aulas/{id}/concluir", ctx -> concluirAula(ctx, true));
        app.delete("/api/aulas/{id}/concluir", ctx -> concluirAula(ctx, false));
        app.post("/api/sessoes", Api::registrarSessao);
        app.get("/api/configuracoes", ctx -> ctx.json(ConfiguracaoDao.ler()));
        app.put("/api/configuracoes", Api::atualizarConfiguracoes);

        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            String mensagem = e.getMessage() == null ? "Requisição inválida" : e.getMessage();
            ctx.status(400).json(Map.of("erro", mensagem));
        });
        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("Erro ao processar {} {}", ctx.method(), ctx.path(), e);
            if (ctx.res().isCommitted()) {
                return;
            }
            String mensagem = e.getMessage() == null ? "Erro interno" : e.getMessage();
            ctx.status(500).json(Map.of("erro", mensagem));
        });
        app.error(404, ctx -> {
            if (ctx.path().startsWith("/api/")) {
                ctx.json(Map.of("erro", "Rota não encontrada"));
            }
        });
    }

    private static void paginaInicial(Context ctx) {
        try (InputStream in = Api.class.getResourceAsStream("/public/index.html")) {
            if (in == null) {
                ctx.status(500).result("index.html não encontrado");
                return;
            }
            ctx.contentType("text/html; charset=utf-8")
                    .result(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler a página inicial", e);
        }
    }

    private static void saude(Context ctx) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("status", "ok");
        corpo.put("versao", "0.1.0");
        corpo.put("banco", "H2 em arquivo (data/enem2027.mv.db)");
        corpo.put("hora", LocalDateTime.now().withNano(0).toString());
        try (Connection conn = Db.conectar();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM materia")) {
            rs.next();
            corpo.put("materias", rs.getInt(1));
        } catch (Exception e) {
            corpo.put("status", "degradado");
            corpo.put("erroBanco", e.getMessage());
        }
        ctx.json(corpo);
    }

    private static void dashboard(Context ctx) {
        Payloads.Configuracao config = ConfiguracaoDao.ler();
        LocalDate hoje = LocalDate.now();
        LocalDate alvo = LocalDate.parse(config.dataAlvo());
        long dias = Calendario.diasRestantes(hoje, alvo);
        Payloads.MetaHoje meta = new Payloads.MetaHoje(
                ProgressoDao.minutosHoje(), config.metaDiariaMinutos(),
                ProgressoDao.questoesHoje(), config.metaQuestoesDia());
        ctx.json(new Payloads.Dashboard(
                hoje.toString(),
                alvo.toString(),
                dias,
                Calendario.urgencia(dias),
                Calendario.fase(hoje, alvo),
                meta,
                ConteudoDao.contadores(),
                ProgressoDao.resumoAulas(),
                ConteudoDao.materias(),
                ConteudoDao.proximos(3)));
    }

    private static void calendario(Context ctx) {
        Payloads.Configuracao config = ConfiguracaoDao.ler();
        LocalDate hoje = LocalDate.now();
        LocalDate alvo = LocalDate.parse(config.dataAlvo());

        YearMonth mes;
        String parametro = ctx.queryParam("mes");
        try {
            mes = parametro == null || parametro.isBlank() ? YearMonth.from(hoje) : YearMonth.parse(parametro);
        } catch (Exception e) {
            throw new IllegalArgumentException("Mês inválido. Use o formato AAAA-MM.");
        }

        YearMonth minimo = YearMonth.from(hoje);
        YearMonth maximo = YearMonth.from(alvo);
        if (mes.isBefore(minimo) || mes.isAfter(maximo)) {
            throw new IllegalArgumentException("Navegue entre " + minimo + " e " + maximo + ".");
        }

        List<Payloads.Marco> marcos = ConteudoDao.marcosDoMes(mes.atDay(1), mes.atEndOfMonth());
        Map<LocalDate, List<Payloads.Marco>> porDia = new HashMap<>();
        for (Payloads.Marco marco : marcos) {
            porDia.computeIfAbsent(LocalDate.parse(marco.data()), chave -> new ArrayList<>()).add(marco);
        }

        int vazios = mes.atDay(1).getDayOfWeek().getValue() % 7;
        List<Payloads.DiaCalendario> dias = new ArrayList<>();
        for (int dia = 1; dia <= mes.lengthOfMonth(); dia++) {
            LocalDate data = mes.atDay(dia);
            dias.add(new Payloads.DiaCalendario(
                    data.toString(), dia, data.equals(hoje), data.equals(alvo),
                    Calendario.numeroFase(data, alvo), porDia.getOrDefault(data, List.of())));
        }

        ctx.json(new Payloads.Calendario(
                mes.toString(), Calendario.tituloMes(mes), hoje.toString(), alvo.toString(),
                Calendario.numeroFase(hoje, alvo), vazios, dias, marcos));
    }

    private static void cronograma(Context ctx) {
        Payloads.Configuracao config = ConfiguracaoDao.ler();
        LocalDate hoje = LocalDate.now();
        LocalDate alvo = LocalDate.parse(config.dataAlvo());
        ctx.json(PlanoEstudos.gerar(hoje, alvo, config.metaQuestoesDia(), ConteudoDao.aulasParaPlano()));
    }

    private static void concluirAula(Context ctx, boolean concluida) {
        int aulaId;
        try {
            aulaId = Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Id de aula inválido.");
        }
        Payloads.Conclusao resultado = ProgressoDao.concluirAula(aulaId, concluida);
        if (resultado == null) {
            ctx.status(404).json(Map.of("erro", "Aula não encontrada"));
            return;
        }
        ctx.json(resultado);
    }

    private static void registrarSessao(Context ctx) {
        Payloads.SessaoInput entrada = ctx.bodyAsClass(Payloads.SessaoInput.class);
        if (entrada == null || entrada.minutos() == null) {
            throw new IllegalArgumentException("Informe os minutos estudados.");
        }
        int minutos = entrada.minutos();
        if (minutos < 5 || minutos > 600) {
            throw new IllegalArgumentException("Registre entre 5 e 600 minutos por sessão.");
        }
        String tipo = textoOuNulo(entrada.tipo());
        int total = ProgressoDao.registrarSessao(
                minutos, tipo == null ? "estudo" : tipo, textoOuNulo(entrada.descricao()));
        ctx.json(new Payloads.Sessao(LocalDate.now().toString(), minutos, total));
    }

    private static void topico(Context ctx) {
        Payloads.TopicoDetalhe detalhe = ConteudoDao.topicoDetalhe(ctx.pathParam("slug"));
        if (detalhe == null) {
            ctx.status(404).json(Map.of("erro", "Tópico não encontrado"));
            return;
        }
        ctx.json(detalhe);
    }

    private static void questoes(Context ctx) {
        int limite = inteiroOuPadrao(ctx.queryParam("limite"), 50, 1, 200);
        ctx.json(ConteudoDao.questoes(
                textoOuNulo(ctx.queryParam("materia")),
                textoOuNulo(ctx.queryParam("dificuldade")),
                textoOuNulo(ctx.queryParam("banca")),
                limite));
    }

    private static void atualizarConfiguracoes(Context ctx) {
        Payloads.ConfigInput entrada = ctx.bodyAsClass(Payloads.ConfigInput.class);
        if (entrada == null || entrada.dataAlvo() == null || entrada.dataAlvo().isBlank()) {
            throw new IllegalArgumentException("Informe a data alvo no formato AAAA-MM-DD.");
        }
        LocalDate data;
        try {
            data = LocalDate.parse(entrada.dataAlvo());
        } catch (Exception e) {
            throw new IllegalArgumentException("Data inválida. Use o formato AAAA-MM-DD.");
        }
        if (data.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("A data alvo precisa estar no futuro.");
        }
        if (entrada.metaDiariaMinutos() != null
                && (entrada.metaDiariaMinutos() < 15 || entrada.metaDiariaMinutos() > 600)) {
            throw new IllegalArgumentException("A meta diária deve ficar entre 15 e 600 minutos.");
        }
        if (entrada.metaQuestoesDia() != null
                && (entrada.metaQuestoesDia() < 5 || entrada.metaQuestoesDia() > 200)) {
            throw new IllegalArgumentException("A meta de questões deve ficar entre 5 e 200.");
        }
        ConfiguracaoDao.salvar(data.toString(), entrada.metaDiariaMinutos(), entrada.metaQuestoesDia());
        ctx.json(ConfiguracaoDao.ler());
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static int inteiroOuPadrao(String valor, int padrao, int minimo, int maximo) {
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        try {
            int numero = Integer.parseInt(valor.trim());
            return Math.max(minimo, Math.min(maximo, numero));
        } catch (NumberFormatException e) {
            return padrao;
        }
    }
}

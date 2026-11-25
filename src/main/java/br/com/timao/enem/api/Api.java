package br.com.timao.enem.api;

import br.com.timao.enem.dao.ConfiguracaoDao;
import br.com.timao.enem.dao.ConteudoDao;
import br.com.timao.enem.dao.ProgressoDao;
import br.com.timao.enem.db.Db;
import br.com.timao.enem.model.Payloads;
import br.com.timao.enem.service.Calendario;
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
import java.util.LinkedHashMap;
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
                ConteudoDao.materias(),
                ConteudoDao.proximos(3)));
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

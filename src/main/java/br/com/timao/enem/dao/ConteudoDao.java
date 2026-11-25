package br.com.timao.enem.dao;

import br.com.timao.enem.db.Db;
import br.com.timao.enem.model.Payloads;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Consultas de leitura do conteúdo pedagógico. */
public final class ConteudoDao {

    private static final String SELECT_TOPICO = """
            SELECT t.id, t.slug, t.titulo, t.resumo, t.dificuldade, t.fase, t.ordem,
                   m.slug AS materia_slug, m.nome AS materia_nome, m.cor AS materia_cor,
                   (SELECT COUNT(*) FROM aula a WHERE a.topico_id = t.id) AS aulas,
                   (SELECT COUNT(*) FROM questao q WHERE q.topico_id = t.id) AS questoes,
                   (SELECT COUNT(*) FROM flashcard f WHERE f.topico_id = t.id) AS flashcards,
                   (SELECT COUNT(*) FROM aula_concluida ac
                      JOIN aula a2 ON ac.aula_id = a2.id
                     WHERE a2.topico_id = t.id AND ac.usuario_id = 1) AS aulas_concluidas
              FROM topico t
              JOIN materia m ON t.materia_id = m.id
            """;

    private ConteudoDao() {
    }

    public static List<Payloads.Materia> materias() {
        String sql = """
                SELECT m.id, m.slug, m.nome, m.area, m.cor, m.prioridade, m.descricao,
                       (SELECT COUNT(*) FROM topico t WHERE t.materia_id = m.id) AS topicos,
                       (SELECT COUNT(*) FROM aula a JOIN topico t ON a.topico_id = t.id
                         WHERE t.materia_id = m.id) AS aulas,
                       (SELECT COUNT(*) FROM aula_concluida ac
                          JOIN aula a ON ac.aula_id = a.id
                          JOIN topico t ON a.topico_id = t.id
                         WHERE t.materia_id = m.id AND ac.usuario_id = 1) AS concluidas
                  FROM materia m
                 ORDER BY m.ordem
                """;
        return Db.comConexao(c -> {
            List<Payloads.Materia> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int aulas = rs.getInt("aulas");
                    int concluidas = rs.getInt("concluidas");
                    int percentual = aulas == 0 ? 0 : (int) Math.round(100.0 * concluidas / aulas);
                    lista.add(new Payloads.Materia(
                            rs.getInt("id"), rs.getString("slug"), rs.getString("nome"),
                            rs.getString("area"), rs.getString("cor"), rs.getInt("prioridade"),
                            rs.getString("descricao"), rs.getInt("topicos"), aulas, percentual));
                }
            }
            return lista;
        });
    }

    public static List<Payloads.Topico> topicos(String materiaSlug) {
        boolean filtrar = materiaSlug != null && !materiaSlug.isBlank();
        String sql = SELECT_TOPICO + (filtrar ? " WHERE m.slug = ?" : "") + " ORDER BY m.ordem, t.ordem";
        return Db.comConexao(c -> {
            List<Payloads.Topico> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (filtrar) {
                    ps.setString(1, materiaSlug);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(topico(rs));
                    }
                }
            }
            return lista;
        });
    }

    public static List<Payloads.Topico> proximos(int limite) {
        String sql = SELECT_TOPICO + " WHERE m.prioridade = 1 ORDER BY m.ordem, t.ordem LIMIT ?";
        return Db.comConexao(c -> {
            List<Payloads.Topico> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, limite);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(topico(rs));
                    }
                }
            }
            return lista;
        });
    }

    public static Payloads.TopicoDetalhe topicoDetalhe(String slug) {
        return Db.comConexao(c -> {
            Payloads.Topico t;
            try (PreparedStatement ps = c.prepareStatement(SELECT_TOPICO + " WHERE t.slug = ?")) {
                ps.setString(1, slug);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    t = topico(rs);
                }
            }
            return new Payloads.TopicoDetalhe(t, aulas(c, t.id()), flashcardsDoTopico(c, t.id()));
        });
    }

    private static List<Payloads.Aula> aulas(Connection c, int topicoId) throws SQLException {
        String sql = """
                SELECT a.id, a.titulo, a.tipo, a.conteudo, a.video_url, a.video_arquivo,
                       a.duracao_min, a.ordem,
                       CASE WHEN ac.aula_id IS NULL THEN FALSE ELSE TRUE END AS concluida
                  FROM aula a
                  LEFT JOIN aula_concluida ac ON ac.aula_id = a.id AND ac.usuario_id = 1
                 WHERE a.topico_id = ?
                 ORDER BY a.ordem
                """;
        List<Payloads.Aula> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, topicoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int duracao = rs.getInt("duracao_min");
                    Integer duracaoOuNull = rs.wasNull() ? null : duracao;
                    lista.add(new Payloads.Aula(
                            rs.getInt("id"), rs.getString("titulo"), rs.getString("tipo"),
                            rs.getString("conteudo"), rs.getString("video_url"),
                            rs.getString("video_arquivo"), duracaoOuNull, rs.getInt("ordem"),
                            rs.getBoolean("concluida")));
                }
            }
        }
        return lista;
    }

    private static List<Payloads.Flashcard> flashcardsDoTopico(Connection c, int topicoId) throws SQLException {
        String sql = """
                SELECT f.id, f.codigo, f.frente, f.verso, f.dica, m.nome AS materia, t.titulo AS topico
                  FROM flashcard f
                  JOIN topico t ON f.topico_id = t.id
                  JOIN materia m ON t.materia_id = m.id
                 WHERE f.topico_id = ?
                 ORDER BY f.id
                """;
        List<Payloads.Flashcard> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, topicoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(flashcard(rs));
                }
            }
        }
        return lista;
    }

    public static List<Payloads.Flashcard> flashcards(String materiaSlug) {
        boolean filtrar = materiaSlug != null && !materiaSlug.isBlank();
        String sql = """
                SELECT f.id, f.codigo, f.frente, f.verso, f.dica, m.nome AS materia, t.titulo AS topico
                  FROM flashcard f
                  JOIN topico t ON f.topico_id = t.id
                  JOIN materia m ON t.materia_id = m.id
                """ + (filtrar ? " WHERE m.slug = ?" : "") + " ORDER BY m.ordem, t.ordem, f.id";
        return Db.comConexao(c -> {
            List<Payloads.Flashcard> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (filtrar) {
                    ps.setString(1, materiaSlug);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(flashcard(rs));
                    }
                }
            }
            return lista;
        });
    }

    public static List<Payloads.Questao> questoes(String materia, String dificuldade, String banca, int limite) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT q.id, q.codigo, q.banca, q.ano, q.fonte, q.autoral, q.dificuldade,
                       q.enunciado, q.gabarito, q.comentario,
                       m.nome AS materia, t.titulo AS topico
                  FROM questao q
                  JOIN topico t ON q.topico_id = t.id
                  JOIN materia m ON t.materia_id = m.id
                 WHERE 1 = 1
                """);
        if (materia != null && !materia.isBlank()) {
            sql.append(" AND m.slug = ?");
            params.add(materia);
        }
        if (dificuldade != null && !dificuldade.isBlank()) {
            sql.append(" AND q.dificuldade = ?");
            params.add(dificuldade);
        }
        if (banca != null && !banca.isBlank()) {
            sql.append(" AND q.banca = ?");
            params.add(banca);
        }
        sql.append(" ORDER BY m.ordem, t.ordem, q.id LIMIT ?");
        params.add(limite);

        return Db.comConexao(c -> {
            record Linha(int id, String codigo, String banca, Integer ano, String fonte, boolean autoral,
                         String dificuldade, String enunciado, String gabarito, String comentario,
                         String materia, String topico) {
            }
            List<Linha> linhas = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int ano = rs.getInt("ano");
                        Integer anoOuNull = rs.wasNull() ? null : ano;
                        linhas.add(new Linha(rs.getInt("id"), rs.getString("codigo"), rs.getString("banca"),
                                anoOuNull, rs.getString("fonte"), rs.getBoolean("autoral"),
                                rs.getString("dificuldade"), rs.getString("enunciado"),
                                rs.getString("gabarito"), rs.getString("comentario"),
                                rs.getString("materia"), rs.getString("topico")));
                    }
                }
            }
            List<Payloads.Questao> lista = new ArrayList<>();
            for (Linha l : linhas) {
                lista.add(new Payloads.Questao(l.id(), l.codigo(), l.banca(), l.ano(), l.fonte(), l.autoral(),
                        l.dificuldade(), l.enunciado(), l.gabarito(), l.comentario(),
                        alternativas(c, l.id()), l.materia(), l.topico()));
            }
            return lista;
        });
    }

    private static List<Payloads.Alternativa> alternativas(Connection c, int questaoId) throws SQLException {
        String sql = "SELECT letra, texto FROM alternativa WHERE questao_id = ? ORDER BY letra";
        List<Payloads.Alternativa> lista = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, questaoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Payloads.Alternativa(rs.getString("letra"), rs.getString("texto")));
                }
            }
        }
        return lista;
    }

    public static List<Payloads.Repertorio> repertorios() {
        String sql = "SELECT id, codigo, area, titulo, autor, citacao, aplicacao, tags FROM repertorio ORDER BY area, titulo";
        return Db.comConexao(c -> {
            List<Payloads.Repertorio> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Payloads.Repertorio(rs.getInt("id"), rs.getString("codigo"),
                            rs.getString("area"), rs.getString("titulo"), rs.getString("autor"),
                            rs.getString("citacao"), rs.getString("aplicacao"), rs.getString("tags")));
                }
            }
            return lista;
        });
    }

    public static List<Payloads.TemaRedacao> temasRedacao() {
        String sql = "SELECT id, codigo, titulo, ano, proposta, textos_motivadores FROM tema_redacao ORDER BY ano DESC, titulo";
        return Db.comConexao(c -> {
            List<Payloads.TemaRedacao> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int ano = rs.getInt("ano");
                    Integer anoOuNull = rs.wasNull() ? null : ano;
                    lista.add(new Payloads.TemaRedacao(rs.getInt("id"), rs.getString("codigo"),
                            rs.getString("titulo"), anoOuNull, rs.getString("proposta"),
                            rs.getString("textos_motivadores")));
                }
            }
            return lista;
        });
    }

    public static List<Payloads.Competencia> competencias() {
        String sql = "SELECT numero, titulo, descricao, criterios FROM competencia_redacao ORDER BY numero";
        return Db.comConexao(c -> {
            List<Payloads.Competencia> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Payloads.Competencia(rs.getInt("numero"), rs.getString("titulo"),
                            rs.getString("descricao"), rs.getString("criterios")));
                }
            }
            return lista;
        });
    }

    public static Payloads.Contadores contadores() {
        String sql = """
                SELECT (SELECT COUNT(*) FROM materia) AS materias,
                       (SELECT COUNT(*) FROM topico) AS topicos,
                       (SELECT COUNT(*) FROM aula) AS aulas,
                       (SELECT COUNT(*) FROM questao) AS questoes,
                       (SELECT COUNT(*) FROM flashcard) AS flashcards,
                       (SELECT COUNT(*) FROM repertorio) AS repertorios
                """;
        return Db.comConexao(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Payloads.Contadores(rs.getInt("materias"), rs.getInt("topicos"),
                        rs.getInt("aulas"), rs.getInt("questoes"), rs.getInt("flashcards"),
                        rs.getInt("repertorios"));
            }
        });
    }

    private static Payloads.Topico topico(ResultSet rs) throws SQLException {
        return new Payloads.Topico(
                rs.getInt("id"), rs.getString("slug"), rs.getString("titulo"), rs.getString("resumo"),
                rs.getString("dificuldade"), rs.getInt("fase"), rs.getInt("ordem"),
                rs.getString("materia_slug"), rs.getString("materia_nome"), rs.getString("materia_cor"),
                rs.getInt("aulas"), rs.getInt("questoes"), rs.getInt("flashcards"),
                rs.getInt("aulas_concluidas"));
    }

    /** Aulas com a matéria e o tópico, usadas pelo gerador de cronograma. */
    public static List<Payloads.AulaPlano> aulasParaPlano() {
        String sql = """
                SELECT a.id AS aula_id, a.titulo, a.duracao_min,
                       t.id AS topico_id, t.titulo AS topico, t.slug AS topico_slug,
                       t.fase AS fase_topico, t.dificuldade,
                       m.nome AS materia, m.slug AS materia_slug, m.prioridade,
                       CASE WHEN ac.aula_id IS NULL THEN FALSE ELSE TRUE END AS concluida
                  FROM aula a
                  JOIN topico t ON a.topico_id = t.id
                  JOIN materia m ON t.materia_id = m.id
                  LEFT JOIN aula_concluida ac ON ac.aula_id = a.id AND ac.usuario_id = 1
                 ORDER BY m.prioridade, m.ordem, t.fase, t.ordem, a.ordem
                """;
        return Db.comConexao(c -> {
            List<Payloads.AulaPlano> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int duracao = rs.getInt("duracao_min");
                    Integer duracaoOuNull = rs.wasNull() ? null : duracao;
                    lista.add(new Payloads.AulaPlano(
                            rs.getInt("aula_id"), rs.getInt("topico_id"), rs.getString("topico"),
                            rs.getString("topico_slug"), rs.getString("materia"),
                            rs.getString("materia_slug"), rs.getInt("prioridade"),
                            rs.getInt("fase_topico"), rs.getString("dificuldade"),
                            rs.getString("titulo"), duracaoOuNull, rs.getBoolean("concluida")));
                }
            }
            return lista;
        });
    }

    public static List<Payloads.Marco> marcosDoMes(java.time.LocalDate inicio, java.time.LocalDate fim) {
        String sql = """
                SELECT codigo, data, titulo, descricao, tipo
                  FROM marco
                 WHERE data BETWEEN ? AND ?
                 ORDER BY data, id
                """;
        return Db.comConexao(c -> {
            List<Payloads.Marco> lista = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setDate(1, java.sql.Date.valueOf(inicio));
                ps.setDate(2, java.sql.Date.valueOf(fim));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(new Payloads.Marco(rs.getString("codigo"),
                                rs.getDate("data").toLocalDate().toString(), rs.getString("titulo"),
                                rs.getString("descricao"), rs.getString("tipo")));
                    }
                }
            }
            return lista;
        });
    }

    private static Payloads.Flashcard flashcard(ResultSet rs) throws SQLException {
        return new Payloads.Flashcard(rs.getInt("id"), rs.getString("codigo"), rs.getString("frente"),
                rs.getString("verso"), rs.getString("dica"), rs.getString("materia"), rs.getString("topico"));
    }
}

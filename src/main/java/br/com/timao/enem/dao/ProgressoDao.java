package br.com.timao.enem.dao;

import br.com.timao.enem.db.Db;
import br.com.timao.enem.model.Payloads;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ProgressoDao {

    private ProgressoDao() {
    }

    public static int minutosHoje() {
        return Db.comConexao(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(minutos), 0) FROM sessao_estudo WHERE usuario_id = 1 AND data = CURRENT_DATE");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }

    public static int questoesHoje() {
        return Db.comConexao(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM tentativa_questao WHERE usuario_id = 1 AND CAST(criado_em AS DATE) = CURRENT_DATE");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }

    public static Payloads.ResumoAulas resumoAulas() {
        return Db.comConexao(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT (SELECT COUNT(*) FROM aula) AS total,
                           (SELECT COUNT(*) FROM aula_concluida WHERE usuario_id = 1) AS concluidas
                    """);
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new Payloads.ResumoAulas(rs.getInt("concluidas"), rs.getInt("total"));
            }
        });
    }

    /** Marca ou desmarca uma aula e atualiza o progresso do tópico. */
    public static Payloads.Conclusao concluirAula(int aulaId, boolean concluida) {
        return Db.comConexao(c -> {
            Integer topicoId = topicoDaAula(c, aulaId);
            if (topicoId == null) {
                return null;
            }

            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                if (concluida) {
                    try (PreparedStatement ps = c.prepareStatement("""
                            MERGE INTO aula_concluida (usuario_id, aula_id, concluido_em)
                            KEY (usuario_id, aula_id)
                            VALUES (1, ?, CURRENT_TIMESTAMP)
                            """)) {
                        ps.setInt(1, aulaId);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = c.prepareStatement(
                            "DELETE FROM aula_concluida WHERE usuario_id = 1 AND aula_id = ?")) {
                        ps.setInt(1, aulaId);
                        ps.executeUpdate();
                    }
                }

                int total = contarAulasConcluidas(c, topicoId);
                if (existeProgresso(c, topicoId)) {
                    try (PreparedStatement ps = c.prepareStatement("""
                            UPDATE topico_progresso
                               SET aulas_concluidas = ?,
                                   ultima_estudo = CASE WHEN ? > 0 THEN CURRENT_DATE ELSE ultima_estudo END
                             WHERE usuario_id = 1 AND topico_id = ?
                            """)) {
                        ps.setInt(1, total);
                        ps.setInt(2, total);
                        ps.setInt(3, topicoId);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO topico_progresso (usuario_id, topico_id, ultima_estudo, aulas_concluidas)
                            VALUES (1, ?, CURRENT_DATE, ?)
                            """)) {
                        ps.setInt(1, topicoId);
                        ps.setInt(2, total);
                        ps.executeUpdate();
                    }
                }

                c.commit();
                return new Payloads.Conclusao(aulaId, concluida, total);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        });
    }

    public static int registrarSessao(int minutos, String tipo, String descricao) {
        return Db.comConexao(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO sessao_estudo (usuario_id, data, minutos, tipo, descricao)
                    VALUES (1, CURRENT_DATE, ?, ?, ?)
                    """)) {
                ps.setInt(1, minutos);
                ps.setString(2, tipo);
                ps.setString(3, descricao);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(minutos), 0) FROM sessao_estudo WHERE usuario_id = 1 AND data = CURRENT_DATE");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }

    private static Integer topicoDaAula(Connection c, int aulaId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT topico_id FROM aula WHERE id = ?")) {
            ps.setInt(1, aulaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private static int contarAulasConcluidas(Connection c, int topicoId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COUNT(*)
                  FROM aula_concluida ac
                  JOIN aula a ON ac.aula_id = a.id
                 WHERE ac.usuario_id = 1 AND a.topico_id = ?
                """)) {
            ps.setInt(1, topicoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static boolean existeProgresso(Connection c, int topicoId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM topico_progresso WHERE usuario_id = 1 AND topico_id = ?")) {
            ps.setInt(1, topicoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}

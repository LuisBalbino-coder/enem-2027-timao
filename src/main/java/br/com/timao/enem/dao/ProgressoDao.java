package br.com.timao.enem.dao;

import br.com.timao.enem.db.Db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

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
}
